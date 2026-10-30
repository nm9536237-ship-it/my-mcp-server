package dev.local.mcpcontroller

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class PhoneAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun screenTree(): List<Map<String, Any?>> {
        val root = rootInActiveWindow ?: return emptyList()
        val result = mutableListOf<Map<String, Any?>>()
        try {
            collectNodes(root, result, 0)
        } finally {
            root.recycle()
        }
        return result
    }

    private fun collectNodes(node: AccessibilityNodeInfo, output: MutableList<Map<String, Any?>>, depth: Int) {
        if (output.size >= 300 || depth > 30) return
        val bounds = android.graphics.Rect().also(node::getBoundsInScreen)
        val text = node.text?.toString().orEmpty()
        val description = node.contentDescription?.toString().orEmpty()
        if (text.isNotBlank() || description.isNotBlank() || node.isClickable || node.isEditable) {
            output += mapOf(
                "text" to text.take(500),
                "contentDescription" to description.take(500),
                "viewId" to node.viewIdResourceName,
                "bounds" to listOf(bounds.left, bounds.top, bounds.right, bounds.bottom),
                "clickable" to node.isClickable,
                "editable" to node.isEditable
            )
        }
        for (index in 0 until node.childCount) {
            node.getChild(index)?.let { child ->
                collectNodes(child, output, depth + 1)
                child.recycle()
            }
        }
    }

    suspend fun tap(x: Int, y: Int): Boolean = dispatchPath(x, y, x, y, 80)

    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, duration: Long): Boolean =
        dispatchPath(x1, y1, x2, y2, duration.coerceIn(100, 5_000))

    private suspend fun dispatchPath(x1: Int, y1: Int, x2: Int, y2: Int, duration: Long): Boolean =
        suspendCancellableCoroutine { continuation ->
            val path = Path().apply {
                moveTo(x1.toFloat(), y1.toFloat())
                lineTo(x2.toFloat(), y2.toFloat())
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
                .build()
            val accepted = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) continuation.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) continuation.resume(false)
                }
            }, null)
            if (!accepted && continuation.isActive) continuation.resume(false)
        }

    fun typeText(text: String): Boolean {
        val focused = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        return try {
            focused.performAction(
                AccessibilityNodeInfo.ACTION_SET_TEXT,
                Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
            )
        } finally {
            focused.recycle()
        }
    }

    companion object {
        @Volatile
        var instance: PhoneAccessibilityService? = null
            private set
    }
}