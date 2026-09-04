package com.edu.quickaside.application.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.MainThread
import java.util.Locale

class AndroidSpeechTranscriberFactory(
    context: Context,
) : SpeechTranscriberFactory {
    private val applicationContext = context.applicationContext

    override fun create(): SpeechTranscriber = AndroidSpeechTranscriber(applicationContext)
}

@MainThread
class AndroidSpeechTranscriber(
    private val context: Context,
    private val localeProvider: () -> Locale = Locale::getDefault,
) : SpeechTranscriber {
    private var speechRecognizer: SpeechRecognizer? = null
    private var listener: SpeechTranscriberListener? = null
    private var cancelled = false
    private var destroyed = false
    private var usingOnDeviceRecognizer = false
    private var normalFallbackAttempted = false

    override fun start(listener: SpeechTranscriberListener) {
        if (destroyed || speechRecognizer != null) {
            return
        }

        this.listener = listener
        cancelled = false
        normalFallbackAttempted = false

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            emit(SpeechTranscriberEvent.Error(SpeechTranscriberError.Unavailable))
            return
        }

        val recognizer = try {
            createRecognizer()
        } catch (_: RuntimeException) {
            emit(SpeechTranscriberEvent.Error(SpeechTranscriberError.Unavailable))
            return
        }
        speechRecognizer = recognizer

        try {
            recognizer.setRecognitionListener(recognitionListener)
            recognizer.startListening(createRecognitionIntent())
        } catch (_: SecurityException) {
            emit(SpeechTranscriberEvent.Error(SpeechTranscriberError.PermissionDenied))
        } catch (_: RuntimeException) {
            emit(SpeechTranscriberEvent.Error(SpeechTranscriberError.ServiceFailure))
        }
    }

    override fun cancel() {
        if (destroyed) {
            return
        }

        cancelled = true
        speechRecognizer?.cancel()
    }

    override fun destroy() {
        if (destroyed) {
            return
        }

        destroyed = true
        cancelled = true
        speechRecognizer?.destroy()
        speechRecognizer = null
        listener = null
    }

    private fun createRecognizer(): SpeechRecognizer {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        ) {
            try {
                usingOnDeviceRecognizer = true
                return SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } catch (_: RuntimeException) {
                // A service can report on-device availability and still fail
                // to create a session. Preserve the normal system fallback.
            }
        }
        usingOnDeviceRecognizer = false
        return SpeechRecognizer.createSpeechRecognizer(context)
    }

    private fun restartWithNormalRecognizerIfLanguageUnsupported(error: Int): Boolean {
        if (
            !usingOnDeviceRecognizer ||
            normalFallbackAttempted ||
            error != SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED &&
            error != SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE
        ) {
            return false
        }

        normalFallbackAttempted = true
        val onDeviceRecognizer = speechRecognizer
        speechRecognizer = null
        onDeviceRecognizer?.destroy()

        return try {
            usingOnDeviceRecognizer = false
            val normalRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer = normalRecognizer
            normalRecognizer.setRecognitionListener(recognitionListener)
            normalRecognizer.startListening(createRecognitionIntent())
            true
        } catch (_: SecurityException) {
            emit(SpeechTranscriberEvent.Error(SpeechTranscriberError.PermissionDenied))
            true
        } catch (_: RuntimeException) {
            emit(SpeechTranscriberEvent.Error(SpeechTranscriberError.Unavailable))
            true
        }
    }

    private fun createRecognitionIntent(): Intent = Intent(
        RecognizerIntent.ACTION_RECOGNIZE_SPEECH,
    ).apply {
        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
        )
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeProvider().toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
    }

    private fun emit(event: SpeechTranscriberEvent) {
        if (!cancelled && !destroyed) {
            listener?.onEvent(event)
        }
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            emit(SpeechTranscriberEvent.Ready)
        }

        override fun onBeginningOfSpeech() {
            emit(SpeechTranscriberEvent.Listening)
        }

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            emit(SpeechTranscriberEvent.Finalizing)
        }

        override fun onError(error: Int) {
            if (restartWithNormalRecognizerIfLanguageUnsupported(error)) {
                return
            }
            emit(SpeechTranscriberEvent.Error(error.toSpeechTranscriberError()))
        }

        override fun onResults(results: Bundle?) {
            val transcript = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            emit(SpeechTranscriberEvent.FinalTranscript(transcript))
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val transcript = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            if (!transcript.isNullOrBlank()) {
                emit(SpeechTranscriberEvent.PartialTranscript(transcript))
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }
}

private fun Int.toSpeechTranscriberError(): SpeechTranscriberError = when (this) {
    SpeechRecognizer.ERROR_NO_MATCH -> SpeechTranscriberError.NoMatch
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechTranscriberError.Timeout
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechTranscriberError.PermissionDenied
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
    SpeechRecognizer.ERROR_TOO_MANY_REQUESTS,
    -> SpeechTranscriberError.Busy

    SpeechRecognizer.ERROR_NETWORK,
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
    -> SpeechTranscriberError.NetworkFailure

    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
    -> SpeechTranscriberError.Unavailable

    else -> SpeechTranscriberError.ServiceFailure
}
