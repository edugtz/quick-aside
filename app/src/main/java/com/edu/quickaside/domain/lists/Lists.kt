package com.edu.quickaside.domain.lists

import com.edu.quickaside.domain.common.ListDefinitionId
import com.edu.quickaside.domain.common.ListItemId
import com.edu.quickaside.domain.common.ListSessionId
import java.time.Instant

enum class ListBehavior {
    SESSION_BASED,
    CONTINUOUS,
}

enum class ListDefinitionType(val behavior: ListBehavior) {
    MANDADO(ListBehavior.SESSION_BASED),
    COMPRAS(ListBehavior.CONTINUOUS),
}

data class ListDefinition(
    val id: ListDefinitionId,
    val type: ListDefinitionType,
)

data class ListSession(
    val id: ListSessionId,
    val listDefinitionId: ListDefinitionId,
    val startedAt: Instant,
    val endedAt: Instant? = null,
)

data class ListItem(
    val id: ListItemId,
    val listDefinitionId: ListDefinitionId,
    val text: String,
    val listSessionId: ListSessionId? = null,
)

