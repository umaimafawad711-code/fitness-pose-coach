package com.umaimafawad711.fitnessposecoach.analysis

import com.umaimafawad711.fitnessposecoach.pose.BodyPart
import com.umaimafawad711.fitnessposecoach.pose.PoseResult
import kotlin.math.abs
import kotlin.math.atan2

class FormValidator(private val exercise: ExerciseType) {

    /** Live, per-frame check. Always returns something; INFO means "no problem". */
    fun validate(pose: PoseResult): Feedback {
        if (pose.visibleCount < MIN_VISIBLE_KEYPOINTS) {
            return Feedback(MSG_NO_PERSON, Severity.WARNING)
        }
        val issue = when (exercise) {
            ExerciseType.SQUAT -> checkSquat(pose)
            ExerciseType.PUSH_UP -> checkPushUp(pose)
            ExerciseType.LUNGE -> checkLunge(pose)
        }
        return issue ?: Feedback(MSG_GOOD, Severity.INFO)
    }

    /** Rep-level check, called once per completed rep. Null means depth was fine. */
    fun evaluateRep(minAngle: Float?): Feedback? {
        if (minAngle == null) return null
        val target = when (exercise) {
            ExerciseType.SQUAT -> 95f
            ExerciseType.PUSH_UP -> 95f
            ExerciseType.LUNGE -> 100f
        }
        return if (minAngle > target) Feedback(MSG_GO_DEEPER, Severity.WARNING) else null
    }

    private fun checkSquat(pose: PoseResult): Feedback? {
        val knee = AngleCalculator.averageJointAngle(pose, Joints.LEFT_KNEE, Joints.RIGHT_KNEE)
            ?: return null
        if (knee >= BENT_KNEE_ANGLE) return null // standing: nothing to judge yet

        if (kneesCavingIn(pose)) {
            return Feedback(
                MSG_KNEES_OUT, Severity.WARNING,
                setOf(BodyPart.LEFT_KNEE, BodyPart.RIGHT_KNEE)
            )
        }
        val hip = AngleCalculator.averageJointAngle(pose, Joints.LEFT_HIP, Joints.RIGHT_HIP)
        if (hip != null && hip < MIN_SQUAT_HIP_ANGLE) {
            return Feedback(
                MSG_CHEST_UP, Severity.ERROR,
                setOf(
                    BodyPart.LEFT_SHOULDER, BodyPart.RIGHT_SHOULDER,
                    BodyPart.LEFT_HIP, BodyPart.RIGHT_HIP
                )
            )
        }
        return null
    }

    private fun checkPushUp(pose: PoseResult): Feedback? {
        val line = AngleCalculator.averageJointAngle(pose, LEFT_BODY_LINE, RIGHT_BODY_LINE)
            ?: return null
        return if (line < MIN_BODY_LINE_ANGLE) {
            Feedback(
                MSG_BODY_LINE, Severity.ERROR,
                setOf(BodyPart.LEFT_HIP, BodyPart.RIGHT_HIP)
            )
        } else null
    }

    private fun checkLunge(pose: PoseResult): Feedback? {
        val lean = torsoLeanDegrees(pose) ?: return null
        return if (lean > MAX_LUNGE_LEAN) {
            Feedback(
                MSG_TORSO_UPRIGHT, Severity.WARNING,
                setOf(
                    BodyPart.LEFT_SHOULDER, BodyPart.RIGHT_SHOULDER,
                    BodyPart.LEFT_HIP, BodyPart.RIGHT_HIP
                )
            )
        } else null
    }

    private fun kneesCavingIn(pose: PoseResult): Boolean {
        val lk = pose[BodyPart.LEFT_KNEE]
        val rk = pose[BodyPart.RIGHT_KNEE]
        val la = pose[BodyPart.LEFT_ANKLE]
        val ra = pose[BodyPart.RIGHT_ANKLE]
        if (minOf(lk.score, rk.score, la.score, ra.score) < PoseResult.MIN_SCORE) return false

        val kneeGap = abs(lk.x - rk.x)
        val ankleGap = abs(la.x - ra.x)
        // Near-zero ankle gap means a side view, where a knee-width check is meaningless.
        return ankleGap > MIN_ANKLE_GAP && kneeGap < KNEE_ANKLE_RATIO * ankleGap
    }

    /** Angle of the shoulder-hip line from vertical, averaged over visible sides. */
    private fun torsoLeanDegrees(pose: PoseResult): Float? {
        val w = pose.frameWidth.toFloat()
        val h = pose.frameHeight.toFloat()
        val leans = listOf(
            BodyPart.LEFT_SHOULDER to BodyPart.LEFT_HIP,
            BodyPart.RIGHT_SHOULDER to BodyPart.RIGHT_HIP
        ).mapNotNull { (shoulder, hip) ->
            val s = pose[shoulder]
            val p = pose[hip]
            if (minOf(s.score, p.score) < PoseResult.MIN_SCORE) {
                null
            } else {
                val dx = abs(s.x - p.x) * w
                val dy = abs(s.y - p.y) * h
                Math.toDegrees(atan2(dx, dy).toDouble()).toFloat()
            }
        }
        return if (leans.isEmpty()) null else leans.average().toFloat()
    }

    companion object {
        const val MSG_NO_PERSON = "Step into the camera view"
        const val MSG_GOOD = "Good form"
        const val MSG_KNEES_OUT = "Push your knees outward"
        const val MSG_CHEST_UP = "Keep your chest up"
        const val MSG_BODY_LINE = "Keep your body in a straight line"
        const val MSG_TORSO_UPRIGHT = "Keep your torso upright"
        const val MSG_GO_DEEPER = "Go deeper next rep"

        private const val MIN_VISIBLE_KEYPOINTS = 3
        private const val BENT_KNEE_ANGLE = 150f
        private const val MIN_SQUAT_HIP_ANGLE = 45f
        private const val MIN_BODY_LINE_ANGLE = 155f
        private const val MAX_LUNGE_LEAN = 30f
        private const val MIN_ANKLE_GAP = 0.08f
        private const val KNEE_ANKLE_RATIO = 0.8f

        private val LEFT_BODY_LINE =
            Joint(BodyPart.LEFT_SHOULDER, BodyPart.LEFT_HIP, BodyPart.LEFT_ANKLE)
        private val RIGHT_BODY_LINE =
            Joint(BodyPart.RIGHT_SHOULDER, BodyPart.RIGHT_HIP, BodyPart.RIGHT_ANKLE)
    }
}