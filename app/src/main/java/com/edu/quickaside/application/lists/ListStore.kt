package com.edu.quickaside.application.lists

import com.edu.quickaside.domain.common.ListDefinitionId
import com.edu.quickaside.domain.common.ListItemId
import com.edu.quickaside.domain.common.ListSessionId
import com.edu.quickaside.domain.lists.ListDefinition
import com.edu.quickaside.domain.lists.ListItem
import com.edu.quickaside.domain.lists.ListSession
import java.time.Instant

interface ListStore {
    suspend fun readBuiltInDefinitions(): List<ListDefinition>

    suspend fun getActiveSession(listDefinitionId: ListDefinitionId): ListSession?

    suspend fun startSession(listDefinitionId: ListDefinitionId): SessionStartResult

    suspend fun finishActiveSession(listDefinitionId: ListDefinitionId): SessionFinishResult

    suspend fun finishSession(listSessionId: ListSessionId): SessionFinishResult

    suspend fun readSession(listSessionId: ListSessionId): ListSessionWithItems?

    suspend fun readRecentSessions(listDefinitionId: ListDefinitionId): List<ListSessionWithItems>

    suspend fun readCurrentItems(listDefinitionId: ListDefinitionId): List<ListItem>

    suspend fun addItem(
        listDefinitionId: ListDefinitionId,
        text: String,
        listSessionId: ListSessionId? = null,
    ): AddListItemResult

    suspend fun setItemCompleted(
        listItemId: ListItemId,
        isCompleted: Boolean,
    ): ItemCompletionResult

    suspend fun toggleItemCompleted(listItemId: ListItemId): ItemCompletionResult
}

fun interface ListClock {
    fun now(): Instant
}

interface ListIdProvider {
    fun nextSessionId(): ListSessionId

    fun nextItemId(): ListItemId
}

class RandomListIdProvider : ListIdProvider {
    override fun nextSessionId(): ListSessionId = ListSessionId(java.util.UUID.randomUUID().toString())

    override fun nextItemId(): ListItemId = ListItemId(java.util.UUID.randomUUID().toString())
}

data class ListSessionWithItems(
    val session: ListSession,
    val items: List<ListItem>,
)

sealed interface SessionStartResult {
    data class Created(val session: ListSession) : SessionStartResult

    data class Existing(val session: ListSession) : SessionStartResult

    data object MissingDefinition : SessionStartResult

    data object NotSessionBased : SessionStartResult

    data class Failed(val cause: Exception) : SessionStartResult
}

sealed interface SessionFinishResult {
    data class Finished(val session: ListSession) : SessionFinishResult

    data object NoActiveSession : SessionFinishResult

    data object MissingSession : SessionFinishResult

    data object AlreadyEnded : SessionFinishResult

    data object MissingDefinition : SessionFinishResult

    data object NotSessionBased : SessionFinishResult

    data class Failed(val cause: Exception) : SessionFinishResult
}

sealed interface AddListItemResult {
    data class Saved(val item: ListItem) : AddListItemResult

    data object BlankText : AddListItemResult

    data object MissingDefinition : AddListItemResult

    data object NoActiveSession : AddListItemResult

    data object MissingSession : AddListItemResult

    data object SessionNotActive : AddListItemResult

    data object SessionDefinitionMismatch : AddListItemResult

    data object SessionNotAllowed : AddListItemResult

    data class Failed(val cause: Exception) : AddListItemResult
}

sealed interface ItemCompletionResult {
    data class Updated(val item: ListItem) : ItemCompletionResult

    data object Missing : ItemCompletionResult

    data class Failed(val cause: Exception) : ItemCompletionResult
}
