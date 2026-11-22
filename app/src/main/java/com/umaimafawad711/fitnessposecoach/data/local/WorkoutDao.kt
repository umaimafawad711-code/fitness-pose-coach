package com.umaimafawad711.fitnessposecoach.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {

    @Insert
    suspend fun insert(workout: WorkoutEntity): Long

    @Query("SELECT * FROM workouts ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun getById(id: Long): WorkoutEntity?

    // Grouped in SQL with 'localtime' so a 11:30 PM session lands on the user's own day, not UTC's.
    @Query(
        "SELECT strftime('%Y-%m-%d', timestamp / 1000, 'unixepoch', 'localtime') AS day, " +
                "SUM(reps) AS totalReps, " +
                "CAST(ROUND(AVG(formScore)) AS INTEGER) AS avgFormScore, " +
                "COUNT(*) AS sessions " +
                "FROM workouts WHERE timestamp >= :since GROUP BY day ORDER BY day ASC"
    )
    fun observeDailyStats(since: Long): Flow<List<DailyStat>>

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM workouts")
    suspend fun deleteAll()
}