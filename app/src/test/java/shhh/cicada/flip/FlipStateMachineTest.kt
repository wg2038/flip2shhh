package shhh.cicada.flip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the framework-free [FlipStateMachine]. Debounce durations are
 * shortened so the tests stay fast; thresholds and transition rules are the
 * production ones.
 */
class FlipStateMachineTest {

    private val fastConfig = FlipStateMachine.Config(
        downDebounceMs = 100L,
        downDebounceNoGyroMs = 150L,
        upDebounceMs = 30L,
        maxStillnessRetries = 3,
        proximityFirstFrameTimeoutMs = 20L,
    )

    private fun stationary() = StillnessDetector.Result(
        isStationary = true,
        accelVariance = 1.0e-4f,
        gyroVariance = 1.0e-5f,
        accelSampleCount = 50,
        gyroSampleCount = 50,
        warmedUp = true,
    )

    private fun unstable() = StillnessDetector.Result(
        isStationary = false,
        accelVariance = 0.02f,
        gyroVariance = 0.002f,
        accelSampleCount = 50,
        gyroSampleCount = 50,
        warmedUp = true,
    )

    private fun observation(
        timestampMs: Long,
        gravityZ: Float = -9.8f,
        horizontalGravity: Float = 0.5f,
        proximity: FlipStateMachine.Proximity = FlipStateMachine.Proximity.NoOpticalSensor,
    ) = FlipStateMachine.Observation(
        timestampMs = timestampMs,
        hasOrientationSample = true,
        gravityZ = gravityZ,
        horizontalGravity = horizontalGravity,
        proximity = proximity,
    )

    @Test
    fun unstableSurfaceCoolsDownAfterThreeFailedStillnessChecks() {
        val machine = FlipStateMachine(
            hasGyroscope = true,
            hasOpticalProximity = false,
            config = fastConfig,
        )

        var decision = machine.onObservation(observation(0L))
        assertEquals(FlipStateMachine.Phase.DOWN_PENDING, decision.phase)
        assertTrue(decision.useFastSensors)
        assertEquals(100L, decision.scheduleCheckAtMs)

        // Attempt 1 and 2 restart the window and keep high-rate sampling.
        decision = machine.onTimer(100L, unstable())
        assertEquals(FlipStateMachine.Phase.DOWN_PENDING, decision.phase)
        assertEquals(1, decision.retryCount)
        assertTrue(decision.useFastSensors)
        assertTrue(decision.resetStillness)

        decision = machine.onTimer(200L, unstable())
        assertEquals(FlipStateMachine.Phase.DOWN_PENDING, decision.phase)
        assertEquals(2, decision.retryCount)
        assertTrue(decision.useFastSensors)

        // Attempt 3 trips the circuit breaker.
        decision = machine.onTimer(300L, unstable())
        assertEquals(FlipStateMachine.Phase.COOLDOWN, decision.phase)
        assertEquals(3, decision.retryCount)
        assertFalse(decision.useFastSensors)
        assertTrue(decision.releaseWakeLock)
        assertTrue(decision.cancelScheduledCheck)

        // Still face down: stay in COOLDOWN, do not sample fast or retrigger.
        decision = machine.onObservation(observation(400L))
        assertEquals(FlipStateMachine.Phase.COOLDOWN, decision.phase)
        assertNull(decision.scheduleCheckAtMs)
        assertFalse(decision.useFastSensors)

        // Lift the phone: cooldown is cleared only now.
        decision = machine.onObservation(observation(500L, gravityZ = -4.0f, horizontalGravity = 3.0f))
        assertEquals(FlipStateMachine.Phase.IDLE, decision.phase)

        // Put it back down on a stable surface: detection works again.
        decision = machine.onObservation(observation(600L))
        assertEquals(FlipStateMachine.Phase.DOWN_PENDING, decision.phase)
        assertEquals(700L, decision.scheduleCheckAtMs)

        decision = machine.onTimer(700L, stationary())
        assertEquals(FlipStateMachine.Phase.FLIPPED, decision.phase)
        assertTrue(decision.enterDnd)
    }

