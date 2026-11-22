package com.umaimafawad711.fitnessposecoach

import android.app.Application
import com.umaimafawad711.fitnessposecoach.data.local.AppDatabase
import com.umaimafawad711.fitnessposecoach.data.repository.WorkoutRepository

class FitnessApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val repository: WorkoutRepository by lazy { WorkoutRepository(database.workoutDao()) }
}