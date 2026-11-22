package com.umaimafawad711.fitnessposecoach.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class DayRangeTest {

    private fun date(year: Int, month: Int, day: Int): Calendar =
        Calendar.getInstance().apply { set(year, month, day, 10, 0, 0) }

    @Test
    fun sevenDays_endsToday_oldestFirst() {
        val keys = lastDayKeys(7, date(2026, Calendar.NOVEMBER, 22))
        assertEquals(7, keys.size)
        assertEquals("2026-11-16", keys.first())
        assertEquals("2026-11-22", keys.last())
    }

    @Test
    fun crossesMonthBoundary() {
        val keys = lastDayKeys(7, date(2026, Calendar.MARCH, 2))
        assertEquals("2026-02-24", keys.first())
        assertEquals("2026-03-02", keys.last())
    }

    @Test
    fun singleDay_isToday() {
        assertEquals(listOf("2026-11-22"), lastDayKeys(1, date(2026, Calendar.NOVEMBER, 22)))
    }

    @Test
    fun doesNotMutateInput() {
        val now = date(2026, Calendar.NOVEMBER, 22)
        val before = now.timeInMillis
        lastDayKeys(7, now)
        assertEquals(before, now.timeInMillis)
    }
}