    @Test
    fun externalDndOffDoesNotBounceBackUntilPhoneIsLifted() {
        val machine = FlipStateMachine(
            hasGyroscope = true,
            hasOpticalProximity = false,
            config = fastConfig,
        )

        // Establish a normal flipped-down state.
        machine.onObservation(observation(0L))
        var decision = machine.onTimer(100L, stationary())
        assertEquals(FlipStateMachine.Phase.FLIPPED, decision.phase)
        assertTrue(decision.enterDnd)

        // User/system turns DND off while the phone remains face down.
        decision = machine.onExternalDndDisabled(150L)
        assertEquals(FlipStateMachine.Phase.IDLE, decision.phase)
        assertTrue(machine.suppressedUntilLifted)
        assertFalse(decision.enterDnd)

        // The phone is still face down: repeated observations must not re-arm
        // the countdown or turn DND back on.
        var t = 160L
        repeat(10) {
            decision = machine.onObservation(observation(t))
            assertEquals(FlipStateMachine.Phase.IDLE, decision.phase)
            assertFalse(decision.enterDnd)
            assertFalse(decision.useFastSensors)
            assertNull(decision.scheduleCheckAtMs)
            t += 20L
        }
        assertTrue(machine.suppressedUntilLifted)

        // Lift it: the latch clears.
        decision = machine.onObservation(observation(t, gravityZ = -3.0f, horizontalGravity = 2.0f))
        assertEquals(FlipStateMachine.Phase.IDLE, decision.phase)
        assertFalse(machine.suppressedUntilLifted)

        // Place it face down again: a fresh countdown is allowed.
        decision = machine.onObservation(observation(t + 20L))
        assertEquals(FlipStateMachine.Phase.DOWN_PENDING, decision.phase)
        decision = machine.onTimer(t + 120L, stationary())
        assertEquals(FlipStateMachine.Phase.FLIPPED, decision.phase)
        assertTrue(decision.enterDnd)
    }

    @Test
    fun noGyroscopeUsesLongerStillnessWindow() {
        val machine = FlipStateMachine(
            hasGyroscope = false,
            hasOpticalProximity = false,
            config = fastConfig,
        )

        val decision = machine.onObservation(observation(1_000L))
        assertEquals(FlipStateMachine.Phase.DOWN_PENDING, decision.phase)
        // downDebounceNoGyroMs = 150 instead of 100.
        assertEquals(1_150L, decision.scheduleCheckAtMs)

        val confirmed = machine.onTimer(1_150L, stationary())
        assertEquals(FlipStateMachine.Phase.FLIPPED, confirmed.phase)
        assertTrue(confirmed.enterDnd)
    }

    @Test
    fun opticalProximityFirstFrameRaceWaitsForSampleOrTimeout() {
        val machine = FlipStateMachine(
            hasGyroscope = true,
            hasOpticalProximity = true,
            config = fastConfig,
        )

        var decision = machine.onObservation(
            observation(0L, proximity = FlipStateMachine.Proximity.AwaitingFirstSample)
        )
        assertEquals(FlipStateMachine.Phase.IDLE, decision.phase)
        assertTrue(decision.blockedByProximity)

        // Still no sample, but the 20 ms fallback has not elapsed yet.
        decision = machine.onObservation(
            observation(19L, proximity = FlipStateMachine.Proximity.AwaitingFirstSample)
        )
        assertEquals(FlipStateMachine.Phase.IDLE, decision.phase)

        // Timeout: fall back to the stillness engine instead of hanging.
        decision = machine.onObservation(
            observation(20L, proximity = FlipStateMachine.Proximity.AwaitingFirstSample)
        )
        assertEquals(FlipStateMachine.Phase.DOWN_PENDING, decision.phase)

        // Still no HAL callback: the already-approved fallback must not cancel
        // and restart the countdown on every subsequent sample.
        decision = machine.onObservation(
            observation(40L, proximity = FlipStateMachine.Proximity.AwaitingFirstSample)
        )
        assertEquals(FlipStateMachine.Phase.DOWN_PENDING, decision.phase)
        assertNull(decision.scheduleCheckAtMs)
        assertFalse(decision.cancelScheduledCheck)

        // FAR (mid-air) always blocks entry.
        machine.reset()
        decision = machine.onObservation(
            observation(100L, proximity = FlipStateMachine.Proximity.Far)
        )
        assertEquals(FlipStateMachine.Phase.IDLE, decision.phase)
        assertTrue(decision.blockedByProximity)

        // NEAR (against the desk) permits entry immediately.
        decision = machine.onObservation(
            observation(120L, proximity = FlipStateMachine.Proximity.Near)
        )
        assertEquals(FlipStateMachine.Phase.DOWN_PENDING, decision.phase)
    }

    @Test
    fun nudgeDuringUpDebounceCancelsRestore() {
        val machine = FlipStateMachine(
            hasGyroscope = true,
            hasOpticalProximity = false,
            config = fastConfig,
        )

        machine.onObservation(observation(0L))
        machine.onTimer(100L, stationary())

        // Lift slightly -> UP countdown.
        var decision = machine.onObservation(observation(120L, gravityZ = -7.0f, horizontalGravity = 1.0f))
        assertEquals(FlipStateMachine.Phase.FLIPPED, decision.phase)
        assertEquals(FlipStateMachine.Target.UP, decision.pendingTarget)
        assertEquals(150L, decision.scheduleCheckAtMs)

        // Nudge back flat before the debounce expires -> cancel restore.
        decision = machine.onObservation(observation(140L))
        assertEquals(FlipStateMachine.Phase.FLIPPED, decision.phase)
        assertEquals(FlipStateMachine.Target.NONE, decision.pendingTarget)
        assertTrue(decision.cancelScheduledCheck)

        decision = machine.onTimer(150L, null)
        assertEquals(FlipStateMachine.Phase.FLIPPED, decision.phase)
        assertFalse(decision.exitDnd)
    }
}
