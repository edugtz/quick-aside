package com.edu.quickaside

import com.edu.quickaside.application.speech.MicrophonePermissionController
import com.edu.quickaside.application.speech.SpeechTranscriber
import com.edu.quickaside.application.speech.SpeechTranscriberEvent
import com.edu.quickaside.application.speech.SpeechTranscriberFactory
import com.edu.quickaside.application.speech.SpeechTranscriberListener

internal class FakeMicrophonePermissionController(
    private val granted: Boolean,
    private val requestResult: Boolean = granted,
    private val rationale: Boolean = false,
) : MicrophonePermissionController {
    var requestCount: Int = 0
        private set

    override fun isGranted(): Boolean = granted

    override fun shouldShowRationale(): Boolean = rationale

    override fun request(onResult: (Boolean) -> Unit) {
        requestCount += 1
        onResult(requestResult)
    }
}

internal class FakeSpeechTranscriberFactory : SpeechTranscriberFactory {
    val transcribers = mutableListOf<FakeSpeechTranscriber>()

    override fun create(): SpeechTranscriber = FakeSpeechTranscriber().also {
        transcribers += it
    }

    fun latest(): FakeSpeechTranscriber = checkNotNull(transcribers.lastOrNull()) {
        "No fake transcriber has been created"
    }
}

internal class FakeSpeechTranscriber : SpeechTranscriber {
    private var listener: SpeechTranscriberListener? = null

    var startCount: Int = 0
        private set
    var cancelCount: Int = 0
        private set
    var destroyCount: Int = 0
        private set

    override fun start(listener: SpeechTranscriberListener) {
        this.listener = listener
        startCount += 1
        listener.onEvent(SpeechTranscriberEvent.Ready)
    }

    override fun cancel() {
        cancelCount += 1
    }

    override fun destroy() {
        destroyCount += 1
    }

    fun emitPartial(transcript: String) {
        checkNotNull(listener).onEvent(SpeechTranscriberEvent.PartialTranscript(transcript))
    }

    fun emitFinal(transcript: String) {
        checkNotNull(listener).onEvent(SpeechTranscriberEvent.FinalTranscript(transcript))
    }

    fun emitError(reason: com.edu.quickaside.application.speech.SpeechTranscriberError) {
        checkNotNull(listener).onEvent(SpeechTranscriberEvent.Error(reason))
    }
}
