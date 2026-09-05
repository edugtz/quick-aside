package com.edu.quickaside.data.local

import com.edu.quickaside.domain.common.ListDefinitionId
import com.edu.quickaside.domain.common.ListItemId
import com.edu.quickaside.domain.common.ListSessionId
import com.edu.quickaside.domain.lists.BuiltInListDefinitions
import com.edu.quickaside.domain.lists.ListBehavior
import com.edu.quickaside.domain.lists.ListDefinition
import com.edu.quickaside.domain.lists.ListItem
import com.edu.quickaside.domain.lists.ListSession
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ListEntityMappingTest {
    @Test
    fun builtInDefinitionsUseStableStorageValues() {
        assertEquals(
            ListDefinitionEntity("mandado", "Mandado", "SESSION_BASED"),
            BuiltInListDefinitions.MANDADO.toEntity(),
        )
        assertEquals(
            ListDefinitionEntity("compras", "Compras", "CONTINUOUS"),
            BuiltInListDefinitions.COMPRAS.toEntity(),
        )
    }

    @Test
    fun listSessionAndItemRoundTripWithoutNormalizingValues() {
        val session = ListSession(
            id = ListSessionId("session-1"),
            listDefinitionId = BuiltInListDefinitions.MANDADO.id,
            startedAt = Instant.parse("2026-09-03T12:00:00.123Z"),
            endedAt = Instant.parse("2026-09-03T13:00:00.456Z"),
        )
        val item = ListItem(
            id = ListItemId("item-1"),
            listDefinitionId = BuiltInListDefinitions.MANDADO.id,
            text = "  Jabón  ",
            listSessionId = session.id,
            isCompleted = true,
            createdAt = Instant.parse("2026-09-03T12:01:00.789Z"),
        )

        assertEquals(session, session.toEntity().toDomain())
        assertEquals(item, item.toEntity().toDomain())
        assertEquals("  Jabón  ", item.toEntity().text)
        assertTrue(item.toEntity().isCompleted)
    }

    @Test
    fun continuousItemRoundTripHasNoSessionAndStartsIncomplete() {
        val item = ListItem(
            id = ListItemId("item-continuous"),
            listDefinitionId = BuiltInListDefinitions.COMPRAS.id,
            text = "Cuerdas",
            createdAt = Instant.parse("2026-09-03T12:00:00Z"),
        )

        val entity = item.toEntity()

        assertEquals(null, entity.listSessionId)
        assertFalse(entity.isCompleted)
        assertEquals(item, entity.toDomain())
    }

    @Test
    fun unknownPersistedBehaviorFailsInsteadOfFallingBack() {
        assertThrows(IllegalArgumentException::class.java) {
            ListDefinitionEntity("custom", "Custom", "UNKNOWN").toDomain()
        }
    }

    @Test
    fun blankListItemTextIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ListItem(
                id = ListItemId("blank"),
                listDefinitionId = ListDefinitionId("custom"),
                text = " \t\n ",
            )
        }
    }

    @Test
    fun listBehaviorRemainsExtensibleForFutureDefinitions() {
        val definition = ListDefinition(
            id = ListDefinitionId("viajes"),
            name = "Viajes",
            behavior = ListBehavior.CONTINUOUS,
        )

        assertEquals(definition, definition.toEntity().toDomain())
    }
}
