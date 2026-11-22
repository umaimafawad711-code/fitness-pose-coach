package com.umaimafawad711.fitnessposecoach.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.umaimafawad711.fitnessposecoach.analysis.ExerciseType
import com.umaimafawad711.fitnessposecoach.data.local.AppDatabase
import com.umaimafawad711.fitnessposecoach.data.local.WorkoutDao
import com.umaimafawad711.fitnessposecoach.data.local.WorkoutEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar

@RunWith(AndroidJUnit4::class)
class WorkoutDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: WorkoutDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.workoutDao()
    }

    @After
    fun tearDown() = db.close()

    private fun at(daysAgo: Int, hour: Int): Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -daysAgo)
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun workout(timestamp: Long, reps: Int = 10, score: Int = 90) = WorkoutEntity(
        exercise = ExerciseType.SQUAT.name,
        timestamp = timestamp,
        durationSec = 60,
        reps = reps,
        formScore = score
    )

    @Test
    fun insert_thenGetById_returnsSameRow() = runBlocking<Unit> {
        val id = dao.insert(workout(at(0, 8), reps = 12, score = 77))
        val loaded = dao.getById(id)
        assertNotNull(loaded)
        assertEquals(12, loaded!!.reps)
        assertEquals(77, loaded.formScore)
        assertEquals(ExerciseType.SQUAT.name, loaded.exercise)
    }

    @Test
    fun observeAll_ordersNewestFirst() = runBlocking<Unit> {
        dao.insert(workout(at(2, 8)))
        dao.insert(workout(at(0, 8)))
        dao.insert(workout(at(1, 8)))
        val timestamps = dao.observeAll().first().map { it.timestamp }
        assertEquals(timestamps.sortedDescending(), timestamps)
    }

    @Test
    fun deleteById_removesOnlyThatRow() = runBlocking<Unit> {
        val keep = dao.insert(workout(at(0, 8)))
        val drop = dao.insert(workout(at(0, 9)))
        dao.deleteById(drop)
        assertNull(dao.getById(drop))
        assertNotNull(dao.getById(keep))
    }

    @Test
    fun dailyStats_groupsSessionsOfTheSameDay() = runBlocking<Unit> {
        dao.insert(workout(at(0, 8), reps = 10, score = 80))
        dao.insert(workout(at(0, 9), reps = 5, score = 90))
        dao.insert(workout(at(1, 8), reps = 7, score = 70))

        val stats = dao.observeDailyStats(since = 0L).first()
        assertEquals(2, stats.size)
        val today = stats.last()
        assertEquals(15, today.totalReps)
        assertEquals(85, today.avgFormScore)
        assertEquals(2, today.sessions)
    }

    @Test
    fun dailyStats_excludesRowsOlderThanSince() = runBlocking<Unit> {
        dao.insert(workout(at(30, 8)))
        dao.insert(workout(at(0, 8)))
        val stats = dao.observeDailyStats(since = at(6, 0)).first()
        assertEquals(1, stats.size)
    }

    @Test
    fun dailyStats_emptyTable_returnsEmptyList() = runBlocking<Unit> {
        assertEquals(0, dao.observeDailyStats(since = 0L).first().size)
    }
}