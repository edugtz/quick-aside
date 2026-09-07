package com.edu.quickaside

import android.app.Application
import com.edu.quickaside.application.capture.CaptureReader
import com.edu.quickaside.application.capture.CaptureSubmission
import com.edu.quickaside.application.capture.CaptureTranscriptCorrector
import com.edu.quickaside.application.lists.ListStore
import com.edu.quickaside.application.memory.MemoryStore
import com.edu.quickaside.application.search.LocalSearch
import com.edu.quickaside.application.speech.AndroidSpeechTranscriberFactory
import com.edu.quickaside.application.speech.SpeechTranscriberFactory
import com.edu.quickaside.data.local.CaptureWriter
import com.edu.quickaside.data.local.QuickAsideDatabase
import com.edu.quickaside.data.local.RoomCaptureReader
import com.edu.quickaside.data.local.RoomCaptureTranscriptCorrector
import com.edu.quickaside.data.local.RoomCaptureWriter
import com.edu.quickaside.data.local.RoomListStore
import com.edu.quickaside.data.local.RoomMemoryStore
import com.edu.quickaside.data.local.RoomLocalSearch

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

    val captureSubmission: CaptureSubmission by lazy {
        CaptureSubmission(captureWriter)
    }

    val captureTranscriptCorrector: CaptureTranscriptCorrector by lazy {
        RoomCaptureTranscriptCorrector(database.captureDao())
    }

    val listStore: ListStore by lazy {
        RoomListStore(database)
    }

    val memoryStore: MemoryStore by lazy {
        RoomMemoryStore(database)
    }

    val localSearch: LocalSearch by lazy {
        RoomLocalSearch(database)
    }

    val speechTranscriberFactory: SpeechTranscriberFactory by lazy {
        AndroidSpeechTranscriberFactory(this)
    }
}
