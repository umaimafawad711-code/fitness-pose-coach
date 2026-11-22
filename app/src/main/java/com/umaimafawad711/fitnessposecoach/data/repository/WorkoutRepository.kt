package com.umaimafawad711.fitnessposecoach.data.repository

import com.umaimafawad711.fitnessposecoach.data.SessionResult
import com.umaimafawad711.fitnessposecoach.data.local.DailyStat
import com.umaimafawad711.fitnessposecoach.data.local.WorkoutDao
import com.umaimafawad711.fitnessposecoach.data.local.WorkoutEntity
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class WorkoutRepository(private val dao: WorkoutDao) {

    val workouts: Flow<List<WorkoutEntity>> = dao.observeAll()

    /** Last [days] calendar days including today. Days without a workout are simply absent. */
    fun observeDailyStats(days: Int): Flow<List<DailyStat>> {
        val since = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -(days - 1))
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return dao.observeDailyStats(since)
    }

    suspend fun save(result: SessionResult): Long = dao.insert(
        WorkoutEntity(
            exercise = result.exercise.name,
            timestamp = result.startedAtMs,
            durationSec = result.durationSec,
            reps = result.reps,
            formScore = result.formScore
        )
    )

    suspend fun delete(id: Long) = dao.deleteById(id)

    suspend fun clear() = dao.deleteAll()
}