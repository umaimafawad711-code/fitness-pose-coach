package com.umaimafawad711.fitnessposecoach.analysis

import kotlin.math.roundToInt

/** Turns per-frame feedback into a 0..100 score per rep and a session average. */
class FormScoreTracker {

    private var frames = 0
    private var faultFrames = 0
    private val repScores = mutableListOf<Int>()

    /** 0 when no rep has been completed: nothing was measured. */
    val averageScore: Int
        get() = if (repScores.isEmpty()) 0 else repScores.average().roundToInt()

    fun onFrame(feedback: Feedback) {
        frames++
        if (feedback.severity != Severity.INFO) faultFrames++
    }

    fun onRepCompleted(depthOk: Boolean): Int {
        val faultRatio = if (frames == 0) 0f else faultFrames.toFloat() / frames
        var score = (100 * (1f - faultRatio)).roundToInt()
        if (!depthOk) score -= DEPTH_PENALTY
        score = score.coerceIn(0, 100)

        repScores += score
        frames = 0
        faultFrames = 0
        return score
    }

    fun reset() {
        frames = 0
        faultFrames = 0
        repScores.clear()
    }

    private companion object {
        const val DEPTH_PENALTY = 20
    }
}