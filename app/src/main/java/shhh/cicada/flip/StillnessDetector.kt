package shhh.cicada.flip

import kotlin.math.max

/**
 * Framework-free stillness detector for the flip-to-DND feature.
 *
 * It keeps a short sliding window of accelerometer and (optionally) gyroscope
 * samples and decides whether the device is physically resting on a rigid
 * surface. All samples are timestamped by the caller with a monotonic clock
 * (`SystemClock.elapsedRealtime()` in the service) so the detector stays a
 * pure Kotlin class and can be unit tested without Android.
 *
 * Two details matter for robustness:
 *  - The first [Config.warmupMs] of every sampling window is discarded. This
 *    removes the sensor cold-start transient as well as the impact spike that
 *    happens when the phone is dropped onto the table.
 *  - Gyroscope stillness uses the three-axis de-meaned variance instead of raw
 *    squared magnitude. A constant bias (a very common 0.02-0.03 rad/s MEMS
 *    zero-rate offset) therefore contributes nothing and cannot reject a
 *    perfectly still device.
 */
class StillnessDetector(
    private val hasGyroscope: Boolean,
    private val config: Config = Config(),
) {

    data class Config(
        /** Number of samples kept in the sliding window. */
        val windowSize: Int = 50,
        /** Samples younger than this (relative to reset) are ignored. */
        val warmupMs: Long = 300L,
        /** Minimum samples required before a window is considered valid. */
        val minSamples: Int = 15,
        /** Max de-meaned three-axis accel variance for a tabletop. */
        val maxAccelVariance: Float = 0.0015f,
        /** Slightly tighter accel variance when no gyroscope is available. */
        val maxAccelVarianceWithoutGyro: Float = 0.0010f,
        /** Max de-meaned three-axis gyro variance for a tabletop (rad/s)^2. */
        val maxGyroVariance: Float = 0.00030f,
    )

    data class Result(
        val isStationary: Boolean,
        val accelVariance: Float,
        val gyroVariance: Float,
        val accelSampleCount: Int,
        val gyroSampleCount: Int,
        val warmedUp: Boolean,
    )

    private val accelX = FloatArray(config.windowSize)
    private val accelY = FloatArray(config.windowSize)
    private val accelZ = FloatArray(config.windowSize)
    private var accelIndex = 0
    private var accelCount = 0

    private val gyroX = FloatArray(config.windowSize)
    private val gyroY = FloatArray(config.windowSize)
    private val gyroZ = FloatArray(config.windowSize)
    private var gyroIndex = 0
    private var gyroCount = 0

    private var warmupStartMs = -1L
    private var lastSampleMs = -1L

    /**
     * Starts a new sampling window at [startTimestampMs]. Samples before
     * `startTimestampMs + warmupMs` are dropped.
     */
    fun reset(startTimestampMs: Long) {
        accelIndex = 0
        accelCount = 0
        gyroIndex = 0
        gyroCount = 0
        warmupStartMs = startTimestampMs
        lastSampleMs = -1L
    }

    /** Resets without a known start time; the first sample becomes the origin. */
    fun reset() = reset(-1L)

    fun addAccelerometer(x: Float, y: Float, z: Float, timestampMs: Long) {
        if (!acceptTimestamp(timestampMs)) return
        accelX[accelIndex] = x
        accelY[accelIndex] = y
        accelZ[accelIndex] = z
        accelIndex = (accelIndex + 1) % config.windowSize
        if (accelCount < config.windowSize) accelCount++
    }

    fun addGyroscope(x: Float, y: Float, z: Float, timestampMs: Long) {
        if (!hasGyroscope) return
        if (!acceptTimestamp(timestampMs)) return
        gyroX[gyroIndex] = x
        gyroY[gyroIndex] = y
        gyroZ[gyroIndex] = z
        gyroIndex = (gyroIndex + 1) % config.windowSize
        if (gyroCount < config.windowSize) gyroCount++
    }

    private fun acceptTimestamp(timestampMs: Long): Boolean {
        if (timestampMs < 0L) return false
        if (warmupStartMs < 0L) warmupStartMs = timestampMs
        if (timestampMs < warmupStartMs) return false
        if (timestampMs - warmupStartMs < config.warmupMs) return false
        // Ignore out-of-order deliveries instead of poisoning the window.
        if (lastSampleMs >= 0L && timestampMs < lastSampleMs) return false
        lastSampleMs = max(lastSampleMs, timestampMs)
        return true
    }

    /**
     * Returns the stillness verdict for the samples currently in the window.
     * The caller should only treat it as final once the countdown deadline has
     * elapsed (the service checks it from its debounce runnable).
     */
    fun result(): Result {
        val warmedUp = warmupStartMs >= 0L &&
                lastSampleMs >= 0L &&
                lastSampleMs - warmupStartMs >= config.warmupMs

        val accelVariance = computeVariance(accelX, accelY, accelZ, accelCount)
        val gyroVariance = if (hasGyroscope) {
            computeVariance(gyroX, gyroY, gyroZ, gyroCount)
        } else {
            0f
        }

        val accelThreshold = if (hasGyroscope) {
            config.maxAccelVariance
        } else {
            config.maxAccelVarianceWithoutGyro
        }
        val accelStill = accelCount >= config.minSamples && accelVariance <= accelThreshold
        val gyroStill = !hasGyroscope ||
                (gyroCount >= config.minSamples && gyroVariance <= config.maxGyroVariance)

        return Result(
            isStationary = warmedUp && accelStill && gyroStill,
            accelVariance = accelVariance,
            gyroVariance = gyroVariance,
            accelSampleCount = accelCount,
            gyroSampleCount = gyroCount,
            warmedUp = warmedUp,
        )
    }

    private fun computeVariance(xs: FloatArray, ys: FloatArray, zs: FloatArray, count: Int): Float {
        if (count < config.minSamples) return Float.MAX_VALUE
        var sumX = 0f
        var sumY = 0f
        var sumZ = 0f
        for (i in 0 until count) {
            sumX += xs[i]
            sumY += ys[i]
            sumZ += zs[i]
        }
        val meanX = sumX / count
        val meanY = sumY / count
        val meanZ = sumZ / count

        var variance = 0f
        for (i in 0 until count) {
            val dx = xs[i] - meanX
            val dy = ys[i] - meanY
            val dz = zs[i] - meanZ
            variance += dx * dx + dy * dy + dz * dz
        }
        return variance / count
    }
}
