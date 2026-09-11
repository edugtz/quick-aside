package com.edu.quickaside.data.local

import com.edu.quickaside.domain.common.TaskId
import com.edu.quickaside.domain.tasks.Task
import com.edu.quickaside.domain.tasks.TaskSpace
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskEntityMappingTest {
    @Test
    fun personalTaskRoundTripsWithNullDueDateAndExactTitle() {
        val task = Task(
            id = TaskId("task-personal"),
            title = "  Pagar Totalplay  ",
            space = TaskSpace.PERSONAL,
            dueDate = null,
        )

        val entity = task.toEntity()

        assertEquals("task-personal", entity.id)
        assertEquals("  Pagar Totalplay  ", entity.title)
        assertEquals("PERSONAL", entity.space)
        assertNull(entity.dueDate)
        assertEquals(task, entity.toDomain())
    }

    @Test
    fun trabajoTaskRoundTripsWithIsoDateOnlyDueDate() {
        val task = Task(
            id = TaskId("task-trabajo"),
            title = "Revisar integración",
            space = TaskSpace.TRABAJO,
            dueDate = LocalDate.of(2026, 9, 17),
        )

        val entity = task.toEntity()

        assertEquals("TRABAJO", entity.space)
        assertEquals("2026-09-17", entity.dueDate)
        assertEquals(task, entity.toDomain())
    }

    @Test
    fun completedTaskRoundTripsExactInstantAndKeepsDueDateIndependent() {
        val completedAt = Instant.parse("2026-09-17T18:19:20.123Z")
        val task = Task(
            id = TaskId("task-completed"),
            title = "Revisar integración",
            space = TaskSpace.TRABAJO,
            dueDate = LocalDate.of(2026, 9, 17),
            completedAt = completedAt,
        )

        val entity = task.toEntity()

        assertEquals(completedAt.toEpochMilli(), entity.completedAtEpochMillis)
        assertEquals("2026-09-17", entity.dueDate)
        assertEquals(task, entity.toDomain())
    }

    @Test
    fun reopeningClearsCompletionWithoutChangingDueDate() {
        val completed = Task(
            id = TaskId("task-reopened"),
            title = "Reabrir tarea",
            space = TaskSpace.PERSONAL,
            dueDate = LocalDate.of(2026, 9, 18),
            completedAt = Instant.parse("2026-09-18T08:00:00Z"),
        )
        val reopened = completed.copy(completedAt = null)

        val entity = reopened.toEntity()

        assertNull(entity.completedAtEpochMillis)
        assertEquals("2026-09-18", entity.dueDate)
        assertEquals(reopened, entity.toDomain())
    }

    @Test
    fun mappingPreservesStableIdAndDoesNotInventTaskValidation() {
        val task = Task(
            id = TaskId("stable-id"),
            title = " \t ",
            space = TaskSpace.PERSONAL,
        )

        assertEquals(task, task.toEntity().toDomain())
    }

    @Test
    fun unknownPersistedSpaceFailsVisibly() {
        val entity = TaskEntity(
            id = "task-corrupt-space",
            title = "Task",
            space = "UNKNOWN",
        )

        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            entity.toDomain()
        }
    }

    @Test
    fun malformedPersistedDueDateFailsVisibly() {
        val entity = TaskEntity(
            id = "task-corrupt-date",
            title = "Task",
            space = "PERSONAL",
            dueDate = "not-a-date",
        )

        org.junit.Assert.assertThrows(java.time.format.DateTimeParseException::class.java) {
            entity.toDomain()
        }
    }
}
