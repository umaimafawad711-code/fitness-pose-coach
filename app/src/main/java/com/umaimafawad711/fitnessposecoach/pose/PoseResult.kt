package com.umaimafawad711.fitnessposecoach.pose



/**
 * frameWidth/frameHeight are needed because x/y are normalized separately:
 * multiply by them before computing angles, otherwise angles are distorted on non-square frames.
 */
data class PoseResult(
    val keypoints: List<Keypoint>,
    val frameWidth: Int,
    val frameHeight: Int,
    val latencyMs: Long
) {
    operator fun get(part: BodyPart): Keypoint = keypoints[part.ordinal]

    val visibleCount: Int
        get() = keypoints.count { it.score >= MIN_SCORE }

    companion object {
        const val MIN_SCORE = 0.3f
    }
}