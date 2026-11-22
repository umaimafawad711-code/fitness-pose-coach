package com.umaimafawad711.fitnessposecoach.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.umaimafawad711.fitnessposecoach.analysis.ExerciseType

@Entity(tableName = "workouts", indices = [Index("timestamp")])
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** [ExerciseType.name]. Stored as text so adding enum values never needs a migration. */
    val exercise: String,
    /** Session start, epoch milliseconds. */
    val timestamp: Long,
    val durationSec: Int,
    val reps: Int,
    /** 0..100 average form score. */
    val formScore: Int
)

fun WorkoutEntity.exerciseType(): ExerciseType =
    ExerciseType.entries.firstOrNull { it.name == exercise } ?: ExerciseType.SQUAT