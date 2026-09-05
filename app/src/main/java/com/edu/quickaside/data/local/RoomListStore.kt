package com.edu.quickaside.data.local

import com.edu.quickaside.application.lists.AddListItemResult
import com.edu.quickaside.application.lists.ItemCompletionResult
import com.edu.quickaside.application.lists.ListClock
import com.edu.quickaside.application.lists.ListIdProvider
import com.edu.quickaside.application.lists.ListSessionWithItems
import com.edu.quickaside.application.lists.ListStore
import com.edu.quickaside.application.lists.RandomListIdProvider
import com.edu.quickaside.application.lists.SessionFinishResult
import com.edu.quickaside.application.lists.SessionStartResult
import com.edu.quickaside.domain.common.ListDefinitionId
import com.edu.quickaside.domain.common.ListItemId
import com.edu.quickaside.domain.common.ListSessionId
import com.edu.quickaside.domain.lists.ListBehavior
import com.edu.quickaside.domain.lists.ListDefinition
import com.edu.quickaside.domain.lists.ListItem
import com.edu.quickaside.domain.lists.ListSession
import java.time.Instant
import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import kotlinx.coroutines.CancellationException

class RoomListStore(
    private val database: QuickAsideDatabase,
    private val idProvider: ListIdProvider = RandomListIdProvider(),
    private val clock: ListClock = ListClock { Instant.now() },
) : ListStore {
    override suspend fun readBuiltInDefinitions(): List<ListDefinition> = database.withReadTransaction {
        BuiltInListDefinitionsForStore.readFrom(database.listDefinitionDao())
    }

    override suspend fun getActiveSession(listDefinitionId: ListDefinitionId): ListSession? =
        database.withReadTransaction {
            val definition = database.listDefinitionDao().getById(listDefinitionId.value)
                ?.toDomain()
                ?: return@withReadTransaction null
            if (definition.behavior != ListBehavior.SESSION_BASED) {
                return@withReadTransaction null
            }
            database.listSessionDao()
                .getActiveByDefinitionId(definition.id.value)
                ?.toDomain()
        }

    override suspend fun startSession(listDefinitionId: ListDefinitionId): SessionStartResult = try {
        database.withWriteTransaction {
            val definitionEntity = database.listDefinitionDao().getById(listDefinitionId.value)
                ?: return@withWriteTransaction SessionStartResult.MissingDefinition
            val definition = definitionEntity.toDomain()
            if (definition.behavior != ListBehavior.SESSION_BASED) {
                return@withWriteTransaction SessionStartResult.NotSessionBased
            }

            val active = database.listSessionDao()
                .getActiveByDefinitionId(definition.id.value)
                ?.toDomain()
            if (active != null) {
                return@withWriteTransaction SessionStartResult.Existing(active)
            }

            val session = ListSession(
                id = idProvider.nextSessionId(),
                listDefinitionId = definition.id,
                startedAt = clock.now(),
            )
            database.listSessionDao().insert(session.toEntity())
            SessionStartResult.Created(session)
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        SessionStartResult.Failed(failure)
    }

    override suspend fun finishActiveSession(listDefinitionId: ListDefinitionId): SessionFinishResult = try {
        database.withWriteTransaction {
            val definitionEntity = database.listDefinitionDao().getById(listDefinitionId.value)
                ?: return@withWriteTransaction SessionFinishResult.MissingDefinition
            val definition = definitionEntity.toDomain()
            if (definition.behavior != ListBehavior.SESSION_BASED) {
                return@withWriteTransaction SessionFinishResult.NotSessionBased
            }

            val active = database.listSessionDao()
                .getActiveByDefinitionId(definition.id.value)
                ?: return@withWriteTransaction SessionFinishResult.NoActiveSession
            finishSessionInTransaction(active.toDomain(), definition)
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        SessionFinishResult.Failed(failure)
    }

    override suspend fun finishSession(listSessionId: ListSessionId): SessionFinishResult = try {
        database.withWriteTransaction {
            val sessionEntity = database.listSessionDao().getById(listSessionId.value)
                ?: return@withWriteTransaction SessionFinishResult.MissingSession
            val session = sessionEntity.toDomain()
            val definition = database.listDefinitionDao().getById(session.listDefinitionId.value)
                ?.toDomain()
                ?: return@withWriteTransaction SessionFinishResult.MissingDefinition
            if (definition.behavior != ListBehavior.SESSION_BASED) {
                return@withWriteTransaction SessionFinishResult.NotSessionBased
            }
            finishSessionInTransaction(session, definition)
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        SessionFinishResult.Failed(failure)
    }

    override suspend fun readSession(listSessionId: ListSessionId): ListSessionWithItems? =
        database.withReadTransaction {
            val session = database.listSessionDao().getById(listSessionId.value)
                ?.toDomain()
                ?: return@withReadTransaction null
            val definition = requireDefinition(session.listDefinitionId)
            check(definition.behavior == ListBehavior.SESSION_BASED) {
                "A continuous list cannot own a session"
            }
            ListSessionWithItems(
                session = session,
                items = database.listItemDao().getBySessionId(session.id.value)
                    .map { entity -> entity.toDomain().also { validateSessionItem(it, session) } },
            )
        }

    override suspend fun readRecentSessions(
        listDefinitionId: ListDefinitionId,
    ): List<ListSessionWithItems> = database.withReadTransaction {
        val definition = database.listDefinitionDao().getById(listDefinitionId.value)
            ?.toDomain()
            ?: return@withReadTransaction emptyList()
        check(definition.behavior == ListBehavior.SESSION_BASED) {
            "Continuous lists do not have session history"
        }
        database.listSessionDao().getByDefinitionId(definition.id.value).map { entity ->
            val session = entity.toDomain()
            ListSessionWithItems(
                session = session,
                items = database.listItemDao().getBySessionId(session.id.value)
                    .map { item -> item.toDomain().also { validateSessionItem(it, session) } },
            )
        }
    }

    override suspend fun readCurrentItems(listDefinitionId: ListDefinitionId): List<ListItem> =
        database.withReadTransaction {
            val definition = database.listDefinitionDao().getById(listDefinitionId.value)
                ?.toDomain()
                ?: return@withReadTransaction emptyList()
            when (definition.behavior) {
                ListBehavior.CONTINUOUS -> database.listItemDao()
                    .getContinuousByDefinitionId(definition.id.value)
                    .map { entity ->
                        entity.toDomain().also { item ->
                            check(item.listDefinitionId == definition.id) {
                                "List item belongs to a different definition"
                            }
                            check(item.listSessionId == null) {
                                "Continuous list item must not belong to a session"
                            }
                        }
                    }

                ListBehavior.SESSION_BASED -> {
                    val active = database.listSessionDao()
                        .getActiveByDefinitionId(definition.id.value)
                        ?: return@withReadTransaction emptyList()
                    database.listItemDao().getBySessionId(active.id).map { entity ->
                        entity.toDomain().also { validateSessionItem(it, active.toDomain()) }
                    }
                }
            }
        }

    override suspend fun addItem(
        listDefinitionId: ListDefinitionId,
        text: String,
        listSessionId: ListSessionId?,
    ): AddListItemResult {
        if (text.isBlank()) {
            return AddListItemResult.BlankText
        }

        return try {
            database.withWriteTransaction {
                val definitionEntity = database.listDefinitionDao().getById(listDefinitionId.value)
                    ?: return@withWriteTransaction AddListItemResult.MissingDefinition
                val definition = definitionEntity.toDomain()
                val itemSessionId = when (definition.behavior) {
                    ListBehavior.CONTINUOUS -> {
                        if (listSessionId != null) {
                            return@withWriteTransaction AddListItemResult.SessionNotAllowed
                        }
                        null
                    }

                    ListBehavior.SESSION_BASED -> {
                        val session = if (listSessionId == null) {
                            database.listSessionDao()
                                .getActiveByDefinitionId(definition.id.value)
                                ?.toDomain()
                                ?: return@withWriteTransaction AddListItemResult.NoActiveSession
                        } else {
                            database.listSessionDao().getById(listSessionId.value)?.toDomain()
                                ?: return@withWriteTransaction AddListItemResult.MissingSession
                        }
                        if (session.listDefinitionId != definition.id) {
                            return@withWriteTransaction AddListItemResult.SessionDefinitionMismatch
                        }
                        if (session.endedAt != null) {
                            return@withWriteTransaction AddListItemResult.SessionNotActive
                        }
                        session.id
                    }
                }

                val item = ListItem(
                    id = idProvider.nextItemId(),
                    listDefinitionId = definition.id,
                    text = text,
                    listSessionId = itemSessionId,
                    isCompleted = false,
                    createdAt = clock.now(),
                )
                database.listItemDao().insert(item.toEntity())
                AddListItemResult.Saved(item)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            AddListItemResult.Failed(failure)
        }
    }

    override suspend fun setItemCompleted(
        listItemId: ListItemId,
        isCompleted: Boolean,
    ): ItemCompletionResult = try {
        database.withWriteTransaction {
            val updated = database.listItemDao().setCompleted(listItemId.value, isCompleted)
            if (updated != 1) {
                return@withWriteTransaction ItemCompletionResult.Missing
            }
            val item = database.listItemDao().getById(listItemId.value)
                ?: error("Completed list item disappeared after update")
            ItemCompletionResult.Updated(item.toDomain())
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        ItemCompletionResult.Failed(failure)
    }

    override suspend fun toggleItemCompleted(listItemId: ListItemId): ItemCompletionResult = try {
        database.withWriteTransaction {
            val existing = database.listItemDao().getById(listItemId.value)
                ?: return@withWriteTransaction ItemCompletionResult.Missing
            val updated = database.listItemDao().setCompleted(
                id = listItemId.value,
                isCompleted = !existing.isCompleted,
            )
            check(updated == 1) { "Expected exactly one list item completion update" }
            val item = database.listItemDao().getById(listItemId.value)
                ?: error("Toggled list item disappeared after update")
            ItemCompletionResult.Updated(item.toDomain())
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        ItemCompletionResult.Failed(failure)
    }

    private suspend fun requireDefinition(listDefinitionId: ListDefinitionId): ListDefinition =
        database.listDefinitionDao().getById(listDefinitionId.value)
            ?.toDomain()
            ?: error("List definition ${listDefinitionId.value} is missing")

    private suspend fun finishSessionInTransaction(
        session: ListSession,
        definition: ListDefinition,
    ): SessionFinishResult {
        if (session.endedAt != null) {
            return SessionFinishResult.AlreadyEnded
        }
        check(session.listDefinitionId == definition.id) {
            "List session belongs to a different definition"
        }
        val endedAt = clock.now()
        check(database.listSessionDao().finishActive(session.id.value, endedAt.toEpochMilli()) == 1) {
            "Expected exactly one active list session finish update"
        }
        val finished = database.listSessionDao().getById(session.id.value)
            ?: error("Finished list session disappeared after update")
        return SessionFinishResult.Finished(finished.toDomain())
    }

    private fun validateSessionItem(item: ListItem, session: ListSession) {
        check(item.listDefinitionId == session.listDefinitionId) {
            "List item belongs to a different definition than its session"
        }
        check(item.listSessionId == session.id) {
            "List item session does not match its queried session"
        }
    }

    private object BuiltInListDefinitionsForStore {
        suspend fun readFrom(dao: ListDefinitionDao): List<ListDefinition> =
            com.edu.quickaside.domain.lists.BuiltInListDefinitions.ALL.map { expected ->
                val actual = dao.getById(expected.id.value)?.toDomain()
                    ?: error("Built-in list definition ${expected.id.value} is missing")
                check(actual == expected) {
                    "Built-in list definition ${expected.id.value} does not match its contract"
                }
                actual
            }
    }
}
