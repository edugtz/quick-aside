package com.edu.quickaside.application.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskCompletionLedgerPayloadTest {
    @Test
    fun codecUsesTheExactFrozenV1Grammar() {
        assertEquals("pending", TaskCompletionLedgerPayloadCodec.encode(TaskCompletionLedgerState.Pending))
        assertEquals(
            "completed:1789223400123",
            TaskCompletionLedgerPayloadCodec.encode(
                TaskCompletionLedgerState.Completed(1_789_223_400_123L),
            ),
        )
        assertEquals(
            TaskCompletionLedgerState.Pending,
            TaskCompletionLedgerPayloadCodec.decode("pending"),
        )
        assertEquals(
            TaskCompletionLedgerState.Completed(Long.MIN_VALUE),
            TaskCompletionLedgerPayloadCodec.decode("completed:${Long.MIN_VALUE}"),
        )
        assertEquals(
            TaskCompletionLedgerState.Completed(Long.MAX_VALUE),
            TaskCompletionLedgerPayloadCodec.decode("completed:${Long.MAX_VALUE}"),
        )
    }

    @Test
    fun decoderRejectsMalformedValuesWithoutRepairingThem() {
        listOf(
            "",
            "PENDING",
            "pending ",
            " pending",
            "completed",
            "completed:",
            "completed: ",
            "completed:+1",
            "completed:01",
            "completed:-0",
            "completed:1.0",
            "completed:9223372036854775808",
            "completed:-9223372036854775809",
            "completed:1\n",
            "unknown:1",
        ).forEach { payload ->
            assertNull("Expected strict rejection for $payload", TaskCompletionLedgerPayloadCodec.decode(payload))
        }
    }

    @Test
    fun onlyPendingToCompletedAndCompletedToPendingAreValidTransitions() {
        val pending = TaskCompletionLedgerState.Pending
        val completed = TaskCompletionLedgerState.Completed(123L)
        assertTrue(isValidTaskCompletionTransition(pending, completed))
        assertTrue(isValidTaskCompletionTransition(completed, pending))
        assertFalse(isValidTaskCompletionTransition(pending, pending))
        assertFalse(
            isValidTaskCompletionTransition(
                completed,
                TaskCompletionLedgerState.Completed(456L),
            ),
        )
    }
}
