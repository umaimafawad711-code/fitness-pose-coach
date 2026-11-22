package com.umaimafawad711.fitnessposecoach.pose


import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.min

/** Not thread-safe: create and call estimate() on the same single thread. */
class PoseEstimator(context: Context) : AutoCloseable {

    private val interpreter: Interpreter
    private val inputWidth: Int
    private val inputHeight: Int
    private val inputType: DataType
    private val inputBuffer: ByteBuffer
    private val pixels: IntArray
    private val letterbox: Bitmap
    private val canvas: Canvas
    private val matrix = Matrix()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val output = Array(1) { Array(1) { Array(BodyPart.entries.size) { FloatArray(3) } } }

    init {
        interpreter = Interpreter(loadModel(context), Interpreter.Options().setNumThreads(4))

        val inputTensor = interpreter.getInputTensor(0)
        inputHeight = inputTensor.shape()[1]
        inputWidth = inputTensor.shape()[2]
        inputType = inputTensor.dataType()

        val outShape = interpreter.getOutputTensor(0).shape()
        require(outShape.contentEquals(intArrayOf(1, 1, 17, 3))) {
            "Unexpected output shape ${outShape.contentToString()}; expected MoveNet single-pose [1,1,17,3]"
        }

        // Different MoveNet builds use uint8, int32 or float32 input (all with 0..255 values).
        val bytesPerValue = when (inputType) {
            DataType.UINT8 -> 1
            DataType.INT32, DataType.FLOAT32 -> 4
            else -> error("Unsupported input type: $inputType")
        }
        inputBuffer = ByteBuffer
            .allocateDirect(inputWidth * inputHeight * 3 * bytesPerValue)
            .order(ByteOrder.nativeOrder())

        pixels = IntArray(inputWidth * inputHeight)
        letterbox = Bitmap.createBitmap(inputWidth, inputHeight, Bitmap.Config.ARGB_8888)
        canvas = Canvas(letterbox)
    }

    fun estimate(image: ImageProxy): PoseResult {
        val startMs = SystemClock.elapsedRealtime()
        val rotation = image.imageInfo.rotationDegrees
        val source = image.toBitmap()

        val swap = rotation == 90 || rotation == 270
        val frameW = if (swap) source.height else source.width
        val frameH = if (swap) source.width else source.height

        // Letterbox (not stretch) so the body is not distorted; keypoints are mapped back below.
        val scale = min(inputWidth.toFloat() / frameW, inputHeight.toFloat() / frameH)
        matrix.reset()
        matrix.postTranslate(-source.width / 2f, -source.height / 2f)
        matrix.postRotate(rotation.toFloat())
        matrix.postScale(scale, scale)
        matrix.postTranslate(inputWidth / 2f, inputHeight / 2f)

        canvas.drawColor(Color.BLACK)
        canvas.drawBitmap(source, matrix, paint)
        source.recycle()

        fillInput()
        interpreter.run(inputBuffer, output)

        val padX = (inputWidth - frameW * scale) / 2f
        val padY = (inputHeight - frameH * scale) / 2f
        val keypoints = BodyPart.entries.map { part ->
            val (yNorm, xNorm, score) = output[0][0][part.ordinal]
            Keypoint(
                part = part,
                x = ((xNorm * inputWidth - padX) / (frameW * scale)).coerceIn(0f, 1f),
                y = ((yNorm * inputHeight - padY) / (frameH * scale)).coerceIn(0f, 1f),
                score = score
            )
        }
        return PoseResult(keypoints, frameW, frameH, SystemClock.elapsedRealtime() - startMs)
    }

    private fun fillInput() {
        letterbox.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)
        inputBuffer.rewind()
        for (p in pixels) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            when (inputType) {
                DataType.UINT8 -> {
                    inputBuffer.put(r.toByte())
                    inputBuffer.put(g.toByte())
                    inputBuffer.put(b.toByte())
                }
                DataType.INT32 -> {
                    inputBuffer.putInt(r)
                    inputBuffer.putInt(g)
                    inputBuffer.putInt(b)
                }
                else -> {
                    inputBuffer.putFloat(r.toFloat())
                    inputBuffer.putFloat(g.toFloat())
                    inputBuffer.putFloat(b.toFloat())
                }
            }
        }
        inputBuffer.rewind()
    }

    override fun close() {
        interpreter.close()
        letterbox.recycle()
    }

    private fun loadModel(context: Context): MappedByteBuffer =
        context.assets.openFd(MODEL_FILE).use { fd ->
            FileInputStream(fd.fileDescriptor).channel.use { channel ->
                channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            }
        }

    companion object {
        private const val MODEL_FILE = "movenet_lightning.tflite"
    }
}
