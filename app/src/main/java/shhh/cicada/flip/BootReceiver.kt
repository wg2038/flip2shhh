package shhh.cicada.flip

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * BootReceiver. Starts FlipToShhhService automatically after device boot.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.i(TAG, "Received broadcast intent: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            val prefs = context.getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
            val autoStartEnabled = prefs.getBoolean(PrefsKeys.KEY_AUTO_START_BOOT, true)
            val isUserEnabled = prefs.getBoolean(PrefsKeys.KEY_SERVICE_USER_ENABLED, true)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val onboardingComplete = prefs.getBoolean(PrefsKeys.KEY_ONBOARDING_COMPLETE, false)
            if (!onboardingComplete) {
                Log.i(TAG, "Onboarding not complete; skipping auto-start on boot.")
                return
            }

            if (!autoStartEnabled) {
                Log.i(TAG, "Auto-start on boot is disabled by user setting.")
                return
            }

            if (!isUserEnabled) {
                Log.i(TAG, "Service was manually disabled by user; skipping auto-start on boot.")
                return
            }

            if (!nm.isNotificationPolicyAccessGranted) {
                Log.w(TAG, "Cannot auto-start service: Notification Policy Access (DND) not granted.")
                return
            }

            Log.i(TAG, "Starting FlipToShhhService automatically on boot...")
            try {
                val serviceIntent = Intent(context, FlipToShhhService::class.java)
                ContextCompat.startForegroundService(context, serviceIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to auto-start FlipToShhhService on boot", e)
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
