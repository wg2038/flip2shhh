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
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

/**
 * Flip to Shhh Service. Flip the phone face down to mute, flip back to restore.
 *
 * Algorithm highlights:
 *  - Strict flatness & gravity vector analysis: ensures device is resting flat on a surface (tilt <= 15° - 20°).
 *  - Persistent optical proximity verification: screen must face a physical surface (desk/table), preventing false triggers in mid-air.
 *  - 50Hz raw accelerometer sliding-window variance and gyroscope energy analysis: strictly differentiates tabletop resting from physiological hand tremor.
 *  - Continuous 2.0s stillness window: guarantees the phone remains stationary on the surface before triggering DND.
 *  - Dual-pulse haptic feedback on flip-down ("咚 - 咚"), subtle click on flip-up.
 *  - Smart DND ownership tracking: never overwrites or clears external system-scheduled DND rules.
 */
class FlipToShhhService : LifecycleService(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var notifManager: NotificationManager
    private lateinit var powerManager: PowerManager
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())

    private var gravitySensor: Sensor? = null
    private var accelerometerSensor: Sensor? = null
    private var gyroscopeSensor: Sensor? = null
    private var proximitySensor: Sensor? = null
    private var activeOrientationSensor: Sensor? = null
    private var hasOpticalProximity: Boolean = false
    private var isProximityRegistered: Boolean = false

    private var currentXValue: Float = 0f
    private var currentYValue: Float = 0f
    private var currentZValue: Float = 0f

    private var hasAccelSample: Boolean = false
    private var prevAccelX: Float = 0f
    private var prevAccelY: Float = 0f
    private var prevAccelZ: Float = 0f
    private var currentDeltaA: Float = 0f

    private var currentGyroRotation: Float = 0f
    private var isProximityNear: Boolean = false
    private var hasOrientationSample: Boolean = false
    private var hasProximitySample: Boolean = false

    // Sliding-window circular buffers for statistical variance calculation at 50Hz
    private val accelXBuffer = FloatArray(VARIANCE_WINDOW_SIZE)
    private val accelYBuffer = FloatArray(VARIANCE_WINDOW_SIZE)
    private val accelZBuffer = FloatArray(VARIANCE_WINDOW_SIZE)
    private var accelBufferIndex = 0
    private var accelBufferCount = 0

    private val gyroSqBuffer = FloatArray(VARIANCE_WINDOW_SIZE)
    private var gyroBufferIndex = 0
    private var gyroBufferCount = 0

    private var faceDownStartTime: Long = 0L

    // Generation token used to keep our own wake-lock safety timer from releasing a
    // newer acquisition. WakeLock.acquire(timeout) posts an uncancellable releaser in
    // AOSP, so re-arming a timed lock while one is held can release the replacement
    // early; acquire without a timeout and enforce the timeout here instead.
    private var wakeLockGeneration: Long = 0L

    private var wasDndActivatedByService: Boolean = false

    // Gravity/gyroscope run at the low-power UI rate except while a flip-down countdown is
    // pending, when they are oversampled so hand tremor is not aliased (see registerSensors).
    private var usingFastSensorRate: Boolean = false

    private var currentChannelId: String = CHANNEL_ID

    private enum class TargetFlipState { NONE, DOWN, UP }
    private var pendingTargetState: TargetFlipState = TargetFlipState.NONE

    @SuppressLint("WakelockTimeout", "Wakelock")
    private fun acquireWakeLock(timeoutMs: Long = 3000L) {
        val lock = wakeLock ?: return
        try {
            val generation = ++wakeLockGeneration
            // Re-arm from a clean state so a safety timer from the previous countdown
            // cannot release this acquisition.
            if (lock.isHeld) lock.release()
            lock.acquire()
            handler.postDelayed({
                if (generation == wakeLockGeneration) {
                    releaseWakeLock()
                }
            }, timeoutMs.coerceAtLeast(1L))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire wake lock", e)
            releaseWakeLock()
        }
    }

    private fun releaseWakeLock() {
        wakeLockGeneration++
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release wake lock", e)
        }
    }

    private fun resetBuffers() {
        accelBufferIndex = 0
        accelBufferCount = 0
        gyroBufferIndex = 0
        gyroBufferCount = 0
    }

    private fun addAccelSample(x: Float, y: Float, z: Float) {
        accelXBuffer[accelBufferIndex] = x
        accelYBuffer[accelBufferIndex] = y
        accelZBuffer[accelBufferIndex] = z
        accelBufferIndex = (accelBufferIndex + 1) % VARIANCE_WINDOW_SIZE
        if (accelBufferCount < VARIANCE_WINDOW_SIZE) {
            accelBufferCount++
        }
    }

    private fun computeAccelVariance(): Float {
        val count = accelBufferCount
        if (count < 15) return Float.MAX_VALUE
        var sumX = 0f
        var sumY = 0f
        var sumZ = 0f
        for (i in 0 until count) {
            sumX += accelXBuffer[i]
            sumY += accelYBuffer[i]
            sumZ += accelZBuffer[i]
        }
        val meanX = sumX / count
        val meanY = sumY / count
        val meanZ = sumZ / count

        var varSum = 0f
        for (i in 0 until count) {
            val dx = accelXBuffer[i] - meanX
            val dy = accelYBuffer[i] - meanY
            val dz = accelZBuffer[i] - meanZ
            varSum += (dx * dx + dy * dy + dz * dz)
        }
        return varSum / count
    }

    private fun addGyroSample(gx: Float, gy: Float, gz: Float) {
        val normSq = gx * gx + gy * gy + gz * gz
        gyroSqBuffer[gyroBufferIndex] = normSq
        gyroBufferIndex = (gyroBufferIndex + 1) % VARIANCE_WINDOW_SIZE
        if (gyroBufferCount < VARIANCE_WINDOW_SIZE) {
            gyroBufferCount++
        }
    }

    private fun computeGyroEnergy(): Float {
        val count = gyroBufferCount
        if (count < 15) return Float.MAX_VALUE
        var sum = 0f
        for (i in 0 until count) {
            sum += gyroSqBuffer[i]
        }
        return sum / count
    }

    // Proximity gate: On devices with hardware optical proximity sensors, entry is only
    // permitted when the screen is physically facing a close surface (NEAR). Mid-air floating
    // reports FAR and immediately blocks entry. Virtual proximity devices fall back to the
    // rigorous 50Hz accelerometer variance and gyro energy analysis.
    private fun isProximityPermitting(): Boolean {
        if (!hasOpticalProximity) return true
        if (!hasProximitySample) return true
        return isProximityNear
    }

    private val debounceRunnable: Runnable = Runnable {
        val currentlyFlipped = _isFlippedDown.value
        val hMag = sqrt(currentXValue * currentXValue + currentYValue * currentYValue)
        val isOrientationFlatDown = (currentZValue <= FACE_DOWN_ENTER_Z_THRESHOLD) && (hMag <= MAX_HORIZONTAL_GRAVITY)
        val isFlatFaceDownOrientation = isOrientationFlatDown && isProximityPermitting()

        if (pendingTargetState == TargetFlipState.DOWN && !currentlyFlipped) {
            val now = SystemClock.elapsedRealtime()
            val elapsedTime = now - faceDownStartTime
            val remainingMs = FIXED_DEBOUNCE_DOWN_MS - elapsedTime

            if (remainingMs > 50L) {
                if (isFlatFaceDownOrientation) {
                    acquireWakeLock(remainingMs + 1000L)
                    handler.postDelayed(debounceRunnable, remainingMs)
                    return@Runnable
                } else {
                    Log.i(TAG, "Cancelling debounce countdown: phone tilted or moved (Z=$currentZValue, H=$hMag)")
                    pendingTargetState = TargetFlipState.NONE
                    releaseWakeLock()
                    setFastSensorRate(false)
                    resetBuffers()
                    return@Runnable
                }
            }

            val accelVar = computeAccelVariance()
            val gyroEnergy = computeGyroEnergy()
            val isPhysicallyStationary = (accelVar <= MAX_TABLE_ACCEL_VARIANCE) && (gyroEnergy <= MAX_TABLE_GYRO_ENERGY)

            if (isFlatFaceDownOrientation && isPhysicallyStationary) {
                if (!notifManager.isNotificationPolicyAccessGranted) {
                    Log.w(TAG, "Cannot enter Face Down mode: Notification Policy Access not granted!")
                    _isFlippedDown.value = false
                    _isDndActive.value = false
                    pendingTargetState = TargetFlipState.NONE
                    releaseWakeLock()
                    setFastSensorRate(false)
                    stopSelf()
                    return@Runnable
                }
                Log.i(TAG, "Debounce confirmed (2000ms elapsed): entering Face Down mode (DND ON), Z=$currentZValue, H=$hMag, accelVar=$accelVar, gyroEnergy=$gyroEnergy, proxNear=$isProximityNear, optProx=$hasOpticalProximity")
                _isFlippedDown.value = true
                enableDoNotDisturb()
                setFastSensorRate(false)
            } else if (isFlatFaceDownOrientation) {
                // Phone is flat, but hand tremor or physical movement was detected by variance or gyro.
                // Restart full stillness verification window instead of a leaky 250ms loop.
                Log.i(TAG, "Debounce rejected: not stationary on a surface (accelVar=$accelVar, gyroEnergy=$gyroEnergy), restarting stillness verification")
                faceDownStartTime = SystemClock.elapsedRealtime()
                resetBuffers()
                acquireWakeLock(FIXED_DEBOUNCE_DOWN_MS + 1000L)
                handler.postDelayed(debounceRunnable, FIXED_DEBOUNCE_DOWN_MS)
                return@Runnable
            } else {
                Log.i(TAG, "Debounce cancelled: phone tilted or lifted during countdown (Z=$currentZValue, H=$hMag)")
                pendingTargetState = TargetFlipState.NONE
                releaseWakeLock()
                setFastSensorRate(false)
                resetBuffers()
            }
        } else if (pendingTargetState == TargetFlipState.UP && currentlyFlipped) {
            val isPoseExited = (currentZValue > FACE_DOWN_EXIT_Z_THRESHOLD) || (hMag > EXIT_HORIZONTAL_GRAVITY)
            val isProximityLifted = hasOpticalProximity && hasProximitySample && !isProximityNear && (currentDeltaA > 0.15f)
            val isExitCondition = isPoseExited || isProximityLifted

            if (isExitCondition) {
                Log.i(TAG, "Debounce confirmed -> exiting Face Down mode (DND OFF), Z=$currentZValue, H=$hMag, proxNear=$isProximityNear")
                _isFlippedDown.value = false
                disableDoNotDisturb()
            }
        }
        pendingTargetState = TargetFlipState.NONE
        releaseWakeLock()
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    private val dndReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED) {
                val hasPerm = notifManager.isNotificationPolicyAccessGranted
                if (!hasPerm) {
                    Log.w(TAG, "Notification policy access revoked externally; stopping service")
                    _isDndActive.value = false
                    _isFlippedDown.value = false
                    wasDndActivatedByService = false
                    persistState(active = false)
                    updateNotification(active = false)
                    // The service cannot do its job without policy access. Stop now
                    // instead of leaving sensors and the foreground notification alive
                    // until the activity happens to resume.
                    stopSelf()
                    return
                }
                val currentFilter = notifManager.currentInterruptionFilter
                if (currentFilter == NotificationManager.INTERRUPTION_FILTER_ALL && _isDndActive.value) {
                    Log.i(TAG, "DND turned off externally; resetting internal DND state and notification")
                    _isDndActive.value = false
                    wasDndActivatedByService = false
                    persistState(active = false)
                    updateNotification(active = false)
                }
            }
        }
    }

    private val languagePrefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == PrefsKeys.KEY_LANGUAGE_MODE) {
            createNotificationChannel()
            updateNotification(active = _isDndActive.value)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val langMode = if (::prefs.isInitialized) prefs.getInt(PrefsKeys.KEY_LANGUAGE_MODE, 0) else 0
        if (langMode == 0) {
            createNotificationChannel()
            updateNotification(active = _isDndActive.value)
        }
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
        vibrator = (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            ?: @Suppress("DEPRECATION") (getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
        prefs = getSharedPreferences(PrefsKeys.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(languagePrefListener)

        val dndFilter = IntentFilter(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        ContextCompat.registerReceiver(this, dndReceiver, dndFilter, ContextCompat.RECEIVER_NOT_EXPORTED)

        // Clean up legacy locale-suffixed channels from v1.0.2 if present
        for (i in 0..3) {
            try {
                notifManager.deleteNotificationChannel("${CHANNEL_ID}_lang_$i")
            } catch (_: Exception) {}
        }

        gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY, true)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, true)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE, true)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY, true)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
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
                name.contains("ultrasound") || name.contains("elliptic") ||
                name.contains("gesture") || vendor.contains("elliptic")
        return !isVirtual
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.i(TAG, "onStartCommand")
        // specialUse was introduced in API 34; passing that bit to startForeground on
        // API 33 would fail the manifest type check, so keep the runtime guard.
        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        startForeground(NOTIFICATION_ID, buildNotification(active = _isDndActive.value), fgsType)

        // A debounce runnable stranded by a previous session would reference a stale
        // pendingTargetState and permanently block future flip detection.
        pendingTargetState = TargetFlipState.NONE
        releaseWakeLock()
        hasOrientationSample = false
        hasAccelSample = false
        hasProximitySample = false
        handler.removeCallbacks(debounceRunnable)
        usingFastSensorRate = false
        resetBuffers()
        registerSensors()
        _isRunning.value = true
        return START_STICKY
    }

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(languagePrefListener)
        try {
            unregisterReceiver(dndReceiver)
        } catch (_: Exception) {}
        unregisterAllSensors()
        restoreDndIfNeeded()
        pendingTargetState = TargetFlipState.NONE
        releaseWakeLock()
        wakeLock = null
        hasOrientationSample = false
        hasAccelSample = false
        hasProximitySample = false
        resetBuffers()
        _isRunning.value = false
        _isFlippedDown.value = false
        _isDndActive.value = false
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // ── Sensor Listeners ───────────────────────────────────────────────────

    private fun registerSensors() {
        activeOrientationSensor?.let { sensorManager.unregisterListener(this, it) }
        accelerometerSensor?.let { sensorManager.unregisterListener(this, it) }
        gyroscopeSensor?.let { sensorManager.unregisterListener(this, it) }

        val motionDelay = if (usingFastSensorRate) SensorManager.SENSOR_DELAY_GAME else SensorManager.SENSOR_DELAY_UI

        activeOrientationSensor?.let { sensor ->
            val registered = sensorManager.registerListener(
                this,
                sensor,
                motionDelay
            )
            Log.i(TAG, "Registered orientation sensor ${sensor.name}, delay=$motionDelay, success=$registered")
        }

        if (!isProximityRegistered) {
            proximitySensor?.let { prox ->
                val registered = sensorManager.registerListener(
                    this,
                    prox,
                    SensorManager.SENSOR_DELAY_NORMAL
                )
                if (registered) isProximityRegistered = true
                Log.i(TAG, "Registered proximity sensor ${prox.name}, success=$registered")
            }
        }

        if (usingFastSensorRate) {
            if (accelerometerSensor != null && accelerometerSensor?.type != activeOrientationSensor?.type) {
                sensorManager.registerListener(this, accelerometerSensor, motionDelay)
            }
            gyroscopeSensor?.let { gyro ->
                val registered = sensorManager.registerListener(
                    this,
                    gyro,
                    motionDelay
                )
                Log.i(TAG, "Registered gyroscope sensor ${gyro.name}, delay=$motionDelay, success=$registered")
            }
        } else {
            currentGyroRotation = 0f
            currentDeltaA = 0f
            resetBuffers()
        }
    }

    private fun unregisterAllSensors() {
        sensorManager.unregisterListener(this)
        isProximityRegistered = false
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
                hasProximitySample = true
                isProximityNear = if (maxRange <= 4.0f) distance < maxRange else distance <= 4.0f
                checkFlipState()
                return
            }
            Sensor.TYPE_GYROSCOPE -> {
                val gx = event.values[0]
                val gy = event.values[1]
                val gz = event.values[2]
                currentGyroRotation = sqrt(gx * gx + gy * gy + gz * gz)
                addGyroSample(gx, gy, gz)

                // High-precision hand tremor filter:
                // During flip-down countdown, any rotation above table stillness resets the timer.
                if (pendingTargetState == TargetFlipState.DOWN) {
                    if (currentGyroRotation > MAX_GYRO_TABLE_STILLNESS) {
                        faceDownStartTime = SystemClock.elapsedRealtime()
                    }
                }
                return
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val ax = event.values[0]
                val ay = event.values[1]
                val az = event.values[2]
                addAccelSample(ax, ay, az)

                if (!hasAccelSample) {
                    hasAccelSample = true
                    prevAccelX = ax
                    prevAccelY = ay
                    prevAccelZ = az
                    currentDeltaA = 0f
                } else {
                    val dax = ax - prevAccelX
                    val day = ay - prevAccelY
                    val daz = az - prevAccelZ
                    currentDeltaA = sqrt(dax * dax + day * day + daz * daz)
                    prevAccelX = ax
                    prevAccelY = ay
                    prevAccelZ = az

                    if (pendingTargetState == TargetFlipState.DOWN) {
                        if (currentDeltaA > MAX_DELTA_A_TABLE_STILLNESS) {
                            faceDownStartTime = SystemClock.elapsedRealtime()
                        }
                    }
                }

                if (activeOrientationSensor?.type == Sensor.TYPE_ACCELEROMETER) {
                    updateOrientation(ax, ay, az)
                }
                return
            }
            Sensor.TYPE_GRAVITY -> {
                val gx = event.values[0]
                val gy = event.values[1]
                val gz = event.values[2]
                if (activeOrientationSensor?.type == Sensor.TYPE_GRAVITY) {
                    updateOrientation(gx, gy, gz)
                }
                return
            }
        }
    }

    private fun updateOrientation(x: Float, y: Float, z: Float) {
        if (!hasOrientationSample) {
            hasOrientationSample = true
            currentXValue = x
            currentYValue = y
            currentZValue = z
            checkFlipState()
            return
        }

        currentXValue = x
        currentYValue = y
        currentZValue = z
        checkFlipState()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // ── Pixel-Style Flip Detection Logic ───────────────────────────────────

    private fun checkFlipState() {
        if (!hasOrientationSample) return

        val hMag = sqrt(currentXValue * currentXValue + currentYValue * currentYValue)

        // Strict Pixel-grade face-down flatness check:
        // 1. Z <= -9.0 m/s^2 (screen pointing down, normal gravity is ~9.8 m/s^2)
        // 2. Horizontal component sqrt(X^2+Y^2) <= 2.5 m/s^2 (tilt angle <= 15° - 20°)
        // 3. Proximity sensor fusion: verifies screen is facing a physical surface (desk/table)
        val isOrientationFlatDown = (currentZValue <= FACE_DOWN_ENTER_Z_THRESHOLD) &&
                (hMag <= MAX_HORIZONTAL_GRAVITY)
        val isFlatFaceDownOrientation = isOrientationFlatDown && isProximityPermitting()

        // Exit condition: picked up, tilted beyond threshold, or lifted with physical motion
        val isPoseExited = (currentZValue > FACE_DOWN_EXIT_Z_THRESHOLD) ||
                (hMag > EXIT_HORIZONTAL_GRAVITY)
        val isProximityLifted = hasOpticalProximity && hasProximitySample &&
                !isProximityNear && (currentDeltaA > 0.15f)
        val isExitCondition = isPoseExited || isProximityLifted

        val currentlyFlipped = _isFlippedDown.value

        if (!currentlyFlipped) {
            if (isFlatFaceDownOrientation) {
                if (pendingTargetState != TargetFlipState.DOWN) {
                    handler.removeCallbacks(debounceRunnable)
                    pendingTargetState = TargetFlipState.DOWN
                    faceDownStartTime = SystemClock.elapsedRealtime()
                    resetBuffers()
                    acquireWakeLock(FIXED_DEBOUNCE_DOWN_MS + 1000L)
                    handler.postDelayed(debounceRunnable, FIXED_DEBOUNCE_DOWN_MS)
                    setFastSensorRate(true)
                    Log.i(TAG, "Scheduled DOWN debounce timer (${FIXED_DEBOUNCE_DOWN_MS}ms from T0), Z=$currentZValue, H=$hMag, proxNear=$isProximityNear, proxSampled=$hasProximitySample")
                }
            } else {
                // Instantly cancel countdown if phone departs from flat face down
                if (pendingTargetState == TargetFlipState.DOWN) {
                    handler.removeCallbacks(debounceRunnable)
                    pendingTargetState = TargetFlipState.NONE
                    releaseWakeLock()
                    setFastSensorRate(false)
                    resetBuffers()
                    Log.i(TAG, "Cancelled pending DOWN debounce timer (phone tilted or moved), Z=$currentZValue, H=$hMag")
                }
            }
        } else {
            if (isExitCondition) {
                if (pendingTargetState != TargetFlipState.UP) {
                    handler.removeCallbacks(debounceRunnable)
                    pendingTargetState = TargetFlipState.UP
                    acquireWakeLock(DEBOUNCE_UP_MS + 1000L)
                    handler.postDelayed(debounceRunnable, DEBOUNCE_UP_MS)
                    Log.i(TAG, "Scheduled UP debounce timer (${DEBOUNCE_UP_MS}ms restore), Z=$currentZValue, H=$hMag")
                }
            } else if (isFlatFaceDownOrientation) {
                if (pendingTargetState == TargetFlipState.UP) {
                    handler.removeCallbacks(debounceRunnable)
                    pendingTargetState = TargetFlipState.NONE
                    releaseWakeLock()
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

            // 3. Perform lock screen after haptic has been executed, checking screen interactivity
            val autoLockPref = prefs.getBoolean(PrefsKeys.KEY_AUTO_LOCK_SCREEN, true)
            val accessibilityEnabled = FlipLockAccessibilityService.isAccessibilityServiceEnabled(this)

            if (autoLockPref && accessibilityEnabled && powerManager.isInteractive) {
                // Post lock screen with 150ms delay to let the ~133ms dual-pulse haptic waveform finish
                handler.postDelayed({
                    if (_isFlippedDown.value && powerManager.isInteractive) {
                        val locked = FlipLockAccessibilityService.performLock()
                        Log.i(TAG, "Auto lock screen performed via AccessibilityService, success=$locked")
                    }
                }, 150L)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling DND", e)
        }
    }

    private fun disableDoNotDisturb() {
        try {
            if (_isDndActive.value) {
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

                wasDndActivatedByService = false
                persistState(active = false)
                _isDndActive.value = false
                updateNotification(active = false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling DND", e)
        }
        // Physical flip-up feedback must still fire when DND was turned off
        // externally while the phone was face down; the old early return ate it.
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

        // Double pulse: Solid & Distinct "咚 - 咚"
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

        // Subtle single click feedback on flip-up
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
        val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_MIN).apply {
            description = desc
            setShowBadge(false)
        }
        notifManager.createNotificationChannel(channel)
        currentChannelId = CHANNEL_ID
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

        // Dynamic micro-motion thresholds during countdown (instantaneous jerk/rotation):
        private const val MAX_DELTA_A_TABLE_STILLNESS = 0.25f
        private const val MAX_GYRO_TABLE_STILLNESS = 0.12f

        // Rigorous statistical window variance thresholds (evaluated over circular buffer at 2.0s):
        // Rigid table: Variance(a) < 0.0003 m^2/s^4, Gyro Energy < 0.00005 rad^2/s^2.
        // Handheld in mid-air: Variance(a) >= 0.0030 m^2/s^4, Gyro Energy >= 0.0005 rad^2/s^2.
        private const val MAX_TABLE_ACCEL_VARIANCE = 0.0015f
        private const val MAX_TABLE_GYRO_ENERGY = 0.00030f

        private const val VARIANCE_WINDOW_SIZE = 50 // 50 samples at 50Hz = ~1.0s window

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _isFlippedDown = MutableStateFlow(false)
        val isFlippedDown: StateFlow<Boolean> = _isFlippedDown.asStateFlow()

        private val _isDndActive = MutableStateFlow(false)
        val isDndActive: StateFlow<Boolean> = _isDndActive.asStateFlow()
    }
}
