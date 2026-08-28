package com.ridescore.app.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.QUEUE_FLUSH
import android.util.Log
import com.ridescore.app.domain.model.ScreenAnalysis
import com.ridescore.app.domain.settings.RideScoreSettings
import java.util.Locale

/**
 * Optional spoken summary, off by default.
 *
 * Two rules keep it from becoming noise: the same situation is never announced
 * twice, and there is a minimum gap between utterances. A new offer always
 * interrupts an older one (QUEUE_FLUSH) - stale advice is worse than none.
 */
class VoiceAnnouncer(context: Context) {

    private var tts: TextToSpeech? = null
    private var ready = false
    private var lastSignature: String? = null
    private var lastSpokenAt = 0L

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val engine = tts ?: return@TextToSpeech
                val indianEnglish = Locale("en", "IN")
                val result = engine.setLanguage(indianEnglish)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    engine.setLanguage(Locale.US)
                }
                engine.setSpeechRate(1.05f)
                ready = true
            } else {
                Log.w(TAG, "Text to speech unavailable: $status")
            }
        }
    }

    fun announce(analysis: ScreenAnalysis, settings: RideScoreSettings) {
        if (!settings.voiceEnabled || !ready) return

        val signature = VoicePhrases.signatureOf(analysis)
        if (signature == lastSignature) return

        val now = System.currentTimeMillis()
        if (now - lastSpokenAt < settings.voiceMinIntervalMillis) return

        val phrase = VoicePhrases.forAnalysis(analysis) ?: return
        lastSignature = signature
        lastSpokenAt = now
        speak(phrase)
    }

    private fun speak(phrase: String) {
        val engine = tts ?: return
        engine.speak(phrase, QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    /** Used by the settings screen to preview the voice. */
    fun preview() {
        if (ready) speak("Good order, 180 net per hour.")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }

    private companion object {
        const val TAG = "RideScoreVoice"
        const val UTTERANCE_ID = "ridescore"
    }
}
