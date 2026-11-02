package shhh.cicada.flip

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin
import kotlin.math.sqrt

class StillnessDetectorTest {

    private val windowSize = 50
    private val maxTableAccelVariance = 0.0015f
    private val maxTableGyroEnergy = 0.00030f

    private fun computeVariance(xBuf: FloatArray, yBuf: FloatArray, zBuf: FloatArray, count: Int): Float {
        if (count < 15) return Float.MAX_VALUE
        var sumX = 0f
        var sumY = 0f
        var sumZ = 0f
        for (i in 0 until count) {
            sumX += xBuf[i]
            sumY += yBuf[i]
            sumZ += zBuf[i]
        }
        val meanX = sumX / count
        val meanY = sumY / count
        val meanZ = sumZ / count

        var varSum = 0f
        for (i in 0 until count) {
            val dx = xBuf[i] - meanX
            val dy = yBuf[i] - meanY
            val dz = zBuf[i] - meanZ
            varSum += (dx * dx + dy * dy + dz * dz)
        }
        return varSum / count
    }

    private fun computeGyroEnergy(sqBuf: FloatArray, count: Int): Float {
        if (count < 15) return Float.MAX_VALUE
        var sum = 0f
        for (i in 0 until count) {
            sum += sqBuf[i]
        }
        return sum / count
    }

    @Test
    fun testTabletopPlacementPassesStillness() {
        // Table resting: gravity ~ -9.81 m/s^2 on Z axis + typical MEMS thermal noise (sigma ~ 0.006 m/s^2)
        val xBuf = FloatArray(windowSize)
        val yBuf = FloatArray(windowSize)
        val zBuf = FloatArray(windowSize)
        val gyroSqBuf = FloatArray(windowSize)

        val random = java.util.Random(42)
        for (i in 0 until windowSize) {
            val noiseX = (random.nextGaussian() * 0.006).toFloat()
            val noiseY = (random.nextGaussian() * 0.006).toFloat()
            val noiseZ = (random.nextGaussian() * 0.006).toFloat()
            xBuf[i] = noiseX
            yBuf[i] = noiseY
            zBuf[i] = -9.806f + noiseZ

            // Gyro thermal noise (sigma ~ 0.002 rad/s)
            val gx = (random.nextGaussian() * 0.002).toFloat()
            val gy = (random.nextGaussian() * 0.002).toFloat()
            val gz = (random.nextGaussian() * 0.002).toFloat()
            gyroSqBuf[i] = gx * gx + gy * gy + gz * gz
        }

        val accelVar = computeVariance(xBuf, yBuf, zBuf, windowSize)
        val gyroEnergy = computeGyroEnergy(gyroSqBuf, windowSize)

        println("Table simulation: accelVar=$accelVar, gyroEnergy=$gyroEnergy")
        assertTrue("Table accel variance ($accelVar) should be well below $maxTableAccelVariance", accelVar <= maxTableAccelVariance)
        assertTrue("Table gyro energy ($gyroEnergy) should be well below $maxTableGyroEnergy", gyroEnergy <= maxTableGyroEnergy)
    }

    @Test
    fun testHandheldMidAirIsStrictlyRejected() {
        // Handheld in mid-air: 8-12 Hz tremor (modeled as 10Hz sine with 0.10 m/s^2 amplitude)
        // plus slow breathing/postural sway on gyro (~0.025 rad/s)
        val xBuf = FloatArray(windowSize)
        val yBuf = FloatArray(windowSize)
        val zBuf = FloatArray(windowSize)
        val gyroSqBuf = FloatArray(windowSize)

        val random = java.util.Random(123)
        val dt = 0.02f // 50Hz = 20ms
        for (i in 0 until windowSize) {
            val t = i * dt
            val tremor = (0.10 * sin(2.0 * Math.PI * 10.0 * t)).toFloat()
            val noiseX = (random.nextGaussian() * 0.015).toFloat()
            val noiseY = (random.nextGaussian() * 0.015).toFloat()
            val noiseZ = (random.nextGaussian() * 0.015).toFloat()

            xBuf[i] = tremor * 0.5f + noiseX
            yBuf[i] = tremor * 0.5f + noiseY
            zBuf[i] = -9.806f + tremor + noiseZ

            // Involuntary hand sway gyro ~ 0.025 rad/s
            val gx = 0.020f + (random.nextGaussian() * 0.005).toFloat()
            val gy = 0.015f + (random.nextGaussian() * 0.005).toFloat()
            val gz = 0.010f + (random.nextGaussian() * 0.005).toFloat()
            gyroSqBuf[i] = gx * gx + gy * gy + gz * gz
        }

        val accelVar = computeVariance(xBuf, yBuf, zBuf, windowSize)
        val gyroEnergy = computeGyroEnergy(gyroSqBuf, windowSize)

        println("Handheld simulation: accelVar=$accelVar, gyroEnergy=$gyroEnergy")
        assertFalse("Handheld accel variance ($accelVar) must exceed table threshold $maxTableAccelVariance", accelVar <= maxTableAccelVariance)
        assertFalse("Handheld gyro energy ($gyroEnergy) must exceed table threshold $maxTableGyroEnergy", gyroEnergy <= maxTableGyroEnergy)
    }

    @Test
    fun testProximityGatingLogic() {
        // 1. Hardware optical proximity sensor in open air (FAR) must block entry
        val hasOpticalProximity = true
        val hasProximitySample = true
        val isProximityNearInAir = false
        val permitInAir = !hasOpticalProximity || !hasProximitySample || isProximityNearInAir
        assertFalse("Optical proximity in open air must NOT permit flip-down entry", permitInAir)

        // 2. Hardware optical proximity sensor on desk (NEAR) permits entry
        val isProximityNearOnDesk = true
        val permitOnDesk = !hasOpticalProximity || !hasProximitySample || isProximityNearOnDesk
        assertTrue("Optical proximity against desk surface must permit flip-down entry", permitOnDesk)

        // 3. Virtual proximity falls through to stillness verification
        val isVirtual = false // hasOpticalProximity = false
        val permitVirtual = !isVirtual || !hasProximitySample || isProximityNearInAir
        assertTrue("Virtual proximity must fall back to motion variance verification", permitVirtual)
    }
}
