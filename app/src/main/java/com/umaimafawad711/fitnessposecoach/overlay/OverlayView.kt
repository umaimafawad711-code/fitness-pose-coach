package com.umaimafawad711.fitnessposecoach.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.umaimafawad711.fitnessposecoach.pose.BodyPart
import com.umaimafawad711.fitnessposecoach.pose.PoseResult

class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private data class Snapshot(val pose: PoseResult?, val faulty: Set<BodyPart>)

    // Written from the analysis thread, read on the UI thread: one volatile immutable holder.
    @Volatile
    private var snapshot = Snapshot(null, emptySet())

    /** Front camera preview is mirrored by PreviewView, keypoints are not, so flip X here. */
    var mirrored: Boolean = true
        set(value) {
            field = value
            postInvalidate()
        }

    private val density = resources.displayMetrics.density

    private val bonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 4f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val jointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    fun update(pose: PoseResult?, faultyParts: Set<BodyPart> = emptySet()) {
        snapshot = Snapshot(pose, faultyParts)
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val (pose, faulty) = snapshot
        if (pose == null) return

        for ((from, to) in BONES) {
            val a = pose[from]
            val b = pose[to]
            if (a.score < PoseResult.MIN_SCORE || b.score < PoseResult.MIN_SCORE) continue
            bonePaint.color = if (from in faulty || to in faulty) RED else GREEN
            canvas.drawLine(px(a.x), a.y * height, px(b.x), b.y * height, bonePaint)
        }

        for (part in BodyPart.entries) {
            if (part.ordinal < BodyPart.LEFT_SHOULDER.ordinal) continue // skip face points
            val k = pose[part]
            if (k.score < PoseResult.MIN_SCORE) continue
            jointPaint.color = if (part in faulty) RED else GREEN
            canvas.drawCircle(px(k.x), k.y * height, 5f * density, jointPaint)
        }
    }

    private fun px(xNorm: Float): Float = (if (mirrored) 1f - xNorm else xNorm) * width

    private companion object {
        val GREEN = Color.parseColor("#2ECC71")
        val RED = Color.parseColor("#E74C3C")

        val BONES = listOf(
            BodyPart.LEFT_SHOULDER to BodyPart.RIGHT_SHOULDER,
            BodyPart.LEFT_SHOULDER to BodyPart.LEFT_ELBOW,
            BodyPart.LEFT_ELBOW to BodyPart.LEFT_WRIST,
            BodyPart.RIGHT_SHOULDER to BodyPart.RIGHT_ELBOW,
            BodyPart.RIGHT_ELBOW to BodyPart.RIGHT_WRIST,
            BodyPart.LEFT_SHOULDER to BodyPart.LEFT_HIP,
            BodyPart.RIGHT_SHOULDER to BodyPart.RIGHT_HIP,
            BodyPart.LEFT_HIP to BodyPart.RIGHT_HIP,
            BodyPart.LEFT_HIP to BodyPart.LEFT_KNEE,
            BodyPart.LEFT_KNEE to BodyPart.LEFT_ANKLE,
            BodyPart.RIGHT_HIP to BodyPart.RIGHT_KNEE,
            BodyPart.RIGHT_KNEE to BodyPart.RIGHT_ANKLE
        )
    }
}