package com.edu.quickaside.application.speech

enum class SpeechTranscriberError {
    NoMatch,
    Timeout,
    PermissionDenied,
    Busy,
    NetworkFailure,
    ServiceFailure,
    Unavailable,
}

sealed interface SpeechTranscriberEvent {
    data object Ready : SpeechTranscriberEvent

    data object Listening : SpeechTranscriberEvent

    data object Finalizing : SpeechTranscriberEvent

    data class PartialTranscript(
        val transcript: String,
    ) : SpeechTranscriberEvent

    data class FinalTranscript(
        val transcript: String,
    ) : SpeechTranscriberEvent

    data class Error(
        val reason: SpeechTranscriberError,
    ) : SpeechTranscriberEvent
}

fun interface SpeechTranscriberListener {
    fun onEvent(event: SpeechTranscriberEvent)
}

interface SpeechTranscriber {
    fun start(listener: SpeechTranscriberListener)

    fun cancel()

    fun destroy()
}

fun interface SpeechTranscriberFactory {
    fun create(): SpeechTranscriber
}
