package com.edu.quickaside.data.local

import com.edu.quickaside.domain.common.ListDefinitionId
import com.edu.quickaside.domain.common.ListSessionId
import com.edu.quickaside.domain.lists.ListBehavior
import com.edu.quickaside.domain.lists.ListDefinition

/** Validation shared by the legacy ListStore path and the ledgered UI path. */
internal sealed interface ListItemCreateValidation {
    data class Valid(
        val definition: ListDefinition,
        val listSessionId: ListSessionId?,
    ) : ListItemCreateValidation

    data object BlankText : ListItemCreateValidation

    data object MissingDefinition : ListItemCreateValidation

    data object NoActiveSession : ListItemCreateValidation

    data object MissingSession : ListItemCreateValidation

    data object SessionNotActive : ListItemCreateValidation

    data object SessionDefinitionMismatch : ListItemCreateValidation

    data object SessionNotAllowed : ListItemCreateValidation
}

internal suspend fun QuickAsideDatabase.validateListItemCreate(
    listDefinitionId: ListDefinitionId,
    text: String,
    listSessionId: ListSessionId?,
): ListItemCreateValidation {
    if (text.isBlank()) {
        return ListItemCreateValidation.BlankText
    }

    val definition = listDefinitionDao().getById(listDefinitionId.value)?.toDomain()
        ?: return ListItemCreateValidation.MissingDefinition

    val itemSessionId = when (definition.behavior) {
        ListBehavior.CONTINUOUS -> {
            if (listSessionId != null) {
                return ListItemCreateValidation.SessionNotAllowed
            }
            null
        }

        ListBehavior.SESSION_BASED -> {
            val session = if (listSessionId == null) {
                listSessionDao().getActiveByDefinitionId(definition.id.value)?.toDomain()
                    ?: return ListItemCreateValidation.NoActiveSession
            } else {
                listSessionDao().getById(listSessionId.value)?.toDomain()
                    ?: return ListItemCreateValidation.MissingSession
            }
            if (session.listDefinitionId != definition.id) {
                return ListItemCreateValidation.SessionDefinitionMismatch
            }
            if (session.endedAt != null) {
                return ListItemCreateValidation.SessionNotActive
            }
            session.id
        }
    }

    return ListItemCreateValidation.Valid(
        definition = definition,
        listSessionId = itemSessionId,
    )
}
