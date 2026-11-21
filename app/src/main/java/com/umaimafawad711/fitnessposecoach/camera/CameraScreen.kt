package com.umaimafawad711.fitnessposecoach.camera

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
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
import com.umaimafawad711.fitnessposecoach.R
import com.umaimafawad711.fitnessposecoach.databinding.FragmentCameraBinding
import com.umaimafawad711.fitnessposecoach.pose.PoseEstimator
import com.umaimafawad711.fitnessposecoach.pose.PoseResult
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import com.umaimafawad711.fitnessposecoach.analysis.ExerciseStateMachine
import com.umaimafawad711.fitnessposecoach.analysis.ExerciseType

class CameraScreen :
    Fragment(R.layout.fragment_camera) {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = checkNotNull(_binding)

    private lateinit var appContext: Context
    private lateinit var analysisExecutor: ExecutorService

    // Created and used only on analysisExecutor's thread (TFLite Interpreter is not thread-safe).
    private var poseEstimator: PoseEstimator? = null

    // Added State Machine (Hardcoded to SQUAT for now)
    private val stateMachine = ExerciseStateMachine(ExerciseType.SQUAT)

    private var cameraProvider: ProcessCameraProvider? = null
    private var lensFacing = CameraSelector.LENS_FACING_FRONT
    private var permissionRequested = false

    // FPS counters: analysis thread only.
    private var frameCount = 0
    private var fps = 0
    private var windowStartMs = SystemClock.elapsedRealtime()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            _binding?.permissionGroup?.isVisible = !granted
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentCameraBinding.bind(view)
        appContext = requireContext().applicationContext
        analysisExecutor = Executors.newSingleThreadExecutor()

        binding.btnFlip.setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                CameraSelector.LENS_FACING_BACK
            } else {
                CameraSelector.LENS_FACING_FRONT
            }
            bindUseCases()
        }

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

        // Added: Update Overlay mirror based on front/back camera
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

    // Runs on analysisExecutor.
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

    // Replaced: Updated handleResult to include State Machine logic and Overlay updates
    private fun handleResult(result: PoseResult) {
        val snapshot = stateMachine.update(result, SystemClock.elapsedRealtime())
        _binding?.overlay?.update(result)

        frameCount++
        val now = SystemClock.elapsedRealtime()
        if (now - windowStartMs >= 1000) {
            fps = frameCount
            frameCount = 0
            windowStartMs = now
        }
        val angleText = snapshot.angle?.let { "${it.toInt()}°" } ?: "--"
        val text = "FPS $fps | ${result.latencyMs} ms | ${stateMachine.exercise.displayName}" +
                " | angle $angleText | ${snapshot.state} | reps ${snapshot.reps}"
        _binding?.root?.post { _binding?.tvDebug?.text = text }
    }

    override fun onDestroyView() {
        cameraProvider?.unbindAll()
        cameraProvider = null
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
    }
}