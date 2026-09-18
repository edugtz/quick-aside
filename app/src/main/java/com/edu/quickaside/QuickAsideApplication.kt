package com.edu.quickaside

import android.app.Application
import com.edu.quickaside.application.actions.ActionLedgerStore
import com.edu.quickaside.application.capture.AIProvider
import com.edu.quickaside.application.capture.CaptureInterpreter
import com.edu.quickaside.application.capture.CapturePlanValidator
import com.edu.quickaside.application.capture.CaptureReader
import com.edu.quickaside.application.capture.CaptureSubmission
import com.edu.quickaside.application.capture.CaptureTranscriptCorrector
import com.edu.quickaside.application.capture.ProviderCaptureInterpreter
import com.edu.quickaside.application.gateway.DevicePairer
import com.edu.quickaside.application.lists.ListStore
import com.edu.quickaside.application.lists.ReversibleListItemActions
import com.edu.quickaside.application.memory.MemoryStore
import com.edu.quickaside.application.search.LocalSearch
import com.edu.quickaside.application.speech.AndroidSpeechTranscriberFactory
import com.edu.quickaside.application.speech.SpeechTranscriberFactory
import com.edu.quickaside.application.tasks.ReversibleTaskActions
import com.edu.quickaside.application.tasks.TaskStore
import com.edu.quickaside.data.local.CaptureWriter
import com.edu.quickaside.data.local.QuickAsideDatabase
import com.edu.quickaside.data.local.RoomActionLedgerStore
import com.edu.quickaside.data.local.RoomCaptureReader
import com.edu.quickaside.data.local.RoomCaptureTranscriptCorrector
import com.edu.quickaside.data.local.RoomCaptureWriter
import com.edu.quickaside.data.local.RoomListStore
import com.edu.quickaside.data.local.RoomLocalSearch
import com.edu.quickaside.data.local.RoomMemoryStore
import com.edu.quickaside.data.local.RoomReversibleListItemActions
import com.edu.quickaside.data.local.RoomReversibleTaskActions
import com.edu.quickaside.data.local.RoomTaskStore
import com.edu.quickaside.data.remote.gateway.AndroidKeystoreQa1DeviceIdentity
import com.edu.quickaside.data.remote.gateway.HttpsUrlConnectionGatewayTransport
import com.edu.quickaside.data.remote.gateway.Qa1RequestAuthenticator
import com.edu.quickaside.data.remote.gateway.QuickAsideGatewayAIProvider
import com.edu.quickaside.data.remote.gateway.QuickAsideGatewayPairer

class QuickAsideApplication : Application() {
    val database: QuickAsideDatabase by lazy {
        QuickAsideDatabase.create(this)
    }

    val captureWriter: CaptureWriter by lazy {
        RoomCaptureWriter(database)
    }

    val captureReader: CaptureReader by lazy {
        RoomCaptureReader(database)
    }

    private val gatewayTransport by lazy {
        HttpsUrlConnectionGatewayTransport()
    }

    private val qa1Identity by lazy {
        AndroidKeystoreQa1DeviceIdentity()
    }

    val aiProvider: AIProvider by lazy {
        QuickAsideGatewayAIProvider(
            transport = gatewayTransport,
            authenticator = Qa1RequestAuthenticator(qa1Identity),
        )
    }

    val captureInterpreter: CaptureInterpreter by lazy {
        ProviderCaptureInterpreter(
            provider = aiProvider,
            validator = CapturePlanValidator(),
        )
    }

    val devicePairer: DevicePairer by lazy {
        QuickAsideGatewayPairer(
            transport = gatewayTransport,
            identity = qa1Identity,
        )
    }

    val captureSubmission: CaptureSubmission by lazy {
        CaptureSubmission(
            writer = captureWriter,
            interpreter = captureInterpreter,
        )
    }

    val captureTranscriptCorrector: CaptureTranscriptCorrector by lazy {
        RoomCaptureTranscriptCorrector(database.captureDao())
    }

    val listStore: ListStore by lazy {
        RoomListStore(database)
    }

    val reversibleListItemActions: ReversibleListItemActions by lazy {
        RoomReversibleListItemActions(database)
    }

    val memoryStore: MemoryStore by lazy {
        RoomMemoryStore(database)
    }

    val actionLedgerStore: ActionLedgerStore by lazy {
        RoomActionLedgerStore(database)
    }

    val taskStore: TaskStore by lazy {
        RoomTaskStore(database)
    }

    val reversibleTaskActions: ReversibleTaskActions by lazy {
        RoomReversibleTaskActions(database)
    }

    val localSearch: LocalSearch by lazy {
        RoomLocalSearch(database)
    }

    val speechTranscriberFactory: SpeechTranscriberFactory by lazy {
        AndroidSpeechTranscriberFactory(this)
    }
}
