package com.umaimafawad711.fitnessposecoach.data.local

/** One row per calendar day (device time zone), used by the History charts. */
data class DailyStat(
    /** Format yyyy-MM-dd. */
    val day: String,
    val totalReps: Int,
    val avgFormScore: Int,
    val sessions: Int
)