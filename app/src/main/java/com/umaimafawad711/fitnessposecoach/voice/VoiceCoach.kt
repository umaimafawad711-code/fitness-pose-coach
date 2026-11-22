package com.umaimafawad711.fitnessposecoach.voice

import android.content.Context
import android.media.AudioAttributes
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.util.Log
import com.umaimafawad711.fitnessposecoach.analysis.Feedback
import com.umaimafawad711.fitnessposecoach.analysis.Severity
import java.util.Locale

/** Create on the main thread. speakFeedback/announceRep must be called from one thread only. */
class VoiceCoach(context: Context) {

    private var tts: TextToSpeech? = null

    @Volatile
    private var ready = false

    @Volatile
    var enabled = true

    private val lastSpokenAt = HashMap<String, Long>()
    private var lastFeedbackAt = 0L

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            val engine = tts
            if (status != TextToSpeech.SUCCESS || engine == null) {
                Log.w(TAG, "TTS init failed: $status")
                return@TextToSpeech
            }
            val result = engine.setLanguage(Locale.US)
            engine.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            // Missing voice data: stay silent, on-screen text still works.
            ready = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    /** Rep counts are always queued so none is lost behind a warning. */
    fun announceRep(count: Int) {
        if (!enabled || !ready) return
        tts?.speak(count.toString(), TextToSpeech.QUEUE_ADD, null, "rep$count")
    }

    fun speakFeedback(feedback: Feedback) {
        if (!enabled || !ready || feedback.severity == Severity.INFO) return
        val engine = tts ?: return

        val now = SystemClock.elapsedRealtime()
        if (now - lastFeedbackAt < MIN_GAP_MS) return
        val last = lastSpokenAt[feedback.message]
        if (last != null && now - last < SAME_MESSAGE_COOLDOWN_MS) return

        // Warnings are dropped, not queued: stale advice spoken late is worse than none.
        if (engine.isSpeaking) return

        lastFeedbackAt = now
        lastSpokenAt[feedback.message] = now
        engine.speak(feedback.message, TextToSpeech.QUEUE_ADD, null, "fb")
    }

    fun shutdown() {
        ready = false
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    private companion object {
        const val TAG = "VoiceCoach"
        const val MIN_GAP_MS = 2_500L
        const val SAME_MESSAGE_COOLDOWN_MS = 8_000L
    }
}