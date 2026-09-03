package com.edu.quickaside.domain.memory

import com.edu.quickaside.domain.common.CaptureId
import com.edu.quickaside.domain.common.NoteId
import com.edu.quickaside.domain.common.StructuredLogId

data class Note(
    val id: NoteId,
    val text: String,
    val sourceCaptureId: CaptureId? = null,
)

data class StructuredLog(
    val id: StructuredLogId,
    val fields: Map<String, String> = emptyMap(),
    val sourceCaptureId: CaptureId? = null,
)

