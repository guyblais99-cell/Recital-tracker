package com.recital.scavengerhunt.util

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class SpeechHelper(context: Context) {
    private var tts: TextToSpeech? = null
    private val ready = AtomicBoolean(false)

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setSpeechRate(0.92f)
                ready.set(true)
            }
        }
    }

    fun speak(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        val engine = tts ?: return
        engine.stop()
        engine.speak(t, TextToSpeech.QUEUE_FLUSH, null, "hunt-clue")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready.set(false)
    }
}
