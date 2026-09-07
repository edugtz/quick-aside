package com.edu.quickaside.application.search

import com.edu.quickaside.domain.capture.CaptureKind
import com.edu.quickaside.domain.common.CaptureId
import com.edu.quickaside.domain.common.NoteId
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalSearchContractTest {
    @Test
    fun blankAndSurroundingWhitespaceNormalizeToNoQuery() {
        assertEquals("", LocalSearchQuery.normalize("  \t\n  "))
        assertEquals("alpha  beta", LocalSearchQuery.normalize("  alpha  beta  "))
    }

    @Test
    fun internalWhitespaceIsPreservedInLikePattern() {
        assertEquals(
            listOf('%', 'a', 'l', 'p', 'h', 'a', ' ', ' ', 'b', 'e', 't', 'a', '%'),
            LocalSearchQuery.likePatternForNormalizedQuery("alpha  beta").toList(),
        )
    }

    @Test
    fun backslashIsEscapedBeforeOtherLikeSyntax() {
        assertEquals(
            listOf('%', '\\', '\\', '%'),
            LocalSearchQuery.likePatternForNormalizedQuery("\\").toList(),
        )
    }

    @Test
    fun percentIsEscapedLiterally() {
        assertEquals(
            listOf('%', '\\', '%', '%'),
            LocalSearchQuery.likePatternForNormalizedQuery("%").toList(),
        )
    }

    @Test
    fun underscoreIsEscapedLiterally() {
        assertEquals(
            listOf('%', '\\', '_', '%'),
            LocalSearchQuery.likePatternForNormalizedQuery("_").toList(),
        )
    }

    @Test
    fun combinedWildcardAndEscapeInputUsesRequiredOrder() {
        assertEquals(
            listOf('%', '\\', '\\', '\\', '%', '\\', '_', '%'),
            LocalSearchQuery.likePatternForNormalizedQuery("\\%_").toList(),
        )
    }

    @Test
    fun limitPolicyRejectsNonPositiveAndCapsAtDefaultMaximum() {
        assertEquals(0, LocalSearchQuery.effectiveLimit(0))
        assertEquals(0, LocalSearchQuery.effectiveLimit(-4))
        assertEquals(DEFAULT_LOCAL_SEARCH_LIMIT, LocalSearchQuery.effectiveLimit(51))
        assertEquals(DEFAULT_LOCAL_SEARCH_LIMIT, LocalSearchQuery.effectiveLimit(50))
        assertEquals(7, LocalSearchQuery.effectiveLimit(7))
    }

    @Test
    fun comparatorUsesTimestampThenIdThenDeterministicKind() {
        val timestamp = Instant.parse("2026-09-06T12:00:00Z")
        val later = LocalSearchResult.Note(
            noteId = NoteId("later"),
            displayText = "later",
            sourceCaptureId = null,
            createdAt = timestamp.plusSeconds(1),
        )
        val idB = LocalSearchResult.Capture(
            captureId = CaptureId("b"),
            captureKind = CaptureKind.TEXT,
            displayText = "b",
            capturedAt = timestamp,
        )
        val idA = LocalSearchResult.Capture(
            captureId = CaptureId("a"),
            captureKind = CaptureKind.TEXT,
            displayText = "a",
            capturedAt = timestamp,
        )
        val sameIdNote = LocalSearchResult.Note(
            noteId = NoteId("a"),
            displayText = "same id",
            sourceCaptureId = null,
            createdAt = timestamp,
        )

        val sorted = listOf(idA, sameIdNote, later, idB)
            .sortedWith(LOCAL_SEARCH_RESULT_COMPARATOR)

        assertEquals(
            listOf(
                "NOTE:later",
                "CAPTURE:b",
                "CAPTURE:a",
                "NOTE:a",
            ),
            sorted.map { "${it.resultKind.name}:${it.sourceId}" },
        )
    }
}
