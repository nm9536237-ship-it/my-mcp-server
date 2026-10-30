package dev.local.mcpcontroller

import android.app.Activity
import android.app.AlertDialog
import android.app.KeyguardManager
import android.content.ClipDescription
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var clientName: EditText
    private lateinit var clientList: LinearLayout
    private var pendingAction: PendingAction? = null

    private sealed interface PendingAction {
        data class Authorize(val name: String) : PendingAction
        data class Revoke(val name: String) : PendingAction
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }
        status = TextView(this).apply { textSize = 16f }
        layout.addView(status)
        clientName = EditText(this).apply {
            hint = "AI client name, e.g. Gemini on-device"
            maxLines = 1
        }
        layout.addView(clientName)
        layout.addView(button("Enable accessibility") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        layout.addView(button("Grant screen capture") {
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE)
        })
        layout.addView(button("Stop screen capture") {
            stopService(Intent(this, ScreenCaptureService::class.java))
            refreshStatus()
        })
        layout.addView(button("Start local MCP server") { startMcpHost() })
        layout.addView(button("Stop local MCP server") {
            stopService(Intent(this, McpHostService::class.java))
            refreshStatus()
        })
        layout.addView(button("Authorize this AI client") {
            beginDeviceConfirmation(PendingAction.Authorize(clientName.text.toString()))
        })
        layout.addView(TextView(this).apply { text = "Authorized clients (their tokens can control this phone):" })
        clientList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        layout.addView(clientList)
        setContentView(layout)
        refreshStatus()
    }

    @Deprecated("Activity result is used to keep compatibility with older Android versions")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_DEVICE_CONFIRM) {
            val action = pendingAction
            pendingAction = null
            if (resultCode == RESULT_OK && action != null) completeDeviceConfirmedAction(action)
            else Toast.makeText(this, "Authorization change cancelled", Toast.LENGTH_SHORT).show()
            return
        }
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
        status.text = "MCP server: $server\nAccessibility: $accessibility\nScreen capture: $capture\nAuthorized clients: ${ClientAuthorizations.clients(this).size}\n\nEndpoint: http://127.0.0.1:8765/mcp"
        refreshClientList()
    }

    private fun refreshClientList() {
        clientList.removeAllViews()
        ClientAuthorizations.clients(this).forEach { name ->
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(TextView(this).apply { text = name; textSize = 16f }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(button("Revoke") {
                AlertDialog.Builder(this)
                    .setTitle("Revoke $name?")
                    .setMessage("This client will immediately lose access to screen data and phone controls.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Revoke") { _, _ -> beginDeviceConfirmation(PendingAction.Revoke(name)) }
                    .show()
            })
            clientList.addView(row)
        }
    }

    private fun beginDeviceConfirmation(action: PendingAction) {
        val keyguard = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        if (!keyguard.isDeviceSecure) {
            Toast.makeText(this, "Set a screen lock before authorizing AI clients", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
            return
        }
        val prompt = when (action) {
            is PendingAction.Authorize -> "Authorize ${action.name.trim()} to use phone controls"
            is PendingAction.Revoke -> "Revoke ${action.name} access"
        }
        val confirmation = keyguard.createConfirmDeviceCredentialIntent("Confirm device owner", prompt)
        if (confirmation == null) {
            Toast.makeText(this, "Could not start device authentication", Toast.LENGTH_LONG).show()
            return
        }
        pendingAction = action
        startActivityForResult(confirmation, REQUEST_DEVICE_CONFIRM)
    }

    private fun completeDeviceConfirmedAction(action: PendingAction) {
        when (action) {
            is PendingAction.Authorize -> runCatching {
                val credential = ClientAuthorizations.issue(this, action.name)
                val config = org.json.JSONObject()
                    .put("mcpServers", org.json.JSONObject().put("android-local-controller", org.json.JSONObject()
                        .put("url", "http://127.0.0.1:8765/mcp")
                        .put("headers", org.json.JSONObject().put("Authorization", "Bearer ${credential.token}"))))
                    .toString()
                val clip = ClipData.newPlainText("MCP config for ${credential.clientName}", config)
                if (Build.VERSION.SDK_INT >= 33) {
                    clip.description.extras = PersistableBundle().apply {
                        putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                    }
                }
                (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                clientName.text.clear()
                Toast.makeText(this, "Client authorized. Its private config was copied once; paste only into that client.", Toast.LENGTH_LONG).show()
                refreshStatus()
            }.onFailure { error ->
                Toast.makeText(this, error.message ?: "Could not authorize client", Toast.LENGTH_LONG).show()
            }
            is PendingAction.Revoke -> {
                if (ClientAuthorizations.revoke(this, action.name)) {
                    Toast.makeText(this, "${action.name} access revoked", Toast.LENGTH_SHORT).show()
                }
                refreshStatus()
            }
        }
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener { action() }
    }

    companion object {
        private const val REQUEST_CAPTURE = 4102
        private const val REQUEST_DEVICE_CONFIRM = 4103
    }
}