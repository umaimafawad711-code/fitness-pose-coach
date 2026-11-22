package com.umaimafawad711.fitnessposecoach.analysis

import com.umaimafawad711.fitnessposecoach.pose.PoseResult
import kotlin.math.min

enum class RepState { UP, DOWN }

data class RepSnapshot(
    val state: RepState,
    val reps: Int,
    val angle: Float?,
    /** True only on the single frame where a rep was just counted. */
    val repCompleted: Boolean,
    val lastRepMinAngle: Float?
)

class ExerciseStateMachine(val exercise: ExerciseType) {

    // Thresholds in degrees. Gap between them is the hysteresis band: no state change inside it.
    private class Config(
        val downBelow: Float,
        val upAbove: Float,
        val metric: (PoseResult) -> Float?
    )

    private val config: Config = when (exercise) {
        ExerciseType.SQUAT -> Config(100f, 160f) {
            AngleCalculator.averageJointAngle(it, Joints.LEFT_KNEE, Joints.RIGHT_KNEE)
        }
        ExerciseType.PUSH_UP -> Config(100f, 150f) {
            AngleCalculator.averageJointAngle(it, Joints.LEFT_ELBOW, Joints.RIGHT_ELBOW)
        }
        ExerciseType.LUNGE -> Config(105f, 160f) {
            AngleCalculator.minJointAngle(it, Joints.LEFT_KNEE, Joints.RIGHT_KNEE)
        }
    }

    var state: RepState = RepState.UP
        private set
    var reps: Int = 0
        private set
    var lastRepMinAngle: Float? = null
        private set

    private var pendingState: RepState? = null
    private var pendingFrames = 0
    private var lastRepMs = -1L
    private var minAngleInRep = Float.MAX_VALUE

    fun update(pose: PoseResult, nowMs: Long): RepSnapshot =
        updateAngle(config.metric(pose), nowMs)

    /** Separate from [update] so unit tests can drive the machine without building poses. */
    fun updateAngle(angle: Float?, nowMs: Long): RepSnapshot {
        var completed = false

        if (angle == null) {
            clearPending()
        } else {
            minAngleInRep = min(minAngleInRep, angle)

            val target = when {
                state == RepState.UP && angle < config.downBelow -> RepState.DOWN
                state == RepState.DOWN && angle > config.upAbove -> RepState.UP
                else -> null
            }

            if (target == null) {
                clearPending()
            } else {
                // Debounce: the new zone must hold for several consecutive frames to filter keypoint jitter.
                if (pendingState == target) pendingFrames++ else {
                    pendingState = target
                    pendingFrames = 1
                }
                if (pendingFrames >= CONFIRM_FRAMES) completed = commit(target, nowMs)
            }
        }
        return RepSnapshot(state, reps, angle, completed, lastRepMinAngle)
    }

    fun reset() {
        state = RepState.UP
        reps = 0
        lastRepMinAngle = null
        lastRepMs = -1L
        minAngleInRep = Float.MAX_VALUE
        clearPending()
    }

    private fun commit(target: RepState, nowMs: Long): Boolean {
        clearPending()
        state = target
        if (target != RepState.UP) return false

        // A rep is counted on DOWN -> UP, so partial reps (never reaching DOWN) are never counted.
        lastRepMinAngle = minAngleInRep
        minAngleInRep = Float.MAX_VALUE
        val tooSoon = lastRepMs >= 0 && nowMs - lastRepMs < MIN_REP_INTERVAL_MS
        if (tooSoon) return false

        reps++
        lastRepMs = nowMs
        return true
    }

    private fun clearPending() {
        pendingState = null
        pendingFrames = 0
    }

    companion object {
        const val CONFIRM_FRAMES = 2
        const val MIN_REP_INTERVAL_MS = 500L
    }
}