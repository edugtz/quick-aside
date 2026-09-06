package com.edu.quickaside.application.memory

import com.edu.quickaside.domain.common.CaptureId
import com.edu.quickaside.domain.common.NoteId
import com.edu.quickaside.domain.common.StructuredLogId
import com.edu.quickaside.domain.memory.Note
import com.edu.quickaside.domain.memory.StructuredLog
import java.time.Instant

interface MemoryStore {
    suspend fun createNote(
        text: String,
        sourceCaptureId: CaptureId? = null,
    ): NoteCreationResult

    suspend fun readRecentNotes(limit: Int = RECENT_MEMORY_LIMIT): List<Note>

    suspend fun getNote(id: NoteId): Note?

    suspend fun createStructuredLog(
        fields: Map<String, String>,
        sourceCaptureId: CaptureId? = null,
    ): StructuredLogCreationResult

    suspend fun readRecentStructuredLogs(limit: Int = RECENT_MEMORY_LIMIT): List<StructuredLog>

    suspend fun getStructuredLog(id: StructuredLogId): StructuredLog?
}

fun interface MemoryClock {
    fun now(): Instant
}

interface MemoryIdProvider {
    fun nextNoteId(): NoteId

    fun nextStructuredLogId(): StructuredLogId
}

class RandomMemoryIdProvider : MemoryIdProvider {
    override fun nextNoteId(): NoteId = NoteId(java.util.UUID.randomUUID().toString())

    override fun nextStructuredLogId(): StructuredLogId =
        StructuredLogId(java.util.UUID.randomUUID().toString())
}

sealed interface NoteCreationResult {
    data class Saved(val note: Note) : NoteCreationResult

    data object BlankText : NoteCreationResult

    data object MissingSourceCapture : NoteCreationResult

    data class Failed(val cause: Exception) : NoteCreationResult
}

sealed interface StructuredLogCreationResult {
    data class Saved(val log: StructuredLog) : StructuredLogCreationResult

    data object EmptyFields : StructuredLogCreationResult

    data object BlankFieldKey : StructuredLogCreationResult

    data object BlankFieldValue : StructuredLogCreationResult

    data object MissingSourceCapture : StructuredLogCreationResult

    data class Failed(val cause: Exception) : StructuredLogCreationResult
}

const val RECENT_MEMORY_LIMIT = 50
