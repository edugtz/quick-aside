package com.edu.quickaside.data.local

import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import com.edu.quickaside.application.memory.MemoryClock
import com.edu.quickaside.application.memory.MemoryIdProvider
import com.edu.quickaside.application.memory.MemoryStore
import com.edu.quickaside.application.memory.NoteCreationResult
import com.edu.quickaside.application.memory.RandomMemoryIdProvider
import com.edu.quickaside.application.memory.StructuredLogCreationResult
import com.edu.quickaside.domain.common.CaptureId
import com.edu.quickaside.domain.common.NoteId
import com.edu.quickaside.domain.common.StructuredLogId
import com.edu.quickaside.domain.memory.Note
import com.edu.quickaside.domain.memory.StructuredLog
import java.time.Instant
import kotlinx.coroutines.CancellationException

class RoomMemoryStore(
    private val database: QuickAsideDatabase,
    private val idProvider: MemoryIdProvider = RandomMemoryIdProvider(),
    private val clock: MemoryClock = MemoryClock { Instant.now() },
) : MemoryStore {
    override suspend fun createNote(
        text: String,
        sourceCaptureId: CaptureId?,
    ): NoteCreationResult {
        if (text.isBlank()) {
            return NoteCreationResult.BlankText
        }

        return try {
            database.withWriteTransaction {
                if (sourceCaptureId != null && database.captureDao().getById(sourceCaptureId.value) == null) {
                    return@withWriteTransaction NoteCreationResult.MissingSourceCapture
                }
                val note = Note(
                    id = idProvider.nextNoteId(),
                    text = text,
                    sourceCaptureId = sourceCaptureId,
                    createdAt = clock.now(),
                )
                database.noteDao().insert(note.toEntity())
                NoteCreationResult.Saved(note)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            NoteCreationResult.Failed(failure)
        }
    }

    override suspend fun readRecentNotes(limit: Int): List<Note> = database.withReadTransaction {
        database.noteDao().getRecent(limit).map(NoteEntity::toDomain)
    }

    override suspend fun getNote(id: NoteId): Note? = database.withReadTransaction {
        database.noteDao().getById(id.value)?.toDomain()
    }

    override suspend fun createStructuredLog(
        fields: Map<String, String>,
        sourceCaptureId: CaptureId?,
    ): StructuredLogCreationResult {
        if (fields.isEmpty()) {
            return StructuredLogCreationResult.EmptyFields
        }
        if (fields.keys.any(String::isBlank)) {
            return StructuredLogCreationResult.BlankFieldKey
        }
        if (fields.values.any(String::isBlank)) {
            return StructuredLogCreationResult.BlankFieldValue
        }

        return try {
            database.withWriteTransaction {
                if (sourceCaptureId != null && database.captureDao().getById(sourceCaptureId.value) == null) {
                    return@withWriteTransaction StructuredLogCreationResult.MissingSourceCapture
                }
                val log = StructuredLog(
                    id = idProvider.nextStructuredLogId(),
                    fields = fields,
                    sourceCaptureId = sourceCaptureId,
                    createdAt = clock.now(),
                )
                database.structuredLogDao().insert(log.toEntity())
                database.structuredLogFieldDao().insertAll(log.toFieldEntities())
                StructuredLogCreationResult.Saved(log)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            StructuredLogCreationResult.Failed(failure)
        }
    }

    override suspend fun readRecentStructuredLogs(limit: Int): List<StructuredLog> =
        database.withReadTransaction {
            database.structuredLogDao().getRecent(limit).map { entity ->
                entity.toDomain(
                    database.structuredLogFieldDao().getByStructuredLogId(entity.id),
                )
            }
        }

    override suspend fun getStructuredLog(id: StructuredLogId): StructuredLog? =
        database.withReadTransaction {
            val entity = database.structuredLogDao().getById(id.value)
                ?: return@withReadTransaction null
            entity.toDomain(database.structuredLogFieldDao().getByStructuredLogId(entity.id))
        }
}
