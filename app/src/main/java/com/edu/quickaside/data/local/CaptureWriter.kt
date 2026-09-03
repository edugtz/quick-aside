package com.edu.quickaside.data.local

import com.edu.quickaside.domain.capture.Capture

fun interface CaptureWriter {
    suspend fun save(capture: Capture)
}

class RoomCaptureWriter(
    private val database: QuickAsideDatabase,
) : CaptureWriter {
    override suspend fun save(capture: Capture) {
        database.captureDao().insert(capture.toEntity())
    }
}
