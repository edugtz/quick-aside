package com.edu.quickaside.data.local

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import com.edu.quickaside.domain.common.CaptureId
import com.edu.quickaside.domain.common.NoteId
import com.edu.quickaside.domain.common.StructuredLogId
import com.edu.quickaside.domain.memory.Note
import com.edu.quickaside.domain.memory.StructuredLog
import java.time.Instant

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = CaptureEntity::class,
            parentColumns = ["id"],
            childColumns = ["source_capture_id"],
            onDelete = ForeignKey.NO_ACTION,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["source_capture_id"]),
    ],
)
data class NoteEntity(
    @PrimaryKey
    val id: String,
    val text: String,
    @ColumnInfo(name = "source_capture_id")
    val sourceCaptureId: String? = null,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,
)

@Entity(
    tableName = "structured_logs",
    foreignKeys = [
        ForeignKey(
            entity = CaptureEntity::class,
            parentColumns = ["id"],
            childColumns = ["source_capture_id"],
            onDelete = ForeignKey.NO_ACTION,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["source_capture_id"]),
    ],
)
data class StructuredLogEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "source_capture_id")
    val sourceCaptureId: String? = null,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,
)

@Entity(
    tableName = "structured_log_fields",
    primaryKeys = ["structured_log_id", "field_key"],
    foreignKeys = [
        ForeignKey(
            entity = StructuredLogEntity::class,
            parentColumns = ["id"],
            childColumns = ["structured_log_id"],
            onDelete = ForeignKey.NO_ACTION,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
)
data class StructuredLogFieldEntity(
    @ColumnInfo(name = "structured_log_id")
    val structuredLogId: String,
    @ColumnInfo(name = "field_key")
    val fieldKey: String,
    @ColumnInfo(name = "field_value")
    val fieldValue: String,
)

fun Note.toEntity(): NoteEntity = NoteEntity(
    id = id.value,
    text = text,
    sourceCaptureId = sourceCaptureId?.value,
    createdAtEpochMillis = createdAt.toEpochMilli(),
)

fun NoteEntity.toDomain(): Note = Note(
    id = NoteId(id),
    text = text,
    sourceCaptureId = sourceCaptureId?.let(::CaptureId),
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
)

fun StructuredLog.toEntity(): StructuredLogEntity = StructuredLogEntity(
    id = id.value,
    sourceCaptureId = sourceCaptureId?.value,
    createdAtEpochMillis = createdAt.toEpochMilli(),
)

fun StructuredLog.toFieldEntities(): List<StructuredLogFieldEntity> = fields.map { (key, value) ->
    StructuredLogFieldEntity(
        structuredLogId = id.value,
        fieldKey = key,
        fieldValue = value,
    )
}

fun StructuredLogEntity.toDomain(fields: List<StructuredLogFieldEntity>): StructuredLog {
    check(fields.all { it.structuredLogId == id }) {
        "Structured log fields contain a mismatched parent ID"
    }
    check(fields.map(StructuredLogFieldEntity::fieldKey).distinct().size == fields.size) {
        "Structured log fields contain duplicate keys"
    }
    return StructuredLog(
        id = StructuredLogId(id),
        fields = fields.associate { it.fieldKey to it.fieldValue },
        sourceCaptureId = sourceCaptureId?.let(::CaptureId),
        createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    )
}
