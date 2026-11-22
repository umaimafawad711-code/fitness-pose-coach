package com.umaimafawad711.fitnessposecoach.ui.select

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.umaimafawad711.fitnessposecoach.R
import com.umaimafawad711.fitnessposecoach.analysis.ExerciseType
import com.umaimafawad711.fitnessposecoach.databinding.FragmentExerciseSelectBinding
import com.umaimafawad711.fitnessposecoach.ui.WorkoutViewModel

class ExerciseSelectScreen : Fragment(R.layout.fragment_exercise_select) {

    private val viewModel: WorkoutViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentExerciseSelectBinding.bind(view)
        binding.btnSquat.setOnClickListener { start(ExerciseType.SQUAT) }
        binding.btnPushUp.setOnClickListener { start(ExerciseType.PUSH_UP) }
        binding.btnLunge.setOnClickListener { start(ExerciseType.LUNGE)
            binding.btnHistory.setOnClickListener {
                findNavController().navigate(R.id.action_select_to_history)
            }}
    }

    private fun start(type: ExerciseType) {
        viewModel.selectedExercise = type
        findNavController().navigate(R.id.action_select_to_camera)
    }
}