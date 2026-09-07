package com.edu.quickaside.data.local

import androidx.room3.withReadTransaction
import com.edu.quickaside.application.search.LOCAL_SEARCH_RESULT_COMPARATOR
import com.edu.quickaside.application.search.LocalSearch
import com.edu.quickaside.application.search.LocalSearchQuery
import com.edu.quickaside.application.search.LocalSearchResult
import com.edu.quickaside.application.search.StructuredLogSearchField
import com.edu.quickaside.domain.common.ListDefinitionId
import java.time.Instant

class RoomLocalSearch(
    private val database: QuickAsideDatabase,
) : LocalSearch {
    override suspend fun search(
        query: String,
        limit: Int,
    ): List<LocalSearchResult> {
        val normalizedQuery = LocalSearchQuery.normalize(query)
        val effectiveLimit = LocalSearchQuery.effectiveLimit(limit)
        if (normalizedQuery.isBlank() || effectiveLimit == 0) {
            return emptyList()
        }

        val pattern = LocalSearchQuery.likePatternForNormalizedQuery(normalizedQuery)
        return database.withReadTransaction {
            val results = mutableListOf<LocalSearchResult>()
            results += database.captureDao()
                .search(pattern, effectiveLimit)
                .map(::toCaptureResult)
            results += database.noteDao()
                .search(pattern, effectiveLimit)
                .map(NoteEntity::toSearchResult)

            database.structuredLogDao().search(pattern, effectiveLimit).forEach { entity ->
                val fields = database.structuredLogFieldDao()
                    .getByStructuredLogId(entity.id)
                results += entity.toSearchResult(fields)
            }

            results += database.listItemDao()
                .search(pattern, effectiveLimit)
                .map(ListItemSearchRow::toSearchResult)

            results.sortWith(LOCAL_SEARCH_RESULT_COMPARATOR)
            results.take(effectiveLimit)
        }
    }

    private fun toCaptureResult(entity: CaptureEntity): LocalSearchResult.Capture {
        val capture = entity.toDomain()
        val displayText = when (val input = capture.originalInput) {
            is com.edu.quickaside.domain.capture.CaptureInput.Text -> input.originalText
            is com.edu.quickaside.domain.capture.CaptureInput.Voice ->
                capture.effectiveTranscript
                    ?: error("Voice capture ${capture.id.value} has no effective transcript")
        }
        return LocalSearchResult.Capture(
            captureId = capture.id,
            captureKind = capture.kind,
            displayText = displayText,
            capturedAt = capture.capturedAt,
        )
    }
}

private fun NoteEntity.toSearchResult(): LocalSearchResult.Note {
    val note = toDomain()
    return LocalSearchResult.Note(
        noteId = note.id,
        displayText = note.text,
        sourceCaptureId = note.sourceCaptureId,
        createdAt = note.createdAt,
    )
}

private fun StructuredLogEntity.toSearchResult(
    fields: List<StructuredLogFieldEntity>,
): LocalSearchResult.StructuredLog {
    val log = toDomain(fields)
    return LocalSearchResult.StructuredLog(
        structuredLogId = log.id,
        fields = fields.map { field ->
            StructuredLogSearchField(
                key = field.fieldKey,
                value = field.fieldValue,
            )
        },
        sourceCaptureId = log.sourceCaptureId,
        createdAt = log.createdAt,
    )
}

private fun ListItemSearchRow.toSearchResult(): LocalSearchResult.ListItem {
    val item = ListItemEntity(
        id = id,
        listDefinitionId = listDefinitionId,
        listSessionId = listSessionId,
        text = text,
        isCompleted = isCompleted,
        createdAtEpochMillis = createdAtEpochMillis,
    ).toDomain()
    if (listSessionId == null) {
        check(sessionStartedAtEpochMillis == null && sessionEndedAtEpochMillis == null) {
            "Continuous list item has session context"
        }
    } else {
        check(sessionStartedAtEpochMillis != null) {
            "List item session ${listSessionId} has no persisted start time"
        }
    }
    return LocalSearchResult.ListItem(
        listItemId = item.id,
        displayText = item.text,
        isCompleted = item.isCompleted,
        listDefinitionId = ListDefinitionId(item.listDefinitionId.value),
        listDefinitionName = listDefinitionName,
        listSessionId = item.listSessionId,
        listSessionStartedAt = sessionStartedAtEpochMillis?.let(Instant::ofEpochMilli),
        listSessionEndedAt = sessionEndedAtEpochMillis?.let(Instant::ofEpochMilli),
        createdAt = item.createdAt,
    )
}
