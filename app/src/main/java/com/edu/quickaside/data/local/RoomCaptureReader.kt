package com.edu.quickaside.data.local

import com.edu.quickaside.application.capture.CaptureReader
import com.edu.quickaside.domain.capture.Capture

class RoomCaptureReader(
    private val database: QuickAsideDatabase,
    private val recentLimit: Int = RECENT_CAPTURE_LIMIT,
) : CaptureReader {
    override suspend fun readRecent(): List<Capture> = database
        .captureDao()
        .getRecent(recentLimit)
        .map(CaptureEntity::toDomain)

    companion object {
        const val RECENT_CAPTURE_LIMIT = 50
    }
}
