package com.umaimafawad711.fitnessposecoach.ui.history

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.mikephil.charting.charts.BarLineChartBase
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.umaimafawad711.fitnessposecoach.R
import com.umaimafawad711.fitnessposecoach.data.local.DailyStat
import com.umaimafawad711.fitnessposecoach.data.local.WorkoutEntity
import com.umaimafawad711.fitnessposecoach.data.local.exerciseType
import com.umaimafawad711.fitnessposecoach.databinding.FragmentHistoryBinding
import com.umaimafawad711.fitnessposecoach.ui.WorkoutViewModel
import com.umaimafawad711.fitnessposecoach.util.lastDayKeys
import kotlinx.coroutines.launch

class HistoryScreen : Fragment(R.layout.fragment_history) {

    private val viewModel: WorkoutViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentHistoryBinding.bind(view)
        // Theme-aware text color: hard-coded black would be invisible in dark mode.
        val textColor = MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurface)

        val adapter = WorkoutAdapter { workout -> confirmDelete(workout) }
        binding.rvWorkouts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvWorkouts.adapter = adapter
        binding.btnClear.setOnClickListener { confirmClear() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.workouts.collect { list ->
                        if (list == null) return@collect // still loading from Room
                        binding.tvEmpty.isVisible = list.isEmpty()
                        binding.content.isVisible = list.isNotEmpty()
                        // The list is inside a NestedScrollView (no view recycling), so cap it.
                        adapter.submitList(list.take(MAX_LISTED))
                    }
                }
                launch {
                    viewModel.dailyStats.collect { stats -> drawCharts(binding, stats, textColor) }
                }
            }
        }
    }

    private fun drawCharts(binding: FragmentHistoryBinding, stats: List<DailyStat>, textColor: Int) {
        val days = lastDayKeys(WorkoutViewModel.DAYS_IN_CHART)
        val labels = days.map { it.substring(5) } // MM-dd
        val byDay = stats.associateBy { it.day }

        // Bars: every day gets a bar, 0 when nothing was done.
        val barEntries = days.mapIndexed { index, day ->
            BarEntry(index.toFloat(), (byDay[day]?.totalReps ?: 0).toFloat())
        }
        val barSet = BarDataSet(barEntries, "Reps").apply {
            color = GREEN
            valueTextColor = textColor
            valueTextSize = 11f
            valueFormatter = object : ValueFormatter() {
                override fun getBarLabel(barEntry: BarEntry?): String =
                    if (barEntry != null && barEntry.y > 0f) barEntry.y.toInt().toString() else ""
            }
        }
        binding.chartReps.apply {
            styleChart(this, labels, textColor)
            data = BarData(barSet).apply { barWidth = 0.6f }
            setFitBars(true)
            invalidate()
        }

        // Line: only days with a workout get a point, so rest days do not drag the trend to 0.
        val lineEntries = days.mapIndexedNotNull { index, day ->
            byDay[day]?.let { Entry(index.toFloat(), it.avgFormScore.toFloat()) }
        }
        val lineSet = LineDataSet(lineEntries, "Form score").apply {
            color = BLUE
            setCircleColor(BLUE)
            lineWidth = 2.5f
            circleRadius = 4f
            valueTextColor = textColor
            valueTextSize = 11f
            valueFormatter = object : ValueFormatter() {
                override fun getPointLabel(entry: Entry?): String =
                    entry?.y?.toInt()?.toString().orEmpty()
            }
        }
        binding.chartForm.apply {
            styleChart(this, labels, textColor)
            axisLeft.axisMaximum = 100f
            data = LineData(lineSet)
            invalidate()
        }
    }

    private fun styleChart(chart: BarLineChartBase<*>, labels: List<String>, textColor: Int) {
        chart.description.isEnabled = false
        chart.legend.isEnabled = false
        chart.axisRight.isEnabled = false
        chart.setScaleEnabled(false)
        chart.setNoDataText("No workouts in the last 7 days")
        chart.setNoDataTextColor(textColor)

        chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            valueFormatter = IndexAxisValueFormatter(labels)
            granularity = 1f
            labelCount = labels.size
            this.textColor = textColor
            setDrawGridLines(false)
        }
        chart.axisLeft.apply {
            axisMinimum = 0f
            granularity = 1f
            this.textColor = textColor
        }
    }

    private fun confirmDelete(workout: WorkoutEntity) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete workout?")
            .setMessage("This ${workout.exerciseType().displayName} session will be removed permanently.")
            .setPositiveButton("Delete") { _, _ -> viewModel.deleteWorkout(workout.id) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmClear() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Clear all history?")
            .setMessage("Every saved workout will be removed permanently.")
            .setPositiveButton("Clear all") { _, _ -> viewModel.clearHistory() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private companion object {
        const val MAX_LISTED = 50
        val GREEN = Color.parseColor("#2ECC71")
        val BLUE = Color.parseColor("#3498DB")
    }
}