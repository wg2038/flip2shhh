package shhh.cicada.flip

/**
 * Framework-free flip detection state machine.
 *
 * Sensor callbacks, debounce timers, proximity observation and external DND
 * changes are all funnelled into [onObservation], [onTimer],
 * [onMotionDisturbance] and [onExternalDndDisabled]. Each method returns a
 * [Decision] describing the side effects the Android service must apply
 * (sensor rate, wake lock, next timer, DND transitions). Keeping the decision
 * logic pure makes the tricky cases - low-power sensors, retry circuit
 * breaking and manual DND overrides - directly unit testable.
 */
class FlipStateMachine(
    private val hasGyroscope: Boolean,
    private val hasOpticalProximity: Boolean,
    private val config: Config = Config(),
) {

    data class Config(
        val downDebounceMs: Long = 2_000L,
        /** No gyroscope: accel variance is tighter, so require a longer window. */
        val downDebounceNoGyroMs: Long = 2_500L,
        val upDebounceMs: Long = 300L,
        /** Consecutive failed stillness checks before entering COOLDOWN. */
        val maxStillnessRetries: Int = 3,
        /** Wait at most this long for the first optical proximity sample. */
        val proximityFirstFrameTimeoutMs: Long = 500L,
        val enterGravityZThreshold: Float = -9.0f,
        /** sqrt(X^2 + Y^2) <= 2.5 m/s^2, i.e. about 14.8 degrees of tilt. */
        val enterHorizontalGravityThreshold: Float = 2.5f,
        val exitGravityZThreshold: Float = -7.5f,
        val exitHorizontalGravityThreshold: Float = 3.5f,
    )

    enum class Phase {
        /** Listening for a face-down pose. */
        IDLE,
        /** Face-down countdown running; stillness is checked at the deadline. */
        DOWN_PENDING,
        /** DND owned by a confirmed flip-down. */
        FLIPPED,
        /** Unstable surface; wait for a lift before allowing another attempt. */
        COOLDOWN,
    }

    enum class Target { NONE, DOWN, UP }

    sealed interface Proximity {
        data object NoOpticalSensor : Proximity
        data object AwaitingFirstSample : Proximity
        data object Near : Proximity
        data object Far : Proximity
    }

    data class Observation(
        val timestampMs: Long,
        val hasOrientationSample: Boolean,
        val gravityZ: Float,
        val horizontalGravity: Float,
        val proximity: Proximity,
    )

    data class Decision(
        val phase: Phase,
        val pendingTarget: Target,
        val useFastSensors: Boolean,
        val enterDnd: Boolean = false,
        val exitDnd: Boolean = false,
        /** Non-null: acquire/extend a wake lock by this timeout. */
        val acquireWakeLockMs: Long? = null,
        val releaseWakeLock: Boolean = false,
        /** Non-null: schedule the debounce runnable at this elapsedRealtime. */
        val scheduleCheckAtMs: Long? = null,
        val cancelScheduledCheck: Boolean = false,
        /** Clear the stillness detector window and restart its warm-up. */
        val resetStillness: Boolean = false,
        val retryCount: Int = 0,
        val blockedByProximity: Boolean = false,
    )

    var phase: Phase = Phase.IDLE
        private set

    var pendingTarget: Target = Target.NONE
        private set

    var stillnessRetryCount: Int = 0
        private set

    /** Latched after an external DND-off until the phone is lifted. */
    var suppressedUntilLifted: Boolean = false
        private set

    private val downDebounceMs =
        if (hasGyroscope) config.downDebounceMs else config.downDebounceNoGyroMs

    private var deadlineMs = 0L
    private var proximityAwaitStartMs: Long? = null

    fun reset(startFlipped: Boolean = false) {
        phase = if (startFlipped) Phase.FLIPPED else Phase.IDLE
        pendingTarget = Target.NONE
        stillnessRetryCount = 0
        suppressedUntilLifted = false
        proximityAwaitStartMs = null
        deadlineMs = 0L
    }

    fun onObservation(observation: Observation): Decision {
        val idle = decision()
        if (!observation.hasOrientationSample) return idle

        val faceDownPose = observation.gravityZ <= config.enterGravityZThreshold &&
                observation.horizontalGravity <= config.enterHorizontalGravityThreshold
        val tiltExit = observation.gravityZ > config.exitGravityZThreshold ||
                observation.horizontalGravity > config.exitHorizontalGravityThreshold
        val proximityLifted = observation.proximity is Proximity.Far
        val exitCondition = tiltExit || proximityLifted

        return when (phase) {
            Phase.IDLE -> {
                if (!faceDownPose || exitCondition) {
                    proximityAwaitStartMs = null
                }
                if (exitCondition) {
                    // Re-arm only after the phone has actually been lifted.
                    suppressedUntilLifted = false
                    return if (faceDownPose) {
                        decision(blockedByProximity = true)
                    } else {
                        decision()
                    }
                }
                if (suppressedUntilLifted) return decision()
                if (!faceDownPose) return decision()
                if (!isProximityPermitting(observation)) {
                    return decision(blockedByProximity = true)
                }

                phase = Phase.DOWN_PENDING
                pendingTarget = Target.DOWN
                stillnessRetryCount = 0
                deadlineMs = observation.timestampMs + downDebounceMs
                return decision(
                    acquireWakeLockMs = downDebounceMs + WAKE_LOCK_MARGIN_MS,
                    scheduleCheckAtMs = deadlineMs,
                    resetStillness = true,
                )
            }

            Phase.DOWN_PENDING -> {
                if (!faceDownPose || !isProximityPermitting(observation)) {
                    phase = Phase.IDLE
                    pendingTarget = Target.NONE
                    proximityAwaitStartMs = null
                    return decision(
                        releaseWakeLock = true,
                        cancelScheduledCheck = true,
                        resetStillness = true,
                    )
                }
                return decision()
            }

            Phase.FLIPPED -> {
                if (exitCondition) {
                    if (pendingTarget != Target.UP) {
                        pendingTarget = Target.UP
                        deadlineMs = observation.timestampMs + config.upDebounceMs
                        return decision(
                            acquireWakeLockMs = config.upDebounceMs + WAKE_LOCK_MARGIN_MS,
                            scheduleCheckAtMs = deadlineMs,
                        )
                    }
                } else if (faceDownPose && pendingTarget == Target.UP) {
                    // Nudged but still flat: do not restore DND.
                    pendingTarget = Target.NONE
                    return decision(
                        releaseWakeLock = true,
                        cancelScheduledCheck = true,
                    )
                }
                return decision()
            }

            Phase.COOLDOWN -> {
                if (exitCondition) {
                    phase = Phase.IDLE
                    pendingTarget = Target.NONE
                    stillnessRetryCount = 0
                    proximityAwaitStartMs = null
                    suppressedUntilLifted = false
                }
                return decision()
            }
        }
    }

    /**
     * Called from the debounce timer. [stillness] is only consulted when a
     * face-down countdown has actually elapsed.
     */
    fun onTimer(timestampMs: Long, stillness: StillnessDetector.Result?): Decision {
        when (phase) {
            Phase.DOWN_PENDING -> {
                if (timestampMs < deadlineMs) {
                    // Timer fired slightly early (clock/latency jitter): re-arm.
                    return decision(scheduleCheckAtMs = deadlineMs)
                }

                if (stillness?.isStationary == true) {
                    phase = Phase.FLIPPED
                    pendingTarget = Target.NONE
                    proximityAwaitStartMs = null
                    return decision(
                        enterDnd = true,
                        releaseWakeLock = true,
                        cancelScheduledCheck = true,
                    )
                }

                stillnessRetryCount++
                if (stillnessRetryCount >= config.maxStillnessRetries) {
                    // The surface is unstable. Stop high-rate sampling and wait
                    // for a lift instead of burning battery in a retry loop.
                    phase = Phase.COOLDOWN
                    pendingTarget = Target.NONE
                    return decision(
                        releaseWakeLock = true,
                        cancelScheduledCheck = true,
                        resetStillness = true,
                    )
                }

                deadlineMs = timestampMs + downDebounceMs
                return decision(
                    acquireWakeLockMs = downDebounceMs + WAKE_LOCK_MARGIN_MS,
                    scheduleCheckAtMs = deadlineMs,
                    resetStillness = true,
                )
            }

            Phase.FLIPPED -> {
                if (pendingTarget == Target.UP && timestampMs >= deadlineMs) {
                    phase = Phase.IDLE
                    pendingTarget = Target.NONE
                    return decision(
                        exitDnd = true,
                        releaseWakeLock = true,
                        cancelScheduledCheck = true,
                    )
                }
                return decision()
            }

            Phase.IDLE, Phase.COOLDOWN -> return decision()
        }
    }

    /**
     * High-rate gyro callback exceeded the tabletop stillness threshold.
     * Restart the countdown and clear the window so the disturbance sample
     * cannot linger in the variance computation.
     */
    fun onMotionDisturbance(timestampMs: Long): Decision? {
        if (phase != Phase.DOWN_PENDING) return null
        deadlineMs = timestampMs + downDebounceMs
        return decision(
            acquireWakeLockMs = downDebounceMs + WAKE_LOCK_MARGIN_MS,
            scheduleCheckAtMs = deadlineMs,
            resetStillness = true,
        )
    }

    /**
     * The user (or another app/system policy) turned DND off while we were
     * flipped. Latch suppression until the phone leaves the face-down pose so
     * the service does not immediately turn DND back on.
     */
    fun onExternalDndDisabled(timestampMs: Long): Decision {
        suppressedUntilLifted = true
        if (phase == Phase.FLIPPED || phase == Phase.DOWN_PENDING) {
            phase = Phase.IDLE
            pendingTarget = Target.NONE
        }
        proximityAwaitStartMs = null
        return decision(
            releaseWakeLock = true,
            cancelScheduledCheck = true,
        )
    }

    private fun isProximityPermitting(observation: Observation): Boolean {
        return when (observation.proximity) {
            Proximity.NoOpticalSensor, Proximity.Near -> true
            Proximity.Far -> false
            Proximity.AwaitingFirstSample -> {
                if (!hasOpticalProximity) return true
                // Entry was already approved in IDLE (either by the first sample
                // arriving or by this timeout); do not restart the wait while
                // the countdown is running.
                if (phase == Phase.DOWN_PENDING) return true
                val start = proximityAwaitStartMs ?: observation.timestampMs.also {
                    proximityAwaitStartMs = it
                }
                // Fall back after a short timeout if the HAL never delivers.
                observation.timestampMs - start >= config.proximityFirstFrameTimeoutMs
            }
        }
    }

    private fun decision(
        enterDnd: Boolean = false,
        exitDnd: Boolean = false,
        acquireWakeLockMs: Long? = null,
        releaseWakeLock: Boolean = false,
        scheduleCheckAtMs: Long? = null,
        cancelScheduledCheck: Boolean = false,
        resetStillness: Boolean = false,
        blockedByProximity: Boolean = false,
    ): Decision = Decision(
        phase = phase,
        pendingTarget = pendingTarget,
        useFastSensors = phase == Phase.DOWN_PENDING,
        enterDnd = enterDnd,
        exitDnd = exitDnd,
        acquireWakeLockMs = acquireWakeLockMs,
        releaseWakeLock = releaseWakeLock,
        scheduleCheckAtMs = scheduleCheckAtMs,
        cancelScheduledCheck = cancelScheduledCheck,
        resetStillness = resetStillness,
        retryCount = stillnessRetryCount,
        blockedByProximity = blockedByProximity,
    )

    companion object {
        /** Keep the CPU alive a little past the scheduled deadline. */
        const val WAKE_LOCK_MARGIN_MS = 1_000L
    }
}
