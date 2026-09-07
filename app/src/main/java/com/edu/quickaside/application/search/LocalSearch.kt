package com.edu.quickaside.application.search

import com.edu.quickaside.domain.capture.CaptureKind
import com.edu.quickaside.domain.common.CaptureId
import com.edu.quickaside.domain.common.ListDefinitionId
import com.edu.quickaside.domain.common.ListItemId
import com.edu.quickaside.domain.common.ListSessionId
import com.edu.quickaside.domain.common.NoteId
import com.edu.quickaside.domain.common.StructuredLogId
import java.time.Instant

const val DEFAULT_LOCAL_SEARCH_LIMIT = 50

interface LocalSearch {
    suspend fun search(
        query: String,
        limit: Int = DEFAULT_LOCAL_SEARCH_LIMIT,
    ): List<LocalSearchResult>
}

enum class LocalSearchResultKind {
    CAPTURE,
    NOTE,
    STRUCTURED_LOG,
    LIST_ITEM,
}

data class StructuredLogSearchField(
    val key: String,
    val value: String,
)

sealed interface LocalSearchResult {
    val resultKind: LocalSearchResultKind
    val sourceId: String
    val displayText: String
    val timestamp: Instant

    data class Capture(
        val captureId: CaptureId,
        val captureKind: CaptureKind,
        override val displayText: String,
        val capturedAt: Instant,
    ) : LocalSearchResult {
        override val resultKind: LocalSearchResultKind = LocalSearchResultKind.CAPTURE
        override val sourceId: String = captureId.value
        override val timestamp: Instant = capturedAt
    }

    data class Note(
        val noteId: NoteId,
        override val displayText: String,
        val sourceCaptureId: CaptureId?,
        val createdAt: Instant,
    ) : LocalSearchResult {
        override val resultKind: LocalSearchResultKind = LocalSearchResultKind.NOTE
        override val sourceId: String = noteId.value
        override val timestamp: Instant = createdAt
    }

    data class StructuredLog(
        val structuredLogId: StructuredLogId,
        val fields: List<StructuredLogSearchField>,
        val sourceCaptureId: CaptureId?,
        val createdAt: Instant,
    ) : LocalSearchResult {
        override val resultKind: LocalSearchResultKind = LocalSearchResultKind.STRUCTURED_LOG
        override val sourceId: String = structuredLogId.value
        override val timestamp: Instant = createdAt
        override val displayText: String
            get() = fields.joinToString(", ") { field ->
                "${field.key}: ${field.value}"
            }
    }

    data class ListItem(
        val listItemId: ListItemId,
        override val displayText: String,
        val isCompleted: Boolean,
        val listDefinitionId: ListDefinitionId,
        val listDefinitionName: String,
        val listSessionId: ListSessionId?,
        val listSessionStartedAt: Instant?,
        val listSessionEndedAt: Instant?,
        val createdAt: Instant,
    ) : LocalSearchResult {
        override val resultKind: LocalSearchResultKind = LocalSearchResultKind.LIST_ITEM
        override val sourceId: String = listItemId.value
        override val timestamp: Instant = createdAt
    }
}

object LocalSearchQuery {
    fun normalize(query: String): String = query.trim()

    fun effectiveLimit(requestedLimit: Int): Int = when {
        requestedLimit <= 0 -> 0
        else -> minOf(requestedLimit, DEFAULT_LOCAL_SEARCH_LIMIT)
    }

    fun likePatternForNormalizedQuery(normalizedQuery: String): String =
        "%${normalizedQuery.replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")}%"
}

val LOCAL_SEARCH_RESULT_COMPARATOR: Comparator<LocalSearchResult> =
    compareByDescending<LocalSearchResult> { it.timestamp }
        .thenByDescending { it.sourceId }
        .thenBy { it.resultKind.name }
