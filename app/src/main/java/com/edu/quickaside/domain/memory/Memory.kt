package com.edu.quickaside.domain.memory

import com.edu.quickaside.domain.common.CaptureId
import com.edu.quickaside.domain.common.NoteId
import com.edu.quickaside.domain.common.StructuredLogId
import java.time.Instant

data class Note(
    val id: NoteId,
    val text: String,
    val sourceCaptureId: CaptureId? = null,
    val createdAt: Instant,
) {
    init {
        require(text.isNotBlank()) { "Note text must not be blank" }
    }
}

data class StructuredLog(
    val id: StructuredLogId,
    val fields: Map<String, String> = emptyMap(),
    val sourceCaptureId: CaptureId? = null,
    val createdAt: Instant,
) {
    init {
        require(fields.isNotEmpty()) { "Structured log must contain at least one field" }
        require(fields.keys.none(String::isBlank)) { "Structured log field keys must not be blank" }
        require(fields.values.none(String::isBlank)) { "Structured log field values must not be blank" }
    }
}
