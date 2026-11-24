package shhh.cicada.flip

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationAttributes
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
 * Detection is split into two framework-free collaborators:
 *  - [StillnessDetector] owns the sample window, warm-up and variance math.
 *  - [FlipStateMachine] owns the countdown/exit/retry/suppression logic.
 *
 * This class only translates Android callbacks into those pure inputs and
 * applies the returned decisions (sensor mode, wake lock, timers, DND).
 *
 * Algorithm highlights:
 *  - Strict flatness & gravity vector analysis: the phone must rest flat on a
 *    surface, with sqrt(X^2+Y^2) <= 2.5 m/s^2 (about 14.8 degrees of tilt).
 *  - Optical proximity verifies that the screen faces a physical surface. The
 *    first sample is awaited for up to 500 ms so a cold sensor cannot wave the
 *    phone through while it is still in mid-air.
 *  - 50 Hz accelerometer/gyroscope variance separates a rigid tabletop from
 *    physiological hand tremor. The first 300 ms after entering the high-rate
 *    window is discarded to skip cold-start transients and landing impacts.
 *  - 2.0 s stillness window (2.5 s when no gyroscope is available).
 *  - Retry circuit breaker and external-DND suppression avoid battery-draining
 *    loops and DND "bounce-back".
 *  - Low-power listening uses a non-wake-up orientation sensor with hardware
 *    FIFO batching. Once flipped, continuous gravity sampling is replaced by a
 *    one-shot significant-motion sensor plus wake-up proximity.
 */
