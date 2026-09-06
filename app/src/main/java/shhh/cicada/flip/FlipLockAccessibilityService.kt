package shhh.cicada.flip

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * FlipLockAccessibilityService. A light accessibility service that locks the screen on flip-down.
 * Uses GLOBAL_ACTION_LOCK_SCREEN (Android 9+) which allows seamless Fingerprint/Face Unlock afterwards.
 */
class FlipLockAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "FlipLockAccessibilityService connected successfully.")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        Log.i(TAG, "FlipLockAccessibilityService unbound.")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        Log.i(TAG, "FlipLockAccessibilityService destroyed.")
        super.onDestroy()
    }

    companion object {
        private const val TAG = "FlipLockAccessibility"
        var instance: FlipLockAccessibilityService? = null

        fun performLock(): Boolean {
            val service = instance ?: return false
            val success = service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            Log.i(TAG, "performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) result: $success")
            return success
        }

        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            val expectedComponent = ComponentName(context, FlipLockAccessibilityService::class.java)
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServices)
            while (colonSplitter.hasNext()) {
                val componentStr = colonSplitter.next()
                val component = ComponentName.unflattenFromString(componentStr)
                if (component != null && component == expectedComponent) {
                    return true
                }
            }
            return false
        }
    }
}
