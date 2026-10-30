package dev.local.mcpcontroller

import android.content.Context
import io.ktor.http.ContentType
import io.ktor.server.application.call
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.cio.CIO
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import android.util.Base64

object LocalMcpServer {
    private var engine: ApplicationEngine? = null
    private var token: String? = null

    @Synchronized
    fun start(context: Context) {
        if (engine != null) return
        token = accessToken(context)
        engine = embeddedServer(CIO, host = "127.0.0.1", port = 8765) {
            routing {
                post("/mcp") {
                    if (call.request.headers["Authorization"] != "Bearer $token") {
                        call.respondText("Unauthorized", status = io.ktor.http.HttpStatusCode.Unauthorized)
                        return@post
                    }
                    val request = runCatching { JSONObject(call.receiveText()) }.getOrNull()
                    if (request == null) {
                        call.respondText(jsonRpcError(JSONObject.NULL, -32700, "Parse error"), ContentType.Application.Json)
                    } else {
                        val response = handleRequest(request)
                        if (response == null) {
                            call.respondText("", status = io.ktor.http.HttpStatusCode.Accepted)
                        } else {
                            call.respondText(response, ContentType.Application.Json)
                        }
                    }
                }
            }
        }.start(wait = false)
    }

    @Synchronized
    fun stop() {
        engine?.stop(100, 500)
        engine = null
    }

    @Synchronized
    fun accessToken(context: Context): String {
        token?.let { return it }
        val preferences = context.applicationContext.getSharedPreferences("mcp_host", Context.MODE_PRIVATE)
        val stored = preferences.getString("access_token", null)
        if (stored != null) {
            token = stored
            return stored
        }
        val random = ByteArray(32).also(SecureRandom()::nextBytes)
        val generated = Base64.encodeToString(random, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        preferences.edit().putString("access_token", generated).apply()
        token = generated
        return generated
    }

    private suspend fun handleRequest(request: JSONObject): String? {
        val id = request.opt("id") ?: return null
        return when (request.optString("method")) {
            "initialize" -> jsonRpcResult(id, JSONObject()
                .put("protocolVersion", "2025-03-26")
                .put("capabilities", JSONObject().put("tools", JSONObject()))
                .put("serverInfo", JSONObject().put("name", "android-local-controller").put("version", "1.0.0")))
            "ping" -> jsonRpcResult(id, JSONObject())
            "tools/list" -> jsonRpcResult(id, JSONObject().put("tools", toolDefinitions()))
            "tools/call" -> callTool(id, request.optJSONObject("params") ?: JSONObject())
            else -> jsonRpcError(id, -32601, "Method not found")
        }
    }

    private suspend fun callTool(id: Any, params: JSONObject): String {
        val name = params.optString("name")
        val args = params.optJSONObject("arguments") ?: JSONObject()
        val accessibility = PhoneAccessibilityService.instance
        return try {
            when (name) {
                "get_screen_state" -> {
                    val tree = accessibility?.screenTree() ?: throw IllegalStateException("Enable the app's accessibility service first")
                    val image = ScreenCaptureService.instance?.screenshotBase64()
                        ?: throw IllegalStateException("Grant screen capture and wait for the first frame")
                    jsonRpcResult(id, JSONObject().put("content", JSONArray()
                        .put(JSONObject().put("type", "text").put("text", JSONArray(tree).toString()))
                        .put(JSONObject().put("type", "image").put("data", image).put("mimeType", "image/jpeg"))))
                }
                "tap_screen" -> {
                    val service = accessibility ?: throw IllegalStateException("Enable the app's accessibility service first")
                    val accepted = service.tap(args.getInt("x"), args.getInt("y"))
                    toolResult(id, if (accepted) "Tap completed" else "Tap was cancelled", !accepted)
                }
                "type_text" -> {
                    val service = accessibility ?: throw IllegalStateException("Enable the app's accessibility service first")
                    val text = args.getString("text")
                    require(text.length <= 4_000) { "Text is limited to 4000 characters" }
                    val accepted = service.typeText(text)
                    toolResult(id, if (accepted) "Text entered" else "No editable focused field accepted the text", !accepted)
                }
                "swipe_screen" -> {
                    val service = accessibility ?: throw IllegalStateException("Enable the app's accessibility service first")
                    val accepted = service.swipe(
                        args.getInt("x1"), args.getInt("y1"), args.getInt("x2"), args.getInt("y2"), args.optLong("duration_ms", 400)
                    )
                    toolResult(id, if (accepted) "Swipe completed" else "Swipe was cancelled", !accepted)
                }
                else -> jsonRpcError(id, -32602, "Unknown tool: $name")
            }
        } catch (error: Exception) {
            toolResult(id, error.message ?: "Tool failed", true)
        }
    }

    private fun toolDefinitions() = JSONArray()
        .put(tool("get_screen_state", "Return the current screenshot and visible UI nodes with screen bounds.", JSONObject().put("type", "object").put("properties", JSONObject()).put("required", JSONArray())))
        .put(tool("tap_screen", "Tap a screen coordinate.", coordinateSchema("x", "y")))
        .put(tool("type_text", "Enter text into the currently focused editable field.", JSONObject()
            .put("type", "object").put("properties", JSONObject().put("text", stringProperty("Text to enter")))
            .put("required", JSONArray().put("text"))))
        .put(tool("swipe_screen", "Perform a swipe between two screen coordinates.", JSONObject()
            .put("type", "object")
            .put("properties", JSONObject()
                .put("x1", integerProperty("Starting x coordinate"))
                .put("y1", integerProperty("Starting y coordinate"))
                .put("x2", integerProperty("Ending x coordinate"))
                .put("y2", integerProperty("Ending y coordinate"))
                .put("duration_ms", integerProperty("Gesture duration in milliseconds", 400)))
            .put("required", JSONArray().put("x1").put("y1").put("x2").put("y2"))))

    private fun coordinateSchema(vararg names: String): JSONObject {
        val properties = JSONObject()
        names.forEach { properties.put(it, integerProperty("Screen $it coordinate")) }
        val required = JSONArray().apply { names.forEach { put(it) } }
        return JSONObject().put("type", "object").put("properties", properties).put("required", required)
    }

    private fun integerProperty(description: String, default: Int? = null) = JSONObject()
        .put("type", "integer").put("description", description).also { if (default != null) it.put("default", default) }

    private fun stringProperty(description: String) = JSONObject().put("type", "string").put("description", description)

    private fun tool(name: String, description: String, inputSchema: JSONObject) = JSONObject()
        .put("name", name).put("description", description).put("inputSchema", inputSchema)

    private fun toolResult(id: Any, text: String, isError: Boolean) = jsonRpcResult(id, JSONObject()
        .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", text)))
        .put("isError", isError))

    private fun jsonRpcResult(id: Any, result: JSONObject) = JSONObject()
        .put("jsonrpc", "2.0").put("id", id).put("result", result).toString()

    private fun jsonRpcError(id: Any, code: Int, message: String) = JSONObject()
        .put("jsonrpc", "2.0").put("id", id)
        .put("error", JSONObject().put("code", code).put("message", message)).toString()
}