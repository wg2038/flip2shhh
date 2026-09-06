package shhh.cicada.flip

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

/**
 * Flip to Shhh Service. Flip the phone face down to mute, flip back to restore.
 *
 * Algorithm highlights:
 *  - Strict flatness & gravity vector analysis: ensures device is resting flat on a surface (tilt <= 15°).
 *  - Proximity sensor fusion: verifies screen is facing a physical surface (desk/table), preventing false triggers in mid-air or slanted mounts.
 *  - Continuous 2.0s stillness window: guarantees the phone remains stationary on the surface before triggering DND.
 *  - Dual-pulse haptic feedback on flip-down ("咚 - 咚"), subtle click on flip-up.
 *  - Smart DND ownership tracking: never overwrites or clears external system-scheduled DND rules.
 */
class FlipToShhhService : LifecycleService(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var notifManager: NotificationManager
    private var vibrator: Vibrator? = null
    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())

    private var gravitySensor: Sensor? = null
    private var accelerometerSensor: Sensor? = null
    private var gyroscopeSensor: Sensor? = null
    private var proximitySensor: Sensor? = null
    private var activeOrientationSensor: Sensor? = null
    private var hasOpticalProximity: Boolean = false

    private var currentXValue: Float = 0f
    private var currentYValue: Float = 0f
    private var currentZValue: Float = 0f

    private var prevXValue: Float = 0f
    private var prevYValue: Float = 0f
    private var prevZValue: Float = 0f
    private var currentDeltaG: Float = 0f
    private var currentGyroRotation: Float = 0f
    private var isProximityNear: Boolean = false

    private var faceDownStartTime: Long = 0L

    private var wasDndActivatedByService: Boolean = false

    // Gravity/gyroscope run at the low-power UI rate except while a flip-down countdown is
    // pending, when they are oversampled so hand tremor is not aliased (see registerSensors).
    private var usingFastSensorRate: Boolean = false

    // Notification channels are per-locale (see createNotificationChannel); this is the one
    // the foreground notification currently lives in.
    private var currentChannelId: String = CHANNEL_ID

    private enum class TargetFlipState { NONE, DOWN, UP }
    private var pendingTargetState: TargetFlipState = TargetFlipState.NONE

    private val debounceRunnable: Runnable = Runnable {
        val currentlyFlipped = _isFlippedDown.value
        val hMag = sqrt(currentXValue * currentXValue + currentYValue * currentYValue)
        val isPhysicallyStationary = (currentDeltaG <= MAX_DELTA_G_FINAL_CHECK) && (currentGyroRotation <= MAX_GYRO_FINAL_CHECK)
        val isStrictlyFlatFaceDown = (currentZValue <= FACE_DOWN_ENTER_Z_THRESHOLD) &&
                (hMag <= MAX_HORIZONTAL_GRAVITY) &&
                (!hasOpticalProximity || isProximityNear)

        if (pendingTargetState == TargetFlipState.DOWN && !currentlyFlipped) {
            val now = SystemClock.elapsedRealtime()
            val elapsedTime = now - faceDownStartTime
            val remainingMs = FIXED_DEBOUNCE_DOWN_MS - elapsedTime

            if (remainingMs > 30L) {
                if (isStrictlyFlatFaceDown) {
                    handler.postDelayed(debounceRunnable, remainingMs)
                    return@Runnable
                } else {
                    Log.i(TAG, "Cancelling debounce countdown: phone tilted or moved (Z=$currentZValue, H=$hMag)")
                    pendingTargetState = TargetFlipState.NONE
                    setFastSensorRate(false)
                    return@Runnable
                }
            }

            if (isStrictlyFlatFaceDown && isPhysicallyStationary) {
                Log.i(TAG, "Debounce confirmed (2000ms elapsed): entering Face Down mode (DND ON), Z=$currentZValue, H=$hMag, optProx=$hasOpticalProximity")
                _isFlippedDown.value = true
                enableDoNotDisturb()
            } else {
                Log.i(TAG, "Debounce finished but phone not stationary/flat (Z=$currentZValue, H=$hMag, dG=$currentDeltaG, gyro=$currentGyroRotation) -> skip DND")
            }
            // The countdown ended either way; return to the low-power UI sampling rate.
            setFastSensorRate(false)
        } else if (pendingTargetState == TargetFlipState.UP && currentlyFlipped) {
            val isExitCondition = (currentZValue > FACE_DOWN_EXIT_Z_THRESHOLD) ||
                    (hMag > EXIT_HORIZONTAL_GRAVITY) ||
                    (hasOpticalProximity && !isProximityNear)
            if (isExitCondition) {
                Log.i(TAG, "Debounce confirmed -> exiting Face Down mode (DND OFF), Z=$currentZValue, H=$hMag")
                _isFlippedDown.value = false
                disableDoNotDisturb()
            }
        }
        pendingTargetState = TargetFlipState.NONE
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    // Language switched in the app while the service is up: build the new locale's channel,
    // move the foreground notification over, then retire the old channel. Deleting
    // the channel that currently hosts the notification would cancel it outright.
    private val languagePrefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == PrefsKeys.KEY_LANGUAGE_MODE) {
            val oldChannelId = currentChannelId
            createNotificationChannel()
            updateNotification(active = _isDndActive.value)
            if (oldChannelId != currentChannelId &&
                notifManager.getNotificationChannel(oldChannelId) != null
            ) {
                notifManager.deleteNotificationChannel(oldChannelId)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate")
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        vibrator = (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        prefs = getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(languagePrefListener)

        gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        activeOrientationSensor = gravitySensor ?: accelerometerSensor

        hasOpticalProximity = isHardwareOpticalProximity(proximitySensor)

        Log.i(TAG, "Active orientation: ${activeOrientationSensor?.name}, Gyro: ${gyroscopeSensor?.name}, Proximity: ${proximitySensor?.name} (Optical: $hasOpticalProximity)")

        // Restore persisted state if service was killed and restarted.
        if (prefs.getBoolean(PrefsKeys.KEY_DND_ACTIVE, false)) {
            val systemDndStillOn = notifManager.isNotificationPolicyAccessGranted &&
                    notifManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
            if (systemDndStillOn) {
                wasDndActivatedByService = prefs.getBoolean(PrefsKeys.KEY_WAS_DND_ACTIVATED_BY_SERVICE, false)
                _isDndActive.value = true
                _isFlippedDown.value = true
            } else {
                // DND was turned off externally while the service was dead; drop stale state.
                persistState(active = false)
            }
        }

        createNotificationChannel()
    }

    private fun isHardwareOpticalProximity(sensor: Sensor?): Boolean {
        sensor ?: return false
        val name = sensor.name.lowercase()
        val vendor = sensor.vendor.lowercase()
        val isVirtual = name.contains("palm") || name.contains("touch") || name.contains("virtual") ||
                name.contains("ultrasound") || name.contains("elliptic") || name.contains("ear") ||
                name.contains("gesture") || vendor.contains("elliptic") || vendor.contains("samsung")
        return !isVirtual
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.i(TAG, "onStartCommand")
        startForeground(NOTIFICATION_ID, buildNotification(active = _isDndActive.value))

        // A debounce runnable stranded by a previous session would reference a stale
        // pendingTargetState and permanently block future flip detection.
        pendingTargetState = TargetFlipState.NONE
        handler.removeCallbacks(debounceRunnable)
        usingFastSensorRate = false
        registerSensors()
        _isRunning.value = true
        return START_STICKY
    }

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(languagePrefListener)
        unregisterAllSensors()
        restoreDndIfNeeded()
        pendingTargetState = TargetFlipState.NONE
        _isRunning.value = false
        _isFlippedDown.value = false
        _isDndActive.value = false
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // ── Sensor Listeners ───────────────────────────────────────────────────

    private fun registerSensors() {
        unregisterAllSensors()

        // SENSOR_DELAY_UI (~15 Hz) aliases 8-12 Hz hand tremor below the stillness thresholds
        // (Nyquist ~7.5 Hz), which defeated the mid-air false-trigger filter. While a flip-down
        // countdown is running, oversample the motion sensors at ~50 Hz instead; the low-power
        // UI rate is restored as soon as the countdown ends or is cancelled.
        val motionDelay = if (usingFastSensorRate) SensorManager.SENSOR_DELAY_GAME else SensorManager.SENSOR_DELAY_UI

        activeOrientationSensor?.let { sensor ->
            val registered = sensorManager.registerListener(
                this,
                sensor,
                motionDelay
            )
            Log.i(TAG, "Registered orientation sensor ${sensor.name}, delay=$motionDelay, success=$registered")
        }

        gyroscopeSensor?.let { gyro ->
            val registered = sensorManager.registerListener(
                this,
                gyro,
                motionDelay
            )
            Log.i(TAG, "Registered gyroscope sensor ${gyro.name}, delay=$motionDelay, success=$registered")
        }

        proximitySensor?.let { prox ->
            val registered = sensorManager.registerListener(
                this,
                prox,
                SensorManager.SENSOR_DELAY_NORMAL
            )
            Log.i(TAG, "Registered proximity sensor ${prox.name}, success=$registered")
        }
    }

    private fun unregisterAllSensors() {
        // Deliberately does not touch handler callbacks: setFastSensorRate() re-registers the
        // listeners while a countdown may be pending and must not cancel it. Lifecycle call
        // sites that really need the runnable gone remove it explicitly.
        sensorManager.unregisterListener(this)
    }

    private fun setFastSensorRate(fast: Boolean) {
        if (usingFastSensorRate == fast) return
        usingFastSensorRate = fast
        registerSensors()
    }

    // ── Sensor Events ──────────────────────────────────────────────────────

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        when (event.sensor.type) {
            Sensor.TYPE_PROXIMITY -> {
                val distance = event.values[0]
                val maxRange = proximitySensor?.maximumRange?.takeIf { it > 0f } ?: 5.0f
                isProximityNear = (distance == 0f || (distance < maxRange && distance <= 4.0f))
                checkFlipState()
                return
            }
            Sensor.TYPE_GYROSCOPE -> {
                val gx = event.values[0]
                val gy = event.values[1]
                val gz = event.values[2]
                currentGyroRotation = sqrt(gx * gx + gy * gy + gz * gz)

                // High-precision hand tremor filter for devices without optical proximity (e.g. Samsung):
                // If counting down in mid-air, physiological hand tremor (> 0.05 rad/s) instantly resets timer!
                if (pendingTargetState == TargetFlipState.DOWN && (!hasOpticalProximity || !isProximityNear)) {
                    if (currentGyroRotation > MAX_GYRO_TABLE_STILLNESS) {
                        faceDownStartTime = SystemClock.elapsedRealtime()
                    }
                }
                return
            }
        }

        val targetType = activeOrientationSensor?.type ?: return
        if (event.sensor.type != targetType) return

        val newX = event.values[0]
        val newY = event.values[1]
        val newZ = event.values[2]

        val dx = newX - prevXValue
        val dy = newY - prevYValue
        val dz = newZ - prevZValue
        currentDeltaG = sqrt(dx * dx + dy * dy + dz * dz)

        prevXValue = newX
        prevYValue = newY
        prevZValue = newZ

        currentXValue = newX
        currentYValue = newY
        currentZValue = newZ

        // Hand tremor micro-acceleration filter:
        if (pendingTargetState == TargetFlipState.DOWN && (!hasOpticalProximity || !isProximityNear)) {
            if (currentDeltaG > MAX_DELTA_G_TABLE_STILLNESS) {
                faceDownStartTime = SystemClock.elapsedRealtime()
            }
        }

        checkFlipState()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // ── Pixel-Style Flip Detection Logic ───────────────────────────────────

    private fun checkFlipState() {
        val hMag = sqrt(currentXValue * currentXValue + currentYValue * currentYValue)

        // Strict Pixel-grade face-down flatness check:
        // 1. Z <= -9.0 m/s^2 (screen pointing down, normal gravity is ~9.8 m/s^2)
        // 2. Horizontal component sqrt(X^2+Y^2) <= 2.5 m/s^2 (tilt angle <= 15° - 20°)
        // 3. If optical proximity sensor exists, require it to be NEAR
        val isStrictlyFlatFaceDown = (currentZValue <= FACE_DOWN_ENTER_Z_THRESHOLD) &&
                (hMag <= MAX_HORIZONTAL_GRAVITY) &&
                (!hasOpticalProximity || isProximityNear)

        // Exit condition: picked up, tilted beyond threshold, or screen face up
        val isExitCondition = (currentZValue > FACE_DOWN_EXIT_Z_THRESHOLD) ||
                (hMag > EXIT_HORIZONTAL_GRAVITY) ||
                (hasOpticalProximity && !isProximityNear)

        val currentlyFlipped = _isFlippedDown.value

        if (!currentlyFlipped) {
            if (isStrictlyFlatFaceDown) {
                if (pendingTargetState != TargetFlipState.DOWN) {
                    handler.removeCallbacks(debounceRunnable)
                    pendingTargetState = TargetFlipState.DOWN
                    faceDownStartTime = SystemClock.elapsedRealtime()
                    handler.postDelayed(debounceRunnable, FIXED_DEBOUNCE_DOWN_MS)
                    setFastSensorRate(true)
                    Log.i(TAG, "Scheduled DOWN debounce timer (${FIXED_DEBOUNCE_DOWN_MS}ms from T0), Z=$currentZValue, H=$hMag")
                }
            } else {
                // Instantly cancel countdown if phone departs from strictly flat face down
                if (pendingTargetState == TargetFlipState.DOWN) {
                    handler.removeCallbacks(debounceRunnable)
                    pendingTargetState = TargetFlipState.NONE
                    setFastSensorRate(false)
                    Log.i(TAG, "Cancelled pending DOWN debounce timer (phone tilted or moved), Z=$currentZValue, H=$hMag")
                }
            }
        } else {
            if (isExitCondition) {
                if (pendingTargetState != TargetFlipState.UP) {
                    handler.removeCallbacks(debounceRunnable)
                    pendingTargetState = TargetFlipState.UP
                    handler.postDelayed(debounceRunnable, DEBOUNCE_UP_MS)
                    Log.i(TAG, "Scheduled UP debounce timer (${DEBOUNCE_UP_MS}ms restore), Z=$currentZValue, H=$hMag")
                }
            } else if (isStrictlyFlatFaceDown) {
                if (pendingTargetState == TargetFlipState.UP) {
                    handler.removeCallbacks(debounceRunnable)
                    pendingTargetState = TargetFlipState.NONE
                    Log.i(TAG, "Cancelled pending UP debounce timer (phone returned flat), Z=$currentZValue, H=$hMag")
                }
            }
        }
    }

    // ── DND Management ─────────────────────────────────────────────────────

    private fun enableDoNotDisturb() {
        if (!notifManager.isNotificationPolicyAccessGranted) {
            Log.e(TAG, "Cannot enable DND: Notification Policy Access not granted!")
            return
        }
        try {
            // 1. Trigger haptic feedback FIRST before DND activation & screen locking
            triggerFlipDownHaptic()

            // 2. Check current DND mode prior to flip-down
            val currentFilter = notifManager.currentInterruptionFilter
            if (currentFilter == NotificationManager.INTERRUPTION_FILTER_ALL) {
                // DND was OFF when user flipped down -> service activates DND
                notifManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                wasDndActivatedByService = true
                Log.i(TAG, "DND mode activated by service (from INTERRUPTION_FILTER_ALL)")
            } else {
                // DND was ALREADY active prior to flip-down (e.g. system schedule or manual DND).
                // Do not override system DND lifecycle when flipping up later.
                wasDndActivatedByService = false
                Log.i(TAG, "DND was already active ($currentFilter) prior to flip-down; service will not override DND exit lifecycle")
            }

            persistState(active = true)
            _isDndActive.value = true
            updateNotification(active = true)

            // 3. Perform lock screen after haptic has been executed
            val autoLockPref = prefs.getBoolean(PrefsKeys.KEY_AUTO_LOCK_SCREEN, true)
            val accessibilityEnabled = FlipLockAccessibilityService.isAccessibilityServiceEnabled(this)

            if (autoLockPref && accessibilityEnabled) {
                val locked = FlipLockAccessibilityService.performLock()
                Log.i(TAG, "Auto lock screen performed via AccessibilityService, success=$locked")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling DND", e)
        }
    }

    private fun disableDoNotDisturb() {
        if (!_isDndActive.value) return
        try {
            if (notifManager.isNotificationPolicyAccessGranted) {
                if (wasDndActivatedByService) {
                    // Only restore when the filter is still the one this service set; if the user
                    // changed DND manually while face down, leave their choice intact.
                    // The service only ever activates DND from INTERRUPTION_FILTER_ALL, so ALL
                    // always reproduces the pre-flip state.
                    val currentFilter = notifManager.currentInterruptionFilter
                    if (currentFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
                        notifManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                        Log.i(TAG, "DND disabled, restored interruptionFilter=ALL")
                    } else {
                        Log.i(TAG, "DND was modified externally during flip-down ($currentFilter); leaving system DND state untouched")
                    }
                } else {
                    Log.i(TAG, "DND was already active prior to flip-down; leaving current system DND state untouched")
                }
            } else {
                Log.w(TAG, "Notification Policy Access not granted during disableDoNotDisturb; resetting internal state")
            }

            persistState(active = false)
            _isDndActive.value = false
            triggerFlipUpHaptic()
            updateNotification(active = false)
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling DND", e)
        }
    }

    private fun restoreDndIfNeeded() {
        if (_isDndActive.value) {
            try {
                if (notifManager.isNotificationPolicyAccessGranted &&
                    wasDndActivatedByService &&
                    notifManager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY
                ) {
                    notifManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                }
                persistState(active = false)
                _isDndActive.value = false
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring DND state", e)
            }
        }
    }

    // ── Persistence ────────────────────────────────────────────────────────

    private fun persistState(active: Boolean) {
        prefs.edit()
            .putBoolean(PrefsKeys.KEY_DND_ACTIVE, active)
            .putBoolean(PrefsKeys.KEY_WAS_DND_ACTIVATED_BY_SERVICE, wasDndActivatedByService)
            .apply()
    }

    // ── Fixed Default Dual-Pulse Haptic ─────────────────────────────────────

    private fun triggerFlipDownHaptic() {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        val audioAttrs = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .build()

        // Double pulse: Solid & Distinct "咚 - 咚"
        if (vib.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_THUD)) {
            val composition = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f, 65)
                .compose()
            vib.vibrate(composition, audioAttrs)
        } else {
            val timings = longArrayOf(0, 28, 65, 40)
            val amplitudes = intArrayOf(0, 255, 0, 255)
            vib.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1), audioAttrs)
        }
    }

    private fun triggerFlipUpHaptic() {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        val audioAttrs = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .build()

        // Subtle single click feedback on flip-up
        if (vib.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)) {
            val composition = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f)
                .compose()
            vib.vibrate(composition, audioAttrs)
        } else {
            val timings = longArrayOf(0, 22)
            val amplitudes = intArrayOf(0, 200)
            vib.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1), audioAttrs)
        }
    }

    // ── Notifications ──────────────────────────────────────────────────────

    private fun getLocalizedText(key: String): String {
        val langMode = if (::prefs.isInitialized) prefs.getInt(PrefsKeys.KEY_LANGUAGE_MODE, 0) else 0
        val lang = Localization.resolve(this, langMode)
        val isEng = lang == AppLanguage.ENGLISH
        val isTrad = lang == AppLanguage.TRADITIONAL

        return when (key) {
            "channel_name" -> if (isEng) "Flip to Shhh Service" else if (isTrad) "Flip to Shhh 服務" else "Flip to Shhh 服务"
            "channel_desc" -> if (isEng) "Flip to mute gesture detection service" else if (isTrad) "Flip to Shhh 翻轉靜音服務" else "Flip to Shhh 翻转静音服务"
            "notif_active_title" -> if (isEng) "Do Not Disturb Active" else if (isTrad) "已開啟勿擾模式" else "已开启勿扰模式"
            "notif_active_desc" -> if (isEng) "Phone face down · Calls & notifications muted" else if (isTrad) "手機螢幕朝下 · 來電與通知已靜音" else "手机面朝下 · 来电与通知已静音"
            "notif_idle_title" -> if (isEng) "Flip to Shhh is Running" else if (isTrad) "Flip to Shhh 執行中" else "Flip to Shhh 运行中"
            "notif_idle_desc" -> if (isEng) "Flip phone face down to mute" else if (isTrad) "翻轉手機螢幕朝下即可自動開啟勿擾" else "翻转手机面朝下即可自动开启勿扰"
            else -> ""
        }
    }

    private fun createNotificationChannel() {
        val name = getLocalizedText("channel_name")
        val desc = getLocalizedText("channel_desc")
        // Channel names are frozen at creation time by the system, so each locale gets its own
        // channel ID. A language switch never deletes the channel currently hosting the
        // foreground notification (deletion would cancel it and reset user settings); the old
        // channel is retired by the caller only after the notification has been re-posted.
        val channelId = "${CHANNEL_ID}_${Integer.toHexString(name.hashCode())}"
        if (notifManager.getNotificationChannel(channelId) == null) {
            val channel = NotificationChannel(channelId, name, NotificationManager.IMPORTANCE_MIN).apply {
                description = desc
                setShowBadge(false)
            }
            notifManager.createNotificationChannel(channel)
        }
        currentChannelId = channelId
    }

    private fun updateNotification(active: Boolean) {
        notifManager.notify(NOTIFICATION_ID, buildNotification(active = active))
    }

    private fun buildNotification(active: Boolean): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, currentChannelId)
            .setSmallIcon(R.drawable.ic_notification_shhh)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        if (active) {
            builder
                .setContentTitle(getLocalizedText("notif_active_title"))
                .setContentText(getLocalizedText("notif_active_desc"))
        } else {
            builder
                .setContentTitle(getLocalizedText("notif_idle_title"))
                .setContentText(getLocalizedText("notif_idle_desc"))
        }

        return builder.build()
    }

    // ── Companion ──────────────────────────────────────────────────────────

    companion object {
        const val CHANNEL_ID = "flip_to_shhh_channel"
        const val NOTIFICATION_ID = 1001
        private const val TAG = "FlipToShhh"

        // Fixed 2.0-second debounce wait time for flip-down
        private const val FIXED_DEBOUNCE_DOWN_MS = 2000L
        private const val DEBOUNCE_UP_MS = 300L

        // Real-world calibrated face-down flatness thresholds (supports camera bumps and case elevation):
        // Normal gravity magnitude = 9.81 m/s^2.
        // Z <= -9.0 m/s^2 corresponds to screen tilt angle <= 23° from horizontal.
        private const val FACE_DOWN_ENTER_Z_THRESHOLD = -9.0f
        private const val MAX_HORIZONTAL_GRAVITY = 2.5f

        // Exit thresholds: tilt beyond ~40° or screen face up
        private const val FACE_DOWN_EXIT_Z_THRESHOLD = -7.5f
        private const val EXIT_HORIZONTAL_GRAVITY = 3.5f

        // Table stillness thresholds (filters out physiological hand tremor in mid-air for virtual proximity):
        // Solid table: Gyro < 0.02 rad/s, Delta G < 0.03 m/s^2.
        // Handheld in mid-air: Gyro > 0.05 rad/s, Delta G > 0.07 m/s^2 due to 8-12Hz muscle tremor.
        private const val MAX_DELTA_G_TABLE_STILLNESS = 0.07f
        private const val MAX_GYRO_TABLE_STILLNESS = 0.05f

        // Final stillness threshold required at 2.0s mark
        private const val MAX_DELTA_G_FINAL_CHECK = 0.12f
        private const val MAX_GYRO_FINAL_CHECK = 0.08f

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _isFlippedDown = MutableStateFlow(false)
        val isFlippedDown: StateFlow<Boolean> = _isFlippedDown.asStateFlow()

        private val _isDndActive = MutableStateFlow(false)
        val isDndActive: StateFlow<Boolean> = _isDndActive.asStateFlow()
    }
}
