package com.umaimafawad711.fitnessposecoach.camera

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.util.Size
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.umaimafawad711.fitnessposecoach.R
import com.umaimafawad711.fitnessposecoach.analysis.ExerciseStateMachine
import com.umaimafawad711.fitnessposecoach.analysis.ExerciseType
import com.umaimafawad711.fitnessposecoach.analysis.Feedback
import com.umaimafawad711.fitnessposecoach.analysis.FormScoreTracker
import com.umaimafawad711.fitnessposecoach.analysis.FormValidator
import com.umaimafawad711.fitnessposecoach.analysis.Severity
import com.umaimafawad711.fitnessposecoach.data.SessionResult
import com.umaimafawad711.fitnessposecoach.databinding.FragmentCameraBinding
import com.umaimafawad711.fitnessposecoach.pose.PoseEstimator
import com.umaimafawad711.fitnessposecoach.pose.PoseResult
import com.umaimafawad711.fitnessposecoach.ui.WorkoutViewModel
import com.umaimafawad711.fitnessposecoach.util.formatDuration
import com.umaimafawad711.fitnessposecoach.voice.VoiceCoach
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraScreen : Fragment(R.layout.fragment_camera) {

    private enum class SessionState { READY, RUNNING, PAUSED, FINISHED }

    private val viewModel: WorkoutViewModel by activityViewModels()

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = checkNotNull(_binding)

    private lateinit var appContext: Context
    private lateinit var analysisExecutor: ExecutorService
    private val mainHandler = Handler(Looper.getMainLooper())

    // Assigned on the main thread before the analyzer is attached; used on the analysis thread only.
    private lateinit var exercise: ExerciseType
    private lateinit var stateMachine: ExerciseStateMachine
    private lateinit var validator: FormValidator
    private val scoreTracker = FormScoreTracker()
    private var stickyFeedback: Feedback? = null
    private var stickyUntilMs = 0L
    private var poseEstimator: PoseEstimator? = null // analysis thread only
    private var voiceCoach: VoiceCoach? = null

    // Session clock: main thread only. sessionState is also read by the analysis thread.
    @Volatile
    private var sessionState = SessionState.READY
    private var startedAtMs = 0L
    private var accumulatedMs = 0L
    private var runningSinceMs = 0L

    private var cameraProvider: ProcessCameraProvider? = null
    private var lensFacing = CameraSelector.LENS_FACING_FRONT
    private var permissionRequested = false

    // FPS counters: analysis thread only.
    private var frameCount = 0
    private var fps = 0
    private var windowStartMs = SystemClock.elapsedRealtime()

    private val ticker = object : Runnable {
        override fun run() {
            _binding?.tvTimer?.text = formatDuration((elapsedMs() / 1000).toInt())
            mainHandler.postDelayed(this, 500)
        }
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            _binding?.permissionGroup?.isVisible = !granted
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentCameraBinding.bind(view)
        appContext = requireContext().applicationContext
        analysisExecutor = Executors.newSingleThreadExecutor()
        voiceCoach = VoiceCoach(appContext)

        exercise = viewModel.selectedExercise
        stateMachine = ExerciseStateMachine(exercise)
        validator = FormValidator(exercise)

        sessionState = SessionState.READY
        accumulatedMs = 0L
        updateButtons()
        mainHandler.post(ticker)

        binding.btnFlip.setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                CameraSelector.LENS_FACING_BACK
            } else {
                CameraSelector.LENS_FACING_FRONT
            }
            bindUseCases()
        }

        binding.btnStartPause.setOnClickListener {
            when (sessionState) {
                SessionState.READY -> {
                    startedAtMs = System.currentTimeMillis()
                    resumeClock()
                }
                SessionState.RUNNING -> pauseClock()
                SessionState.PAUSED -> resumeClock()
                SessionState.FINISHED -> Unit
            }
        }

        binding.btnFinish.setOnClickListener { finishSession() }

        binding.btnGrant.setOnClickListener {
            if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            } else {
                // "Don't ask again" state: only system settings can grant it now.
                startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", requireContext().packageName, null)
                    )
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        when {
            hasCameraPermission() -> {
                binding.permissionGroup.isVisible = false
                if (cameraProvider == null) startCamera()
            }
            !permissionRequested -> {
                permissionRequested = true
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
            else -> binding.permissionGroup.isVisible = true
        }
    }

    override fun onPause() {
        // Never keep counting reps while the app is in the background.
        if (sessionState == SessionState.RUNNING) pauseClock()
        super.onPause()
    }

    // ---- Session control (main thread) ----

    private fun elapsedMs(): Long =
        accumulatedMs + if (sessionState == SessionState.RUNNING) {
            SystemClock.elapsedRealtime() - runningSinceMs
        } else {
            0L
        }

    private fun resumeClock() {
        runningSinceMs = SystemClock.elapsedRealtime()
        sessionState = SessionState.RUNNING
        updateButtons()
    }

    private fun pauseClock() {
        accumulatedMs += SystemClock.elapsedRealtime() - runningSinceMs
        sessionState = SessionState.PAUSED
        updateButtons()
    }

    private fun updateButtons() {
        _binding?.btnStartPause?.text = when (sessionState) {
            SessionState.READY -> "Start"
            SessionState.RUNNING -> "Pause"
            SessionState.PAUSED -> "Resume"
            SessionState.FINISHED -> "Saving…"
        }
        val active = sessionState != SessionState.FINISHED
        _binding?.btnStartPause?.isEnabled = active
        _binding?.btnFinish?.isEnabled = active
    }

    private fun finishSession() {
        when (sessionState) {
            SessionState.FINISHED -> return
            SessionState.READY -> {
                findNavController().popBackStack() // nothing was recorded
                return
            }
            SessionState.RUNNING -> pauseClock()
            SessionState.PAUSED -> Unit
        }
        sessionState = SessionState.FINISHED
        updateButtons()

        val sharedViewModel = viewModel // captured now: the fragment may be detached by the time the callback runs
        val exerciseType = exercise
        val started = startedAtMs
        val durationSec = (accumulatedMs / 1000).toInt()

        // reps/score are written on the analysis thread, so read them there to avoid half-updated values.
        analysisExecutor.execute {
            val result = SessionResult(
                exercise = exerciseType,
                startedAtMs = started,
                durationSec = durationSec,
                reps = stateMachine.reps,
                formScore = scoreTracker.averageScore
            )
            mainHandler.post {
                sharedViewModel.finishSession(result) // saved even if the user already left this screen
                if (_binding != null) {
                    findNavController().navigate(R.id.action_camera_to_summary)
                }
            }
        }
    }

    // ---- Camera ----

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(requireContext())
        future.addListener({
            if (_binding == null) return@addListener // view destroyed before provider was ready
            cameraProvider = future.get()
            bindUseCases()
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun bindUseCases() {
        val provider = cameraProvider ?: return
        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        val aspect = AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY

        val preview = Preview.Builder()
            .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(aspect).build())
            .build()
            .also { it.setSurfaceProvider(binding.previewView.surfaceProvider) }

        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(aspect)
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(640, 480),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                        )
                    )
                    .build()
            )
            // Drop stale frames instead of queueing them: keeps latency low when inference is slow.
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also { it.setAnalyzer(analysisExecutor) { image -> analyze(image) } }

        binding.overlay.mirrored = lensFacing == CameraSelector.LENS_FACING_FRONT

        try {
            require(provider.hasCamera(selector)) { "No camera for lens facing $lensFacing" }
            provider.unbindAll()
            // Bound to viewLifecycleOwner: camera is released automatically on pause/destroy.
            provider.bindToLifecycle(viewLifecycleOwner, selector, preview, analysis)
        } catch (e: Exception) {
            Log.e(TAG, "Camera bind failed", e)
            Toast.makeText(requireContext(), "Camera not available", Toast.LENGTH_SHORT).show()
        }
    }

    // ---- Analysis (analysisExecutor thread) ----

    private fun analyze(image: ImageProxy) {
        try {
            val estimator = poseEstimator ?: PoseEstimator(appContext).also { poseEstimator = it }
            handleResult(estimator.estimate(image))
        } catch (e: Exception) {
            Log.e(TAG, "Pose estimation failed", e)
        } finally {
            image.close() // must always close, or CameraX stops delivering frames
        }
    }

    private fun handleResult(result: PoseResult) {
        val nowMs = SystemClock.elapsedRealtime()
        val state = sessionState
        val running = state == SessionState.RUNNING
        val live = validator.validate(result)

        // Skeleton and live feedback always show; counting and scoring only while RUNNING.
        val snapshot = if (running) stateMachine.update(result, nowMs) else null
        if (snapshot != null) {
            scoreTracker.onFrame(live)
            if (snapshot.repCompleted) {
                val depthIssue = validator.evaluateRep(snapshot.lastRepMinAngle)
                scoreTracker.onRepCompleted(depthOk = depthIssue == null)
                voiceCoach?.announceRep(snapshot.reps)
                if (depthIssue != null) {
                    stickyFeedback = depthIssue
                    stickyUntilMs = nowMs + STICKY_MS
                }
            }
        }

        val shown = when (state) {
            SessionState.RUNNING -> {
                // A live fault always wins; the depth tip only fills the gaps while it is fresh.
                val sticky = stickyFeedback
                if (live.severity == Severity.INFO && sticky != null && nowMs < stickyUntilMs) sticky else live
            }
            SessionState.READY -> Feedback(MSG_READY, Severity.INFO)
            SessionState.PAUSED -> Feedback(MSG_PAUSED, Severity.INFO)
            SessionState.FINISHED -> Feedback(MSG_SAVING, Severity.INFO)
        }

        if (running) voiceCoach?.speakFeedback(shown)
        _binding?.overlay?.update(result, if (running) live.faultyParts else emptySet())

        frameCount++
        if (nowMs - windowStartMs >= 1000) {
            fps = frameCount
            frameCount = 0
            windowStartMs = nowMs
        }
        val angleText = snapshot?.angle?.let { "${it.toInt()}°" } ?: "--"
        val debugText = "FPS $fps | ${result.latencyMs} ms | ${exercise.displayName}" +
                " | angle $angleText | ${stateMachine.state}"
        val repsText = "Reps ${stateMachine.reps}"
        val feedbackText = shown.message
        val feedbackColor = severityColor(shown.severity)

        _binding?.root?.post {
            _binding?.tvDebug?.text = debugText
            _binding?.tvReps?.text = repsText
            _binding?.tvFeedback?.let {
                it.text = feedbackText
                it.setTextColor(feedbackColor)
            }
        }
    }

    private fun severityColor(severity: Severity): Int = when (severity) {
        Severity.INFO -> Color.parseColor("#2ECC71")
        Severity.WARNING -> Color.parseColor("#F5A623")
        Severity.ERROR -> Color.parseColor("#E74C3C")
    }

    override fun onDestroyView() {
        mainHandler.removeCallbacks(ticker) // only the ticker: a pending save callback must still run
        cameraProvider?.unbindAll()
        cameraProvider = null
        voiceCoach?.shutdown()
        voiceCoach = null
        // Close the estimator on its own thread, after any in-flight frame finishes.
        analysisExecutor.execute {
            poseEstimator?.close()
            poseEstimator = null
        }
        analysisExecutor.shutdown()
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val TAG = "CameraScreen"
        const val STICKY_MS = 2_000L
        const val MSG_READY = "Get in position, then press Start"
        const val MSG_PAUSED = "Paused"
        const val MSG_SAVING = "Saving workout…"
    }
}