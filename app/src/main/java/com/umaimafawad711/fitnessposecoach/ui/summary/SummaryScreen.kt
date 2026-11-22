package com.umaimafawad711.fitnessposecoach.ui.summary

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.umaimafawad711.fitnessposecoach.R
import com.umaimafawad711.fitnessposecoach.databinding.FragmentSummaryBinding
import com.umaimafawad711.fitnessposecoach.ui.WorkoutViewModel
import com.umaimafawad711.fitnessposecoach.util.formatDuration

class SummaryScreen : Fragment(R.layout.fragment_summary) {

    private val viewModel: WorkoutViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentSummaryBinding.bind(view)

        binding.btnDone.setOnClickListener {
            findNavController().popBackStack(R.id.exerciseSelectScreen, false)
        }

        val result = viewModel.lastSession.value
        if (result == null) { // e.g. process was recreated: nothing to show
            findNavController().popBackStack(R.id.exerciseSelectScreen, false)
            return
        }

        binding.tvExercise.text = result.exercise.displayName
        binding.tvReps.text = result.reps.toString()
        binding.tvDuration.text = "Duration ${formatDuration(result.durationSec)}"

        if (result.reps == 0) {
            binding.tvScore.text = "Form score —"
            binding.tvNote.text = "No reps were counted, so this session was not saved."
            return
        }

        binding.tvScore.text = "Form score ${result.formScore}/100"
        binding.tvNote.text = when {
            result.formScore >= 85 -> "Excellent form. Keep it up!"
            result.formScore >= 70 -> "Good work. A few details to polish."
            else -> "Slow down and watch the red joints on screen."
        }
    }
}