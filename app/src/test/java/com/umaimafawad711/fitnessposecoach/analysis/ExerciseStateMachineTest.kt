package com.umaimafawad711.fitnessposecoach.analysis

import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseStateMachineTest {

    private var now = 1_000L

    private fun hold(
        sm: ExerciseStateMachine,
        angle: Float,
        frames: Int = ExerciseStateMachine.CONFIRM_FRAMES
    ) {
        repeat(frames) {
            sm.updateAngle(angle, now)
            now += 50
        }
    }

    private fun slowRep(sm: ExerciseStateMachine) {
        hold(sm, 170f)
        hold(sm, 90f)
        hold(sm, 170f)
        now += 600
    }

    @Test
    fun fullCycle_countsOneRep() {
        val sm = ExerciseStateMachine(ExerciseType.SQUAT)
        hold(sm, 170f)
        hold(sm, 90f)
        assertEquals(RepState.DOWN, sm.state)
        hold(sm, 170f)
        assertEquals(1, sm.reps)
        assertEquals(RepState.UP, sm.state)
        assertEquals(90f, sm.lastRepMinAngle!!, 0.01f)
    }

    @Test
    fun partialRep_isNotCounted() {
        val sm = ExerciseStateMachine(ExerciseType.SQUAT)
        hold(sm, 170f)
        hold(sm, 125f)
        hold(sm, 170f)
        assertEquals(0, sm.reps)
    }

    @Test
    fun jitterInsideHysteresisBand_keepsDownState() {
        val sm = ExerciseStateMachine(ExerciseType.SQUAT)
        hold(sm, 90f)
        listOf(150f, 130f, 155f, 150f, 140f, 158f).forEach { hold(sm, it, frames = 3) }
        assertEquals(RepState.DOWN, sm.state)
        assertEquals(0, sm.reps)
    }

    @Test
    fun singleFrameSpike_isIgnored() {
        val sm = ExerciseStateMachine(ExerciseType.SQUAT)
        hold(sm, 170f)
        hold(sm, 90f, frames = 1)
        hold(sm, 170f)
        assertEquals(RepState.UP, sm.state)
        assertEquals(0, sm.reps)
    }

    @Test
    fun repsFasterThanMinInterval_areIgnored() {
        val sm = ExerciseStateMachine(ExerciseType.SQUAT)
        hold(sm, 90f)
        hold(sm, 170f)
        hold(sm, 90f)
        hold(sm, 170f)
        assertEquals(1, sm.reps)
    }

    @Test
    fun threeSlowReps_areCounted() {
        val sm = ExerciseStateMachine(ExerciseType.PUSH_UP)
        repeat(3) { slowRep(sm) }
        assertEquals(3, sm.reps)
    }

    @Test
    fun nullAngle_doesNotChangeState() {
        val sm = ExerciseStateMachine(ExerciseType.LUNGE)
        hold(sm, 90f)
        repeat(5) { sm.updateAngle(null, now); now += 50 }
        assertEquals(RepState.DOWN, sm.state)
        assertEquals(0, sm.reps)
    }

    @Test
    fun reset_clearsEverything() {
        val sm = ExerciseStateMachine(ExerciseType.SQUAT)
        slowRep(sm)
        sm.reset()
        assertEquals(0, sm.reps)
        assertEquals(RepState.UP, sm.state)
    }
}