package com.umaimafawad711.fitnessposecoach.data

import com.umaimafawad711.fitnessposecoach.analysis.ExerciseType

/** Plain result of one finished session; shared by Camera, Summary and the repository. */
data class SessionResult(
    val exercise: ExerciseType,
    val startedAtMs: Long,
    val durationSec: Int,
    val reps: Int,
    val formScore: Int
)