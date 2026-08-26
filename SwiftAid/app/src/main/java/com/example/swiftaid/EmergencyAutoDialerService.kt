package com.example.swiftaid

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class EmergencyAutoDialerService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (!isEmergencyTriggered || clickConsumed) return

        val foregroundPackage = event.packageName?.toString() ?: return
        if (!isDialerPackage(foregroundPackage)) return

        val rootNode = rootInActiveWindow ?: return
        val callNode = findCallActionNode(rootNode) ?: return

        val clicked = performClick(callNode)
        if (clicked) {
            clickConsumed = true
            emergencyTriggered = false
            Log.i(TAG, "Emergency dialer call button clicked in $foregroundPackage")
        }
    }

    override fun onInterrupt() = Unit

    private fun isDialerPackage(packageName: String): Boolean {
        return packageName in DIALER_PACKAGES
    }

    private fun findCallActionNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (isCallNode(node)) return node

        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            val result = findCallActionNode(child)
            if (result != null) return result
            child.recycle()
        }

        return null
    }

    private fun isCallNode(node: AccessibilityNodeInfo): Boolean {
        val text = node.text?.toString()
        val contentDescription = node.contentDescription?.toString()

        return text.equals(CALL_LABEL, ignoreCase = true) ||
            contentDescription.equals(CALL_LABEL, ignoreCase = true)
    }

    private fun performClick(node: AccessibilityNodeInfo): Boolean {
        var clickableNode: AccessibilityNodeInfo? = node
        while (clickableNode != null) {
            if (clickableNode.isClickable) {
                return clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            clickableNode = clickableNode.parent
        }

        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    companion object {
        private const val TAG = "EmergencyAutoDialer"
        private const val CALL_LABEL = "Call"

        private val DIALER_PACKAGES = setOf(
            "com.google.android.dialer",
            "com.android.dialer",
            "com.samsung.android.dialer",
            "com.android.server.telecom"
        )

        @Volatile
        private var emergencyTriggered = false

        @Volatile
        private var clickConsumed = false

        @get:JvmStatic
        @set:JvmStatic
        var isEmergencyTriggered: Boolean
            get() = emergencyTriggered
            set(value) {
                emergencyTriggered = value
                if (value) {
                    clickConsumed = false
                }
            }

        @JvmStatic
        fun armEmergencyDialer() {
            isEmergencyTriggered = true
        }

        @JvmStatic
        fun resetEmergencyDialer() {
            emergencyTriggered = false
            clickConsumed = false
        }
    }
}
