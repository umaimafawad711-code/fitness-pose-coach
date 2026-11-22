package com.umaimafawad711.fitnessposecoach.analysis

import com.umaimafawad711.fitnessposecoach.pose.BodyPart
import com.umaimafawad711.fitnessposecoach.pose.PoseResult
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

data class Point2D(val x: Float, val y: Float)

/** Angle measured at [vertex], between the segments vertex→[a] and vertex→[c]. */
data class Joint(val a: BodyPart, val vertex: BodyPart, val c: BodyPart)

object Joints {
    val LEFT_KNEE = Joint(BodyPart.LEFT_HIP, BodyPart.LEFT_KNEE, BodyPart.LEFT_ANKLE)
    val RIGHT_KNEE = Joint(BodyPart.RIGHT_HIP, BodyPart.RIGHT_KNEE, BodyPart.RIGHT_ANKLE)
    val LEFT_ELBOW = Joint(BodyPart.LEFT_SHOULDER, BodyPart.LEFT_ELBOW, BodyPart.LEFT_WRIST)
    val RIGHT_ELBOW = Joint(BodyPart.RIGHT_SHOULDER, BodyPart.RIGHT_ELBOW, BodyPart.RIGHT_WRIST)
    val LEFT_HIP = Joint(BodyPart.LEFT_SHOULDER, BodyPart.LEFT_HIP, BodyPart.LEFT_KNEE)
    val RIGHT_HIP = Joint(BodyPart.RIGHT_SHOULDER, BodyPart.RIGHT_HIP, BodyPart.RIGHT_KNEE)
}

object AngleCalculator {

    /** Returns 0..180 degrees. Degenerate input (a point equal to the vertex) returns 0 instead of throwing. */
    fun calculateAngle(a: Point2D, b: Point2D, c: Point2D): Float {
        if ((a.x == b.x && a.y == b.y) || (c.x == b.x && c.y == b.y)) return 0f
        val radians = atan2(c.y - b.y, c.x - b.x) - atan2(a.y - b.y, a.x - b.x)
        var degrees = abs(radians * 180.0 / PI).toFloat()
        if (degrees > 180f) degrees = 360f - degrees
        return degrees
    }

    /** Null when any of the three keypoints is below the confidence threshold. */
    fun jointAngle(pose: PoseResult, joint: Joint): Float? {
        val a = pose[joint.a]
        val b = pose[joint.vertex]
        val c = pose[joint.c]
        if (minOf(a.score, b.score, c.score) < PoseResult.MIN_SCORE) return null
        // Scale to pixels first: normalized x/y have different units on a non-square frame.
        val w = pose.frameWidth.toFloat()
        val h = pose.frameHeight.toFloat()
        return calculateAngle(
            Point2D(a.x * w, a.y * h),
            Point2D(b.x * w, b.y * h),
            Point2D(c.x * w, c.y * h)
        )
    }

    /** Mean of the visible sides; one side may be occluded when the user stands sideways. */
    fun averageJointAngle(pose: PoseResult, left: Joint, right: Joint): Float? {
        val angles = listOfNotNull(jointAngle(pose, left), jointAngle(pose, right))
        return if (angles.isEmpty()) null else angles.average().toFloat()
    }

    /** Smallest visible angle: picks the bent (front) leg in a lunge. */
    fun minJointAngle(pose: PoseResult, left: Joint, right: Joint): Float? =
        listOfNotNull(jointAngle(pose, left), jointAngle(pose, right)).minOrNull()
}