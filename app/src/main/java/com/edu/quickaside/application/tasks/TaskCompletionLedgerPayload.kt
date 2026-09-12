package com.edu.quickaside.application.tasks

internal sealed interface TaskCompletionLedgerState {
    data object Pending : TaskCompletionLedgerState

    data class Completed(val epochMillis: Long) : TaskCompletionLedgerState
}

/** Narrow v1 codec for Task completion-state Action Ledger payloads. */
internal object TaskCompletionLedgerPayloadCodec {
    private const val PENDING = "pending"
    private const val COMPLETED_PREFIX = "completed:"

    fun encode(state: TaskCompletionLedgerState): String = when (state) {
        TaskCompletionLedgerState.Pending -> PENDING
        is TaskCompletionLedgerState.Completed -> "$COMPLETED_PREFIX${state.epochMillis}"
    }

    fun decode(payload: String): TaskCompletionLedgerState? {
        if (payload == PENDING) {
            return TaskCompletionLedgerState.Pending
        }
        if (!payload.startsWith(COMPLETED_PREFIX)) {
            return null
        }

        val rawEpochMillis = payload.removePrefix(COMPLETED_PREFIX)
        if (rawEpochMillis.isEmpty()) {
            return null
        }
        val epochMillis = rawEpochMillis.toLongOrNull() ?: return null
        if (epochMillis.toString() != rawEpochMillis) {
            return null
        }
        return TaskCompletionLedgerState.Completed(epochMillis)
    }
}

internal fun isValidTaskCompletionTransition(
    before: TaskCompletionLedgerState,
    after: TaskCompletionLedgerState,
): Boolean = when {
    before is TaskCompletionLedgerState.Pending && after is TaskCompletionLedgerState.Completed -> true
    before is TaskCompletionLedgerState.Completed && after is TaskCompletionLedgerState.Pending -> true
    else -> false
}

internal fun TaskCompletionLedgerState.epochMillisOrNull(): Long? = when (this) {
    TaskCompletionLedgerState.Pending -> null
    is TaskCompletionLedgerState.Completed -> epochMillis
}
