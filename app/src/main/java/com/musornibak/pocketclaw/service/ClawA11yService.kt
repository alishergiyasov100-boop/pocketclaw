package com.musornibak.pocketclaw.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay

class ClawA11yService : AccessibilityService() {

    private val snapshotBounds = LinkedHashMap<Int, Rect>()
    private val snapshotScrollables = mutableListOf<Int>()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't react to events; we drive actions from the agent.
    }

    override fun onInterrupt() = Unit

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun snapshotScreen(maxNodes: Int = 120): String {
        val win = rootInActiveWindow ?: return "(no active window)"
        val pkg = win.packageName?.toString() ?: "?"
        val dm = resources.displayMetrics
        snapshotBounds.clear()
        snapshotScrollables.clear()
        val sb = StringBuilder()
        sb.append("screen: ").append(dm.widthPixels).append('x').append(dm.heightPixels)
            .append("  app: ").append(pkg).append('\n')
        sb.append("--- interactive nodes (tap_node by #i) ---\n")
        var index = 0
        walk(win, 0) { node, _ ->
            if (index >= maxNodes) return@walk false
            if (!node.isVisibleToUser) return@walk true
            val text = node.text?.toString()?.trim().orEmpty()
            val desc = node.contentDescription?.toString()?.trim().orEmpty()
            val hint = runCatching { node.hintText?.toString()?.trim().orEmpty() }.getOrNull().orEmpty()
            val cls = node.className?.toString()?.substringAfterLast('.') ?: "?"
            val rid = node.viewIdResourceName?.substringAfterLast('/').orEmpty()
            val interactive = node.isClickable || node.isEditable || node.isLongClickable || node.isCheckable
            val labeled = text.isNotEmpty() || desc.isNotEmpty() || hint.isNotEmpty()
            if (!interactive && !labeled && !node.isScrollable) return@walk true

            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (rect.isEmpty) return@walk true

            val label = listOfNotNull(
                text.ifEmpty { null },
                desc.takeIf { it.isNotEmpty() && it != text },
                hint.takeIf { it.isNotEmpty() }?.let { "hint=$it" }
            ).joinToString(" | ")

            sb.append("[#").append(index).append("] ").append(cls)
            if (label.isNotEmpty()) sb.append(" \"").append(label.take(80)).append('"')
            sb.append(" @(").append(rect.centerX()).append(',').append(rect.centerY()).append(')')
            val traits = buildString {
                if (node.isClickable) append(" tap")
                if (node.isLongClickable) append(" long")
                if (node.isEditable) append(" edit")
                if (node.isCheckable) append(" chk=").append(node.isChecked)
                if (node.isScrollable) append(" scroll")
                if (!node.isEnabled) append(" disabled")
            }
            if (traits.isNotEmpty()) sb.append(traits)
            if (rid.isNotEmpty()) sb.append(" id=").append(rid)
            sb.append('\n')

            snapshotBounds[index] = Rect(rect)
            if (node.isScrollable) snapshotScrollables.add(index)
            index++
            true
        }
        if (snapshotScrollables.isNotEmpty()) {
            sb.append("scrollables: ").append(snapshotScrollables.joinToString(",") { "#$it" }).append('\n')
        }
        sb.append("hint: используй tap_node {\"i\":\"<номер>\"} для точного тапа по #i\n")
        return sb.toString()
    }

    suspend fun tapNode(index: Int): Boolean {
        val rect = snapshotBounds[index] ?: return false
        return tapXy(rect.centerX().toFloat(), rect.centerY().toFloat())
    }

    suspend fun longPressNode(index: Int): Boolean {
        val rect = snapshotBounds[index] ?: return false
        return longPressXy(rect.centerX().toFloat(), rect.centerY().toFloat())
    }

    fun findClickableByDesc(desc: String): AccessibilityNodeInfo? {
        val win = rootInActiveWindow ?: return null
        var found: AccessibilityNodeInfo? = null
        walk(win, 0) { node, _ ->
            val d = node.contentDescription?.toString()?.trim().orEmpty()
            if (d.equals(desc, ignoreCase = true) || d.contains(desc, ignoreCase = true)) {
                var cur: AccessibilityNodeInfo? = node
                var hop = 0
                while (cur != null && hop < 6) {
                    if (cur.isClickable) { found = cur; return@walk false }
                    cur = cur.parent
                    hop++
                }
                if (found == null) found = node
                return@walk false
            }
            true
        }
        return found
    }

    suspend fun tapDesc(desc: String): Boolean {
        val node = findClickableByDesc(desc) ?: return false
        if (node.isClickable) return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.isEmpty) return false
        return tapXy(rect.centerX().toFloat(), rect.centerY().toFloat())
    }

    private fun walk(node: AccessibilityNodeInfo?, depth: Int, body: (AccessibilityNodeInfo, Int) -> Boolean) {
        if (node == null) return
        if (!body(node, depth)) return
        for (i in 0 until node.childCount) {
            walk(node.getChild(i), depth + 1, body)
        }
    }

    fun findClickableByText(text: String): AccessibilityNodeInfo? {
        val win = rootInActiveWindow ?: return null
        val matches = win.findAccessibilityNodeInfosByText(text) ?: return null
        for (m in matches) {
            var cur: AccessibilityNodeInfo? = m
            var hop = 0
            while (cur != null && hop < 6) {
                if (cur.isClickable) return cur
                cur = cur.parent
                hop++
            }
        }
        return matches.firstOrNull()
    }

    fun findEditable(): AccessibilityNodeInfo? {
        val win = rootInActiveWindow ?: return null
        var found: AccessibilityNodeInfo? = null
        walk(win, 0) { node, _ ->
            if (node.isEditable && node.isVisibleToUser) {
                found = node
                false
            } else true
        }
        return found
    }

    suspend fun tapText(text: String): Boolean {
        val node = findClickableByText(text) ?: return false
        if (node.isClickable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.isEmpty) return false
        return tapXy(rect.centerX().toFloat(), rect.centerY().toFloat())
    }

    suspend fun tapXy(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 80)
        val g = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGestureSuspend(g)
    }

    suspend fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 300): Boolean {
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val g = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGestureSuspend(g)
    }

    private suspend fun dispatchGestureSuspend(g: GestureDescription): Boolean {
        val done = CompletableDeferred<Boolean>()
        val ok = dispatchGesture(g, object : GestureResultCallback() {
            override fun onCompleted(d: GestureDescription?) { done.complete(true) }
            override fun onCancelled(d: GestureDescription?) { done.complete(false) }
        }, null)
        if (!ok) return false
        return done.await()
    }

    fun typeInFocused(text: String): Boolean {
        val node = findEditable() ?: return false
        val args = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun pressBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun pressHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun pressRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun openNotifications(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    fun currentApp(): String = rootInActiveWindow?.packageName?.toString() ?: "(unknown)"

    suspend fun longPressXy(x: Float, y: Float, durationMs: Long = 800): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val g = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGestureSuspend(g)
    }

    suspend fun waitForText(text: String, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val win = rootInActiveWindow
            if (win != null) {
                val found = win.findAccessibilityNodeInfosByText(text)
                if (!found.isNullOrEmpty()) return true
            }
            delay(250)
        }
        return false
    }

    fun scrollAny(forward: Boolean): Boolean {
        val win = rootInActiveWindow ?: return false
        var ok = false
        walk(win, 0) { node, _ ->
            if (node.isScrollable) {
                val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                if (node.performAction(action)) {
                    ok = true
                    return@walk false
                }
            }
            true
        }
        return ok
    }

    companion object {
        @Volatile private var instance: ClawA11yService? = null
        fun get(): ClawA11yService? = instance
        fun isConnected(): Boolean = instance != null
    }
}
