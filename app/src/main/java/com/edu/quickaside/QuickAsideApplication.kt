package com.edu.quickaside

import android.app.Application
import com.edu.quickaside.application.capture.CaptureSubmission
import com.edu.quickaside.data.local.CaptureWriter
import com.edu.quickaside.data.local.QuickAsideDatabase
import com.edu.quickaside.data.local.RoomCaptureWriter

class QuickAsideApplication : Application() {
    val database: QuickAsideDatabase by lazy {
        QuickAsideDatabase.create(this)
    }

    val captureWriter: CaptureWriter by lazy {
        RoomCaptureWriter(database)
    }

    val captureSubmission: CaptureSubmission by lazy {
        CaptureSubmission(captureWriter)
    }
}
