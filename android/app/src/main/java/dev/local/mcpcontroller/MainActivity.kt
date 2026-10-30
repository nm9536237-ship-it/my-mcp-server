package dev.local.mcpcontroller

import android.app.Activity
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }
        status = TextView(this).apply { textSize = 16f }
        layout.addView(status)
        layout.addView(button("Enable accessibility") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        layout.addView(button("Grant screen capture") {
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE)
        })
        layout.addView(button("Start local MCP server") { startMcpHost() })
        layout.addView(button("Stop local MCP server") {
            stopService(Intent(this, McpHostService::class.java))
            refreshStatus()
        })
        layout.addView(button("Copy MCP client config") {
            val config = """{"mcpServers":{"android-local-controller":{"url":"http://127.0.0.1:8765/mcp","headers":{"Authorization":"Bearer ${LocalMcpServer.accessToken(this)}"}}}}"""
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("MCP config", config))
        })
        setContentView(layout)
        refreshStatus()
    }

    @Deprecated("Activity result is used to keep compatibility with older Android versions")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CAPTURE && resultCode == RESULT_OK && data != null) {
            val intent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
            }
            startForegroundCompat(intent)
            refreshStatus()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun startMcpHost() {
        startForegroundCompat(Intent(this, McpHostService::class.java))
        refreshStatus()
    }

    private fun startForegroundCompat(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
    }

    private fun refreshStatus() {
        val server = if (McpHostService.running) "running" else "stopped"
        val accessibility = if (PhoneAccessibilityService.instance != null) "connected" else "not enabled"
        val capture = if (ScreenCaptureService.isReady) "ready" else "not granted"
        status.text = "MCP server: $server\nAccessibility: $accessibility\nScreen capture: $capture\n\nEndpoint: http://127.0.0.1:8765/mcp\nAccess token: ${LocalMcpServer.accessToken(this)}"
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener { action() }
    }

    companion object {
        private const val REQUEST_CAPTURE = 4102
    }
}