package com.edu.quickaside.application.capture

import com.edu.quickaside.domain.capture.Capture

fun interface CaptureReader {
    suspend fun readRecent(): List<Capture>
}
