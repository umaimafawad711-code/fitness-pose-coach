package com.umaimafawad711.fitnessposecoach.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Day keys (yyyy-MM-dd, device time zone) for the last [count] days, oldest first, ending today.
 * Same format as the SQL grouping in WorkoutDao, so keys can be matched directly.
 */
fun lastDayKeys(count: Int, now: Calendar = Calendar.getInstance()): List<String> {
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val cal = now.clone() as Calendar
    cal.add(Calendar.DAY_OF_YEAR, -(count - 1))
    return List(count) {
        format.format(cal.time).also { cal.add(Calendar.DAY_OF_YEAR, 1) }
    }
}