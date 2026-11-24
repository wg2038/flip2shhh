package shhh.cicada.flip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.sin

/**
 * Unit tests for the framework-free [StillnessDetector]. All buffers and
 * variance math live in production code; these tests only feed synthetic
 * sensor streams through the public API.
 */
class StillnessDetectorTest {

    private val period50Hz = 20L
    private val defaultSamples = 150

    private fun gauss(random: Random, sigma: Double): Float =
        (random.nextGaussian() * sigma).toFloat()

    private fun feedTable(
        detector: StillnessDetector,
        samples: Int = defaultSamples,
        startMs: Long = 0L,
        periodMs: Long = period50Hz,
        accelNoise: Double = 0.006,
        gyroBias: Float = 0f,
        gyroNoise: Double = 0.002,
        seed: Long = 42L,
    ) {
        val random = Random(seed)
        detector.reset(startMs)
        for (i in 0 until samples) {
            val t = startMs + i * periodMs
            detector.addAccelerometer(
                gauss(random, accelNoise),
                gauss(random, accelNoise),
                -9.806f + gauss(random, accelNoise),
                t,
            )
            detector.addGyroscope(
                gyroBias + gauss(random, gyroNoise),
                gyroBias + gauss(random, gyroNoise),
                gyroBias + gauss(random, gyroNoise),
                t,
            )
        }
    }

    @Test
    fun rigidTabletopPassesStillness() {
        val detector = StillnessDetector(hasGyroscope = true)
        feedTable(detector)

        val result = detector.result()
        println("Table: $result")

        assertTrue("tabletop window must be warmed up", result.warmedUp)
        assertTrue("tabletop accel variance ${result.accelVariance} too high", result.accelVariance <= 0.0015f)
        assertTrue("tabletop gyro variance ${result.gyroVariance} too high", result.gyroVariance <= 0.00030f)
        assertTrue("rigid tabletop must be stationary", result.isStationary)
    }

    @Test
    fun handheldMidAirTremorIsRejected() {
        val detector = StillnessDetector(hasGyroscope = true)
        detector.reset(0L)
        val random = Random(123)

        for (i in 0 until defaultSamples) {
            val t = i * period50Hz
            val seconds = t / 1000.0
            val tremor = (0.10 * sin(2.0 * PI * 10.0 * seconds)).toFloat()

            detector.addAccelerometer(
                tremor * 0.5f + gauss(random, 0.015),
                tremor * 0.5f + gauss(random, 0.015),
                -9.806f + tremor + gauss(random, 0.015),
                t,
            )
            // Physiological sway is time-varying, not a constant bias.
            detector.addGyroscope(
                (0.030 * sin(2.0 * PI * 1.5 * seconds)).toFloat() + gauss(random, 0.005),
                (0.020 * sin(2.0 * PI * 1.2 * seconds + 1.0)).toFloat() + gauss(random, 0.005),
                (0.015 * sin(2.0 * PI * 2.0 * seconds + 2.0)).toFloat() + gauss(random, 0.005),
                t,
            )
        }

        val result = detector.result()
        println("Handheld: $result")

        assertTrue("hand tremor must exceed the accel threshold", result.accelVariance > 0.0015f)
        assertTrue("hand sway must exceed the gyro threshold", result.gyroVariance > 0.00030f)
        assertFalse("handheld mid-air pose must be rejected", result.isStationary)
    }

    @Test
    fun constantGyroZeroRateBiasStillPassesOnTable() {
        // A 0.03 rad/s zero-rate offset is far larger than the stillness
        // threshold if it is treated as energy. De-meaning must cancel it.
        val detector = StillnessDetector(hasGyroscope = true)
        feedTable(detector, gyroBias = 0.03f, gyroNoise = 0.002)

        val result = detector.result()
        println("Biased gyro: $result")

        assertTrue("bias must be removed by de-meaned variance", result.gyroVariance <= 0.00030f)
        assertTrue(result.isStationary)
    }

    @Test
    fun lowSampleRateWithLandingImpactInWarmupPasses() {
        // 25 Hz means only ~50 samples fit in a 2 s window. Drop the first
        // 300 ms (including the landing spike) or the buffer never recovers.
        val detector = StillnessDetector(hasGyroscope = true)
        val random = Random(7)
        detector.reset(0L)

        for (i in 0 until 80) {
            val t = i * 40L
            val impact = t < 300L
            val accelSigma = if (impact) 2.5 else 0.004
            val gyroSigma = if (impact) 1.5 else 0.002
            detector.addAccelerometer(
                gauss(random, accelSigma),
                gauss(random, accelSigma),
                -9.806f + gauss(random, accelSigma),
                t,
            )
            detector.addGyroscope(
                gauss(random, gyroSigma),
                gauss(random, gyroSigma),
                gauss(random, gyroSigma),
                t,
            )
        }

        val result = detector.result()
        println("25Hz + impact: $result")

        assertTrue("window should be warmed up after the 300 ms drop", result.warmedUp)
        assertTrue("warm-up must discard landing impact spikes", result.isStationary)
    }

    @Test
    fun deviceWithoutGyroscopeFallsBackToAccelOnly() {
        val detector = StillnessDetector(hasGyroscope = false)
        feedTable(detector, accelNoise = 0.004)

        val result = detector.result()
        println("No gyro table: $result")

        assertTrue("accel-only tabletop must pass", result.isStationary)
        assertEquals("no gyro samples should be required", 0, result.gyroSampleCount)
    }

    @Test
    fun deviceWithoutGyroscopeStillRejectsHandheldShake() {
        val detector = StillnessDetector(hasGyroscope = false)
        detector.reset(0L)
        val random = Random(99)

        for (i in 0 until defaultSamples) {
            val t = i * period50Hz
            val seconds = t / 1000.0
            detector.addAccelerometer(
                (0.60 * sin(2.0 * PI * 3.0 * seconds)).toFloat() + gauss(random, 0.01),
                (0.50 * sin(2.0 * PI * 2.5 * seconds + 0.7)).toFloat() + gauss(random, 0.01),
                -9.806f + (0.40 * sin(2.0 * PI * 4.0 * seconds + 1.3)).toFloat() + gauss(random, 0.01),
                t,
            )
        }

        val result = detector.result()
        println("No gyro shake: $result")

        assertTrue("accel-only shake must exceed the tighter threshold", result.accelVariance > 0.0010f)
        assertFalse("handheld shake must be rejected without a gyroscope", result.isStationary)
    }
}
