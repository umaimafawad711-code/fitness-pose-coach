package com.umaimafawad711.fitnessposecoach.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.umaimafawad711.fitnessposecoach.data.local.WorkoutEntity
import com.umaimafawad711.fitnessposecoach.data.local.exerciseType
import com.umaimafawad711.fitnessposecoach.databinding.ItemWorkoutBinding
import com.umaimafawad711.fitnessposecoach.util.formatDuration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Top level (not nested) so the adapter's super constructor can reference it without visibility issues.
private object WorkoutDiff : DiffUtil.ItemCallback<WorkoutEntity>() {
    override fun areItemsTheSame(old: WorkoutEntity, new: WorkoutEntity) = old.id == new.id
    override fun areContentsTheSame(old: WorkoutEntity, new: WorkoutEntity) = old == new
}

// SimpleDateFormat is not thread-safe; the adapter only binds on the main thread.
private val DATE_FORMAT = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())

class WorkoutAdapter(
    private val onDelete: (WorkoutEntity) -> Unit
) : ListAdapter<WorkoutEntity, WorkoutAdapter.ViewHolder>(WorkoutDiff) {

    class ViewHolder(val binding: ItemWorkoutBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemWorkoutBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val workout = getItem(position)
        holder.binding.tvTitle.text = "${workout.exerciseType().displayName} · ${workout.reps} reps"
        holder.binding.tvDetails.text = "${DATE_FORMAT.format(Date(workout.timestamp))}" +
                " · ${formatDuration(workout.durationSec)} · form ${workout.formScore}"
        holder.binding.btnDelete.setOnClickListener { onDelete(workout) }
    }
}