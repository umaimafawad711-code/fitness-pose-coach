package com.umaimafawad711.fitnessposecoach.pose

// Declaration order MUST match the MoveNet output index order.
enum class BodyPart {
    NOSE, LEFT_EYE, RIGHT_EYE, LEFT_EAR, RIGHT_EAR,
    LEFT_SHOULDER, RIGHT_SHOULDER, LEFT_ELBOW, RIGHT_ELBOW,
    LEFT_WRIST, RIGHT_WRIST, LEFT_HIP, RIGHT_HIP,
    LEFT_KNEE, RIGHT_KNEE, LEFT_ANKLE, RIGHT_ANKLE
}

/** x, y are normalized 0..1 relative to the upright camera frame (not the model input). */
data class Keypoint(
    val part: BodyPart,
    val x: Float,
    val y: Float,
    val score: Float
)