class FlipToShhhService : LifecycleService(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var notifManager: NotificationManager
    private lateinit var powerManager: PowerManager
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var prefs: SharedPreferences

    /** Dedicated thread for 50 Hz callbacks, debounce timers and state changes. */
    private lateinit var sensorThread: HandlerThread
    private lateinit var sensorHandler: Handler

    private var gravitySensor: Sensor? = null
    private var accelerometerSensor: Sensor? = null
    private var gyroscopeSensor: Sensor? = null
    private var proximitySensor: Sensor? = null
    private var significantMotionSensor: Sensor? = null
    private var activeOrientationSensor: Sensor? = null
    private var hasOpticalProximity: Boolean = false

    private lateinit var stillnessDetector: StillnessDetector
    private lateinit var flipStateMachine: FlipStateMachine

    private var currentXValue: Float = 0f
    private var currentYValue: Float = 0f
    private var currentZValue: Float = 0f

    private var isProximityNear: Boolean = false
    private var hasOrientationSample: Boolean = false
    private var hasProximitySample: Boolean = false

    private var wasDndActivatedByService: Boolean = false

    private enum class SensorMode {
        STOPPED,
        /** Batched, non-wake-up orientation sensor for face-down detection. */
        IDLE_LISTENING,
        /** 50 Hz accelerometer + gyroscope while the countdown is running. */
        FAST_SAMPLING,
        /** Flipped: significant motion (if available) + wake-up proximity. */
        FLIPPED_LOW_POWER,
        /** A lift was signalled; gravity is back on for the exit debounce. */
        EXIT_VALIDATION,
    }

    private var sensorMode = SensorMode.STOPPED

    private val sensorLock = Any()

    @Volatile
    private var shuttingDown = false

    private val returnToLowPowerRunnable = Runnable {
        if (flipStateMachine.phase == FlipStateMachine.Phase.FLIPPED) {
            applySensorMode(SensorMode.FLIPPED_LOW_POWER)
        }
    }

    private val debounceRunnable = Runnable {
        if (!this::flipStateMachine.isInitialized) return@Runnable
        val now = SystemClock.elapsedRealtime()
        val stillness = if (flipStateMachine.phase == FlipStateMachine.Phase.DOWN_PENDING) {
            stillnessDetector.result()
        } else {
            null
        }
        applyDecision(flipStateMachine.onTimer(now, stillness))
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock(timeoutMs: Long = 3_000L) {
        try {
            // setReferenceCounted(false): a repeated acquire simply extends the
            // timeout, so there is no release/acquire gap.
            wakeLock?.acquire(timeoutMs.coerceAtLeast(1L))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire wake lock", e)
            releaseWakeLock()
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release wake lock", e)
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    private val dndReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED) {
                return
            }

            val hasPerm = notifManager.isNotificationPolicyAccessGranted
            if (!hasPerm) {
                Log.w(TAG, "Notification policy access revoked externally; stopping service")
                sensorHandler.post {
                    _isDndActive.value = false
                    _isFlippedDown.value = false
                    wasDndActivatedByService = false
                    persistState(active = false)
                    updateNotification(active = false)
                    flipStateMachine.reset(startFlipped = false)
                    applySensorMode(SensorMode.STOPPED, force = true)
                    releaseWakeLock()
                    stopSelf()
                }
                return
            }

            val currentFilter = notifManager.currentInterruptionFilter
            if (currentFilter == NotificationManager.INTERRUPTION_FILTER_ALL && _isDndActive.value) {
                Log.i(TAG, "DND turned off externally; latching suppression until the phone is lifted")
                sensorHandler.post {
                    // Our own flip-up restore also posts INTERRUPTION_FILTER_ALL;
                    // if we already reset the flag this was not an external change.
                    if (!_isDndActive.value) return@post
                    _isDndActive.value = false
                    _isFlippedDown.value = false
                    wasDndActivatedByService = false
                    persistState(active = false)
                    updateNotification(active = false)
                    // Do not let the still-face-down phone immediately re-enable DND.
                    applyDecision(flipStateMachine.onExternalDndDisabled(SystemClock.elapsedRealtime()))
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        createNotificationChannel()
        updateNotification(active = _isDndActive.value)
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate")
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "flip2shhh:DebounceWakeLock").apply {
            setReferenceCounted(false)
        }
        vibrator = (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        prefs = getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)

        sensorThread = HandlerThread("flip2shhh-sensors").apply { start() }
        sensorHandler = Handler(sensorThread.looper)

        val dndFilter = IntentFilter(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        registerReceiver(dndReceiver, dndFilter, Context.RECEIVER_NOT_EXPORTED)

        // Prefer non-wake-up sensors for always-on orientation listening, and a
        // wake-up proximity sensor so lifting the phone is still noticed.
        gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY, false)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY, true)
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, false)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, true)
        gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE, false)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE, true)
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY, true)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY, false)
        significantMotionSensor = sensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)
        activeOrientationSensor = gravitySensor ?: accelerometerSensor

        hasOpticalProximity = isHardwareOpticalProximity(proximitySensor)

        stillnessDetector = StillnessDetector(hasGyroscope = gyroscopeSensor != null)
        flipStateMachine = FlipStateMachine(
            hasGyroscope = gyroscopeSensor != null,
            hasOpticalProximity = hasOpticalProximity,
        )

        Log.i(
            TAG,
            "Active orientation: ${activeOrientationSensor?.name}, Gyro: ${gyroscopeSensor?.name}, " +
                "Proximity: ${proximitySensor?.name} (Optical: $hasOpticalProximity), " +
                "SignificantMotion: ${significantMotionSensor?.name}"
        )

        // Restore persisted state if the service was killed and restarted.
        if (prefs.getBoolean(PrefsKeys.KEY_DND_ACTIVE, false)) {
            val systemDndStillOn = notifManager.isNotificationPolicyAccessGranted &&
                    notifManager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
            if (systemDndStillOn) {
                wasDndActivatedByService = prefs.getBoolean(PrefsKeys.KEY_WAS_DND_ACTIVATED_BY_SERVICE, false)
                _isDndActive.value = true
                _isFlippedDown.value = true
            } else {
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
                name.contains("ultrasound") || name.contains("elliptic") || vendor.contains("elliptic")
        return !isVirtual
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.i(TAG, "onStartCommand")
        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        startForeground(NOTIFICATION_ID, buildNotification(active = _isDndActive.value), fgsType)

        sensorHandler.removeCallbacksAndMessages(null)
        sensorHandler.post { startDetection() }
        _isRunning.value = true
        return START_STICKY
    }

    private fun startDetection() {
        hasOrientationSample = false
        hasProximitySample = false
        isProximityNear = false
        currentXValue = 0f
        currentYValue = 0f
        currentZValue = 0f
        stillnessDetector.reset()
        flipStateMachine.reset(startFlipped = _isFlippedDown.value)
        releaseWakeLock()
        applySensorMode(
            if (flipStateMachine.phase == FlipStateMachine.Phase.FLIPPED) {
                SensorMode.FLIPPED_LOW_POWER
            } else {
                SensorMode.IDLE_LISTENING
            },
            force = true,
        )
    }

    override fun onDestroy() {
        shuttingDown = true
        try {
            unregisterReceiver(dndReceiver)
        } catch (_: Exception) {}

        // Stop callbacks, then wait briefly so no sensor/timer callback mutates
        // state while the service is being torn down.
        if (this::sensorManager.isInitialized) {
            synchronized(sensorLock) {
                sensorManager.unregisterListener(this)
            }
        }
        if (this::sensorHandler.isInitialized) {
            sensorHandler.removeCallbacksAndMessages(null)
        }
        if (this::sensorThread.isInitialized) {
            sensorThread.quitSafely()
            try {
                sensorThread.join(1_000L)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }

        restoreDndIfNeeded()
        sensorMode = SensorMode.STOPPED
        releaseWakeLock()
        wakeLock = null
        hasOrientationSample = false
        hasProximitySample = false
        _isRunning.value = false
        _isFlippedDown.value = false
        _isDndActive.value = false
        super.onDestroy()
    }

    // ── Sensor Management ──────────────────────────────────────────────────

    private fun applySensorMode(mode: SensorMode, force: Boolean = false) {
        synchronized(sensorLock) {
            if (shuttingDown) return
            if (!force && sensorMode == mode) return
            sensorManager.unregisterListener(this)
            sensorMode = mode

            when (mode) {
                SensorMode.STOPPED -> Unit
                SensorMode.IDLE_LISTENING -> {
                    registerOrientation(SensorManager.SENSOR_DELAY_UI, IDLE_BATCH_LATENCY_US)
                    registerProximity()
                }
                SensorMode.FAST_SAMPLING -> {
                    registerOrientation(SensorManager.SENSOR_DELAY_GAME, 0)
                    if (accelerometerSensor != null &&
                        accelerometerSensor?.type != activeOrientationSensor?.type
                    ) {
                        sensorManager.registerListener(
                            this,
                            accelerometerSensor,
                            SensorManager.SENSOR_DELAY_GAME,
                            0,
                            sensorHandler,
                        )
                    }
                    gyroscopeSensor?.let { gyro ->
                        sensorManager.registerListener(
                            this,
                            gyro,
                            SensorManager.SENSOR_DELAY_GAME,
                            0,
                            sensorHandler,
                        )
                    }
                    registerProximity()
                }
                SensorMode.FLIPPED_LOW_POWER -> {
                    registerProximity()
                    if (significantMotionSensor != null) {
                        // One-shot trigger; the continuous gravity stream is off.
                        sensorManager.registerListener(
                            this,
                            significantMotionSensor,
                            SensorManager.SENSOR_DELAY_NORMAL,
                            0,
                            sensorHandler,
                        )
                    } else {
                        registerOrientation(SensorManager.SENSOR_DELAY_UI, IDLE_BATCH_LATENCY_US)
                    }
                }
                SensorMode.EXIT_VALIDATION -> {
                    registerOrientation(SensorManager.SENSOR_DELAY_UI, 0)
                    registerProximity()
                }
            }
        }
    }

    private fun registerOrientation(samplingPeriodUs: Int, maxReportLatencyUs: Int) {
        val sensor = activeOrientationSensor ?: return
        val registered = sensorManager.registerListener(
            this,
            sensor,
            samplingPeriodUs,
            maxReportLatencyUs,
            sensorHandler,
        )
        if (!registered && maxReportLatencyUs > 0) {
            // Some sensors reject FIFO batching; fall back to immediate delivery.
            sensorManager.registerListener(this, sensor, samplingPeriodUs, sensorHandler)
        }
    }

    private fun registerProximity() {
        proximitySensor?.let { prox ->
            val registered = sensorManager.registerListener(
                this,
                prox,
                SensorManager.SENSOR_DELAY_NORMAL,
                0,
                sensorHandler,
            )
            if (!registered) {
                sensorManager.registerListener(this, prox, SensorManager.SENSOR_DELAY_NORMAL, sensorHandler)
            }
        }
    }

    private fun onLiftSignal() {
        if (sensorMode == SensorMode.EXIT_VALIDATION) return
        if (flipStateMachine.phase != FlipStateMachine.Phase.FLIPPED) return
        sensorHandler.removeCallbacks(returnToLowPowerRunnable)
        applySensorMode(SensorMode.EXIT_VALIDATION)
        // If the motion did not actually lift the phone, return to low power.
        sensorHandler.postDelayed(returnToLowPowerRunnable, EXIT_VALIDATION_TIMEOUT_MS)
    }

    // ── Sensor Events ──────────────────────────────────────────────────────

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        if (shuttingDown) return
        val now = SystemClock.elapsedRealtime()

        when (event.sensor.type) {
            Sensor.TYPE_PROXIMITY -> {
                val distance = event.values[0]
                val maxRange = proximitySensor?.maximumRange?.takeIf { it > 0f } ?: 5.0f
                hasProximitySample = true
                isProximityNear = if (maxRange <= 4.0f) distance < maxRange else distance <= 4.0f

                if (hasOpticalProximity && !isProximityNear) {
                    onLiftSignal()
                }
                updateDetection(now)
            }

            Sensor.TYPE_SIGNIFICANT_MOTION -> {
                Log.i(TAG, "Significant motion detected while flipped; validating exit")
                onLiftSignal()
            }

            Sensor.TYPE_GYROSCOPE -> {
                val gx = event.values[0]
                val gy = event.values[1]
                val gz = event.values[2]

                if (flipStateMachine.phase == FlipStateMachine.Phase.DOWN_PENDING) {
                    val magnitude = sqrt(gx * gx + gy * gy + gz * gz)
                    if (magnitude > MAX_GYRO_TABLE_STILLNESS) {
                        // Restart the window and clear buffers together so the
                        // disturbance sample cannot survive into the next try.
                        flipStateMachine.onMotionDisturbance(now)?.let { applyDecision(it) }
                    }
                }
                stillnessDetector.addGyroscope(gx, gy, gz, now)
            }

            Sensor.TYPE_ACCELEROMETER -> {
                val ax = event.values[0]
                val ay = event.values[1]
                val az = event.values[2]
                stillnessDetector.addAccelerometer(ax, ay, az, now)
                if (activeOrientationSensor?.type == Sensor.TYPE_ACCELEROMETER) {
                    updateOrientation(ax, ay, az, now)
                }
            }

            Sensor.TYPE_GRAVITY -> {
                if (activeOrientationSensor?.type == Sensor.TYPE_GRAVITY) {
                    updateOrientation(event.values[0], event.values[1], event.values[2], now)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun updateOrientation(x: Float, y: Float, z: Float, now: Long) {
        hasOrientationSample = true
        currentXValue = x
        currentYValue = y
        currentZValue = z
        updateDetection(now)
    }

    private fun updateDetection(now: Long) {
        applyDecision(flipStateMachine.onObservation(buildObservation(now)))
    }

    private fun buildObservation(now: Long): FlipStateMachine.Observation {
        val hMag = sqrt(currentXValue * currentXValue + currentYValue * currentYValue)
        val proximity = when {
            !hasOpticalProximity -> FlipStateMachine.Proximity.NoOpticalSensor
            !hasProximitySample -> FlipStateMachine.Proximity.AwaitingFirstSample
            isProximityNear -> FlipStateMachine.Proximity.Near
            else -> FlipStateMachine.Proximity.Far
        }
        return FlipStateMachine.Observation(
            timestampMs = now,
            hasOrientationSample = hasOrientationSample,
            gravityZ = currentZValue,
            horizontalGravity = hMag,
            proximity = proximity,
        )
    }

    private fun applyDecision(decision: FlipStateMachine.Decision) {
        if (shuttingDown) return
        val now = SystemClock.elapsedRealtime()

        if (decision.cancelScheduledCheck) {
            sensorHandler.removeCallbacks(debounceRunnable)
        }
        if (decision.resetStillness) {
            stillnessDetector.reset(now)
        }

        when {
            decision.acquireWakeLockMs != null -> acquireWakeLock(decision.acquireWakeLockMs)
            decision.releaseWakeLock -> releaseWakeLock()
        }

        if (decision.scheduleCheckAtMs != null) {
            sensorHandler.removeCallbacks(debounceRunnable)
            val delay = (decision.scheduleCheckAtMs - now).coerceAtLeast(0L)
            sensorHandler.postDelayed(debounceRunnable, delay)
            Log.i(
                TAG,
                "Stillness countdown armed in ${delay}ms (retry=${decision.retryCount}, fast=${decision.useFastSensors})"
            )
        }

        if (decision.phase == FlipStateMachine.Phase.COOLDOWN && decision.cancelScheduledCheck) {
            Log.i(TAG, "Stillness retry limit reached; entering cooldown until the phone is lifted")
        }

        val targetMode = when {
            decision.useFastSensors -> SensorMode.FAST_SAMPLING
            decision.phase == FlipStateMachine.Phase.FLIPPED &&
                decision.pendingTarget == FlipStateMachine.Target.UP -> SensorMode.EXIT_VALIDATION
            decision.phase == FlipStateMachine.Phase.FLIPPED -> SensorMode.FLIPPED_LOW_POWER
            else -> SensorMode.IDLE_LISTENING
        }
        applySensorMode(targetMode)

        if (decision.enterDnd) {
            Log.i(TAG, "Stillness confirmed: entering face-down DND (Z=$currentZValue)")
            _isFlippedDown.value = true
            if (!enableDoNotDisturb()) {
                _isFlippedDown.value = false
                _isDndActive.value = false
                flipStateMachine.reset(startFlipped = false)
                applySensorMode(SensorMode.IDLE_LISTENING)
                releaseWakeLock()
            }
        }
        if (decision.exitDnd) {
            Log.i(TAG, "Face-down exit confirmed: restoring previous DND state (Z=$currentZValue)")
            _isFlippedDown.value = false
            disableDoNotDisturb()
        }
    }

    // ── DND Management ─────────────────────────────────────────────────────

    /** @return true when DND was handled (or the service was asked to stop). */
    private fun enableDoNotDisturb(): Boolean {
        if (!notifManager.isNotificationPolicyAccessGranted) {
            Log.e(TAG, "Cannot enable DND: Notification Policy Access not granted!")
            _isFlippedDown.value = false
            _isDndActive.value = false
            updateNotification(active = false)
            stopSelf()
            return false
        }
        try {
            // 1. Haptic feedback first, before DND activation and screen locking.
            triggerFlipDownHaptic()

            // 2. Remember whether DND was already on.
            val currentFilter = notifManager.currentInterruptionFilter
            if (currentFilter == NotificationManager.INTERRUPTION_FILTER_ALL) {
                notifManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                wasDndActivatedByService = true
                Log.i(TAG, "DND mode activated by service (from INTERRUPTION_FILTER_ALL)")
            } else {
                wasDndActivatedByService = false
                Log.i(TAG, "DND was already active ($currentFilter) prior to flip-down; leaving it alone")
            }

            persistState(active = true)
            _isDndActive.value = true
            updateNotification(active = true)

            // 3. Lock the screen after the haptic, if enabled.
            val autoLockPref = prefs.getBoolean(PrefsKeys.KEY_AUTO_LOCK_SCREEN, false)
            val accessibilityEnabled = FlipLockAccessibilityService.isAccessibilityServiceEnabled(this)

            if (autoLockPref && accessibilityEnabled && powerManager.isInteractive) {
                sensorHandler.postDelayed({
                    if (_isFlippedDown.value && powerManager.isInteractive) {
                        val locked = FlipLockAccessibilityService.performLock()
                        Log.i(TAG, "Auto lock screen performed via AccessibilityService, success=$locked")
                    }
                }, 150L)
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling DND", e)
            return false
        }
    }

    private fun disableDoNotDisturb() {
        try {
            if (_isDndActive.value) {
                if (notifManager.isNotificationPolicyAccessGranted) {
                    if (wasDndActivatedByService) {
                        val currentFilter = notifManager.currentInterruptionFilter
                        if (currentFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
                            notifManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                            Log.i(TAG, "DND disabled, restored interruptionFilter=ALL")
                        } else {
                            Log.i(TAG, "DND was modified externally during flip-down ($currentFilter); leaving it untouched")
                        }
                    } else {
                        Log.i(TAG, "DND was already active prior to flip-down; leaving current DND state untouched")
                    }
                } else {
                    Log.w(TAG, "Notification Policy Access not granted during disableDoNotDisturb; resetting internal state")
                }

                wasDndActivatedByService = false
                persistState(active = false)
                _isDndActive.value = false
                updateNotification(active = false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling DND", e)
        }
        triggerFlipUpHaptic()
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
        val vibAttrs = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_HARDWARE_FEEDBACK)

        if (vib.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_THUD)) {
            val composition = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f, 65)
                .compose()
            vib.vibrate(composition, vibAttrs)
        } else {
            val timings = longArrayOf(0, 28, 65, 40)
            if (vib.hasAmplitudeControl()) {
                val amplitudes = intArrayOf(0, 255, 0, 255)
                vib.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1), vibAttrs)
            } else {
                vib.vibrate(VibrationEffect.createWaveform(timings, -1), vibAttrs)
            }
        }
    }

    private fun triggerFlipUpHaptic() {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        val vibAttrs = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_HARDWARE_FEEDBACK)

        if (vib.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK)) {
            val composition = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f)
                .compose()
            vib.vibrate(composition, vibAttrs)
        } else {
            val timings = longArrayOf(0, 22)
            if (vib.hasAmplitudeControl()) {
                val amplitudes = intArrayOf(0, 200)
                vib.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1), vibAttrs)
            } else {
                vib.vibrate(VibrationEffect.createWaveform(timings, -1), vibAttrs)
            }
        }
    }

    // ── Notifications ──────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val name = getString(R.string.channel_name)
        val desc = getString(R.string.channel_desc)
        val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_MIN).apply {
            description = desc
            setShowBadge(false)
        }
        notifManager.createNotificationChannel(channel)
    }

    private fun updateNotification(active: Boolean) {
        // Notification posting and updates completely suppressed
    }

    private fun buildNotification(active: Boolean): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_shhh)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        if (active) {
            builder
                .setContentTitle(getString(R.string.notif_active_title))
                .setContentText(getString(R.string.notif_active_desc))
        } else {
            builder
                .setContentTitle(getString(R.string.notif_idle_title))
                .setContentText(getString(R.string.notif_idle_desc))
        }

        return builder.build()
    }

    // ── Companion ──────────────────────────────────────────────────────────

    companion object {
        const val CHANNEL_ID = "flip_to_shhh_channel"
        const val NOTIFICATION_ID = 1001
        private const val TAG = "FlipToShhh"

        /** High-rate gyro magnitude above which the countdown is restarted. */
        private const val MAX_GYRO_TABLE_STILLNESS = 0.12f

        /** 500 ms batching lets the sensor hub buffer idle orientation samples. */
        private const val IDLE_BATCH_LATENCY_US = 500_000

        /** Exit validation is time-boxed before returning to low-power mode. */
        private const val EXIT_VALIDATION_TIMEOUT_MS = 1_000L

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        /** Alias kept for external callers that use the longer name. */
        val isServiceRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _isFlippedDown = MutableStateFlow(false)
        val isFlippedDown: StateFlow<Boolean> = _isFlippedDown.asStateFlow()

        private val _isDndActive = MutableStateFlow(false)
        val isDndActive: StateFlow<Boolean> = _isDndActive.asStateFlow()
    }
}
