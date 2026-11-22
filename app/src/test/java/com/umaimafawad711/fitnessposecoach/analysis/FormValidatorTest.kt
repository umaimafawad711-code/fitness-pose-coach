package com.umaimafawad711.fitnessposecoach.analysis

import com.umaimafawad711.fitnessposecoach.pose.BodyPart
import com.umaimafawad711.fitnessposecoach.pose.BodyPart.LEFT_ANKLE
import com.umaimafawad711.fitnessposecoach.pose.BodyPart.LEFT_HIP
import com.umaimafawad711.fitnessposecoach.pose.BodyPart.LEFT_KNEE
import com.umaimafawad711.fitnessposecoach.pose.BodyPart.LEFT_SHOULDER
import com.umaimafawad711.fitnessposecoach.pose.BodyPart.RIGHT_ANKLE
import com.umaimafawad711.fitnessposecoach.pose.BodyPart.RIGHT_HIP
import com.umaimafawad711.fitnessposecoach.pose.BodyPart.RIGHT_KNEE
import com.umaimafawad711.fitnessposecoach.pose.BodyPart.RIGHT_SHOULDER
import com.umaimafawad711.fitnessposecoach.pose.Keypoint
import com.umaimafawad711.fitnessposecoach.pose.PoseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FormValidatorTest {

    // Unlisted keypoints get score 0 (not visible). Frame is 100x100 so pixels == normalized * 100.
    private fun pose(vararg pts: Triple<BodyPart, Float, Float>): PoseResult {
        val byPart = pts.associateBy { it.first }
        val keypoints = BodyPart.entries.map { part ->
            byPart[part]?.let { Keypoint(part, it.second, it.third, 0.9f) }
                ?: Keypoint(part, 0f, 0f, 0f)
        }
        return PoseResult(keypoints, 100, 100, 0L)
    }

    // Frontal squat: both knees bent about 141 degrees; only knee X varies per test.
    private fun squatPose(leftKneeX: Float, rightKneeX: Float) = pose(
        Triple(LEFT_SHOULDER, 0.40f, 0.15f), Triple(RIGHT_SHOULDER, 0.60f, 0.15f),
        Triple(LEFT_HIP, 0.40f, 0.40f), Triple(RIGHT_HIP, 0.60f, 0.40f),
        Triple(LEFT_KNEE, leftKneeX, 0.62f), Triple(RIGHT_KNEE, rightKneeX, 0.62f),
        Triple(LEFT_ANKLE, 0.36f, 0.85f), Triple(RIGHT_ANKLE, 0.64f, 0.85f)
    )

    private val squat = FormValidator(ExerciseType.SQUAT)
    private val pushUp = FormValidator(ExerciseType.PUSH_UP)
    private val lunge = FormValidator(ExerciseType.LUNGE)

    @Test
    fun noKeypoints_warnsNoPerson() {
        val fb = squat.validate(pose())
        assertEquals(Severity.WARNING, fb.severity)
        assertEquals(FormValidator.MSG_NO_PERSON, fb.message)
    }

    @Test
    fun squat_standing_isInfo() {
        assertEquals(Severity.INFO, squat.validate(squatPose(0.38f, 0.62f)).severity)
    }

    @Test
    fun squat_kneesCavingIn_warnsAndMarksKnees() {
        val fb = squat.validate(squatPose(0.46f, 0.54f))
        assertEquals(FormValidator.MSG_KNEES_OUT, fb.message)
        assertEquals(setOf(LEFT_KNEE, RIGHT_KNEE), fb.faultyParts)
    }

    @Test
    fun squat_kneesOutward_isInfo() {
        assertEquals(Severity.INFO, squat.validate(squatPose(0.30f, 0.70f)).severity)
    }

    @Test
    fun squat_excessiveForwardLean_isError() {
        val sideView = pose(
            Triple(LEFT_SHOULDER, 0.80f, 0.38f),
            Triple(LEFT_HIP, 0.50f, 0.50f),
            Triple(LEFT_KNEE, 0.70f, 0.55f),
            Triple(LEFT_ANKLE, 0.55f, 0.85f)
        )
        val fb = squat.validate(sideView)
        assertEquals(Severity.ERROR, fb.severity)
        assertEquals(FormValidator.MSG_CHEST_UP, fb.message)
    }

    @Test
    fun pushUp_sagging_isError() {
        val sag = pose(
            Triple(LEFT_SHOULDER, 0.20f, 0.50f),
            Triple(LEFT_HIP, 0.50f, 0.70f),
            Triple(LEFT_ANKLE, 0.80f, 0.50f)
        )
        assertEquals(FormValidator.MSG_BODY_LINE, pushUp.validate(sag).message)
    }

    @Test
    fun pushUp_straightBody_isInfo() {
        val straight = pose(
            Triple(LEFT_SHOULDER, 0.20f, 0.50f),
            Triple(LEFT_HIP, 0.50f, 0.50f),
            Triple(LEFT_ANKLE, 0.80f, 0.50f)
        )
        assertEquals(Severity.INFO, pushUp.validate(straight).severity)
    }

    @Test
    fun lunge_leaningTorso_warns() {
        val leaning = pose(
            Triple(LEFT_SHOULDER, 0.70f, 0.20f), Triple(RIGHT_SHOULDER, 0.70f, 0.20f),
            Triple(LEFT_HIP, 0.40f, 0.50f), Triple(RIGHT_HIP, 0.40f, 0.50f)
        )
        assertEquals(FormValidator.MSG_TORSO_UPRIGHT, lunge.validate(leaning).message)
    }

    @Test
    fun lunge_uprightTorso_isInfo() {
        val upright = pose(
            Triple(LEFT_SHOULDER, 0.42f, 0.20f), Triple(RIGHT_SHOULDER, 0.42f, 0.20f),
            Triple(LEFT_HIP, 0.40f, 0.50f), Triple(RIGHT_HIP, 0.40f, 0.50f)
        )
        assertEquals(Severity.INFO, lunge.validate(upright).severity)
    }

    @Test
    fun evaluateRep_depth() {
        assertNull(squat.evaluateRep(90f))
        assertNull(squat.evaluateRep(null))
        assertNotNull(squat.evaluateRep(110f))
    }
}

class FormScoreTrackerTest {

    private val ok = Feedback("ok", Severity.INFO)
    private val bad = Feedback("bad", Severity.WARNING)

    @Test
    fun cleanRep_scores100() {
        val tracker = FormScoreTracker()
        repeat(10) { tracker.onFrame(ok) }
        assertEquals(100, tracker.onRepCompleted(depthOk = true))
    }

    @Test
    fun halfFaultyFrames_scores50() {
        val tracker = FormScoreTracker()
        repeat(2) { tracker.onFrame(ok) }
        repeat(2) { tracker.onFrame(bad) }
        assertEquals(50, tracker.onRepCompleted(depthOk = true))
    }

    @Test
    fun missedDepth_costs20() {
        val tracker = FormScoreTracker()
        repeat(10) { tracker.onFrame(ok) }
        assertEquals(80, tracker.onRepCompleted(depthOk = false))
    }

    @Test
    fun average_isMeanOfReps() {
        val tracker = FormScoreTracker()
        repeat(10) { tracker.onFrame(ok) }
        tracker.onRepCompleted(depthOk = true)
        repeat(10) { tracker.onFrame(ok) }
        tracker.onRepCompleted(depthOk = false)
        assertEquals(90, tracker.averageScore)
    }

    @Test
    fun noReps_averageIsZero() {
        assertEquals(0, FormScoreTracker().averageScore)
    }
}