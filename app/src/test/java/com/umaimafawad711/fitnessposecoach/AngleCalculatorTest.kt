package com.umaimafawad711.fitnessposecoach.analysis

import com.umaimafawad711.fitnessposecoach.pose.BodyPart
import com.umaimafawad711.fitnessposecoach.pose.Keypoint
import com.umaimafawad711.fitnessposecoach.pose.PoseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AngleCalculatorTest {

    private val delta = 0.01f

    @Test
    fun rightAngle_is90() {
        val angle = AngleCalculator.calculateAngle(Point2D(1f, 0f), Point2D(0f, 0f), Point2D(0f, 1f))
        assertEquals(90f, angle, delta)
    }

    @Test
    fun straightLine_is180() {
        val angle = AngleCalculator.calculateAngle(Point2D(-1f, 0f), Point2D(0f, 0f), Point2D(1f, 0f))
        assertEquals(180f, angle, delta)
    }

    @Test
    fun overlappingSegments_is0() {
        val angle = AngleCalculator.calculateAngle(Point2D(1f, 0f), Point2D(0f, 0f), Point2D(2f, 0f))
        assertEquals(0f, angle, delta)
    }

    @Test
    fun fortyFiveDegrees() {
        val angle = AngleCalculator.calculateAngle(Point2D(1f, 0f), Point2D(0f, 0f), Point2D(1f, 1f))
        assertEquals(45f, angle, delta)
    }

    @Test
    fun reflexResult_isFoldedBelow180() {
        // atan2 difference of 270 degrees must come back as 90.
        val angle = AngleCalculator.calculateAngle(Point2D(0f, 1f), Point2D(0f, 0f), Point2D(1f, 0f))
        assertEquals(90f, angle, delta)
    }

    @Test
    fun swappingEndpoints_givesSameAngle() {
        val a = Point2D(3f, 1f)
        val b = Point2D(0f, 0f)
        val c = Point2D(-1f, 4f)
        assertEquals(
            AngleCalculator.calculateAngle(a, b, c),
            AngleCalculator.calculateAngle(c, b, a),
            delta
        )
    }

    @Test
    fun zeroLengthSegment_returnsZero() {
        val p = Point2D(2f, 2f)
        assertEquals(0f, AngleCalculator.calculateAngle(p, p, Point2D(5f, 5f)), delta)
    }

    @Test
    fun jointAngle_usesPixelAspect() {
        // 100x200 frame: normalized (0.5, 0.25) is 50px right and 50px down => 90 degrees at the vertex.
        val pose = poseWith(
            BodyPart.LEFT_HIP to Keypoint(BodyPart.LEFT_HIP, 0.5f, 0.25f, 0.9f),
            BodyPart.LEFT_KNEE to Keypoint(BodyPart.LEFT_KNEE, 0f, 0f, 0.9f),
            BodyPart.LEFT_ANKLE to Keypoint(BodyPart.LEFT_ANKLE, 0.5f, 0f, 0.9f),
            width = 100,
            height = 200
        )
        assertEquals(45f, AngleCalculator.jointAngle(pose, Joints.LEFT_KNEE)!!, delta)
    }

    @Test
    fun jointAngle_lowConfidence_returnsNull() {
        val pose = poseWith(
            BodyPart.LEFT_HIP to Keypoint(BodyPart.LEFT_HIP, 0f, 0f, 0.9f),
            BodyPart.LEFT_KNEE to Keypoint(BodyPart.LEFT_KNEE, 0.5f, 0.5f, 0.1f),
            BodyPart.LEFT_ANKLE to Keypoint(BodyPart.LEFT_ANKLE, 1f, 1f, 0.9f)
        )
        assertNull(AngleCalculator.jointAngle(pose, Joints.LEFT_KNEE))
    }

    private fun poseWith(
        vararg overrides: Pair<BodyPart, Keypoint>,
        width: Int = 100,
        height: Int = 100
    ): PoseResult {
        val map = overrides.toMap()
        val keypoints = BodyPart.entries.map { map[it] ?: Keypoint(it, 0f, 0f, 0f) }
        return PoseResult(keypoints, width, height, 0L)
    }
}