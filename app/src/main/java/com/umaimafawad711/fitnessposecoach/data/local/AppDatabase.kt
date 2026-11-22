package com.umaimafawad711.fitnessposecoach.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// No fallbackToDestructiveMigration: silently wiping a user's workout history is worse than a crash in dev.
@Database(entities = [WorkoutEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {

    abstract fun workoutDao(): WorkoutDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "fitness_pose_coach.db"
                ).build().also { instance = it }
            }
    }
}