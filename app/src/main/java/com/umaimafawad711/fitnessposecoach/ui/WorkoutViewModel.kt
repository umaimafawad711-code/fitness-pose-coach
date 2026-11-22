package com.umaimafawad711.fitnessposecoach.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.umaimafawad711.fitnessposecoach.FitnessApp
import com.umaimafawad711.fitnessposecoach.analysis.ExerciseType
import com.umaimafawad711.fitnessposecoach.data.SessionResult
import com.umaimafawad711.fitnessposecoach.data.local.DailyStat
import com.umaimafawad711.fitnessposecoach.data.local.WorkoutEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Activity-scoped: shared by ExerciseSelect, Camera, Summary and History screens. */
class WorkoutViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = getApplication<FitnessApp>().repository

    /** Set by the exercise select screen before navigating to the camera. */
    var selectedExercise: ExerciseType = ExerciseType.SQUAT

    private val _lastSession = MutableStateFlow<SessionResult?>(null)
    val lastSession: StateFlow<SessionResult?> = _lastSession.asStateFlow()

    val workouts: StateFlow<List<WorkoutEntity>?> = repository.workouts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val dailyStats: StateFlow<List<DailyStat>> = repository.observeDailyStats(DAYS_IN_CHART)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Shows the result immediately and persists it. Sessions without a single rep are not saved. */
    fun finishSession(result: SessionResult) {
        _lastSession.value = result
        if (result.reps > 0) {
            viewModelScope.launch { repository.save(result) }
        }
    }

    fun deleteWorkout(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun clearHistory() {
        viewModelScope.launch { repository.clear() }
    }

    companion object {
        const val DAYS_IN_CHART = 7
    }
}