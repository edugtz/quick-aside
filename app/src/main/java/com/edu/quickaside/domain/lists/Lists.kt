package com.edu.quickaside.domain.lists

import com.edu.quickaside.domain.common.ListDefinitionId
import com.edu.quickaside.domain.common.ListItemId
import com.edu.quickaside.domain.common.ListSessionId
import java.time.Instant

enum class ListBehavior {
    SESSION_BASED,
    CONTINUOUS,
}

data class ListDefinition(
    val id: ListDefinitionId,
    val name: String,
    val behavior: ListBehavior,
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
    val isCompleted: Boolean = false,
    val createdAt: Instant,
) {
    // Keep the Change 002 constructor source-compatible without making the
    // creation timestamp optional for new list writes.
    constructor(
        id: ListItemId,
        listDefinitionId: ListDefinitionId,
        text: String,
        listSessionId: ListSessionId? = null,
    ) : this(id, listDefinitionId, text, listSessionId, false, Instant.EPOCH)

    init {
        require(text.isNotBlank()) { "List item text must not be blank" }
    }
}

object BuiltInListDefinitions {
    val MANDADO = ListDefinition(
        id = ListDefinitionId("mandado"),
        name = "Mandado",
        behavior = ListBehavior.SESSION_BASED,
    )

    val COMPRAS = ListDefinition(
        id = ListDefinitionId("compras"),
        name = "Compras",
        behavior = ListBehavior.CONTINUOUS,
    )

    val ALL: List<ListDefinition> = listOf(MANDADO, COMPRAS)
}
