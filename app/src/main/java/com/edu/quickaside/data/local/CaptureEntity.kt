package com.edu.quickaside.data.local

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import com.edu.quickaside.domain.capture.Capture
import com.edu.quickaside.domain.capture.CaptureInput
import com.edu.quickaside.domain.capture.CaptureKind
import com.edu.quickaside.domain.common.CaptureId
import java.time.Instant

@Entity(tableName = "captures")
data class CaptureEntity(
    @PrimaryKey
    val id: String,
    val kind: String,
    @ColumnInfo(name = "original_text")
    val originalText: String,
    @ColumnInfo(name = "captured_at_epoch_millis")
    val capturedAtEpochMillis: Long,
)

fun Capture.toEntity(): CaptureEntity = when (val input = originalInput) {
    is CaptureInput.Text -> CaptureEntity(
        id = id.value,
        kind = CaptureKind.TEXT.name,
        originalText = input.originalText,
        capturedAtEpochMillis = capturedAt.toEpochMilli(),
    )

    is CaptureInput.Voice -> CaptureEntity(
        id = id.value,
        kind = CaptureKind.VOICE.name,
        originalText = input.originalTranscript,
        capturedAtEpochMillis = capturedAt.toEpochMilli(),
    )
}

fun CaptureEntity.toDomain(): Capture {
    val captureKind = CaptureKind.valueOf(kind)
    val input = when (captureKind) {
        CaptureKind.TEXT -> CaptureInput.Text(originalText)
        CaptureKind.VOICE -> CaptureInput.Voice(originalText)
    }

    return Capture(
        id = CaptureId(id),
        originalInput = input,
        capturedAt = Instant.ofEpochMilli(capturedAtEpochMillis),
    )
}
