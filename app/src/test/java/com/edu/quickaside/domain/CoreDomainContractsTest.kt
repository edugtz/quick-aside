package com.edu.quickaside.domain

import com.edu.quickaside.domain.capture.Capture
import com.edu.quickaside.domain.capture.CaptureInput
import com.edu.quickaside.domain.capture.CaptureKind
import com.edu.quickaside.domain.common.CaptureId
import com.edu.quickaside.domain.common.ListDefinitionId
import com.edu.quickaside.domain.common.ListItemId
import com.edu.quickaside.domain.common.ListSessionId
import com.edu.quickaside.domain.common.NoteId
import com.edu.quickaside.domain.common.ReminderId
import com.edu.quickaside.domain.common.TaskId
import com.edu.quickaside.domain.lists.ListBehavior
import com.edu.quickaside.domain.lists.ListDefinition
import com.edu.quickaside.domain.lists.ListItem
import com.edu.quickaside.domain.lists.ListSession
import com.edu.quickaside.domain.memory.Note
import com.edu.quickaside.domain.reminders.Reminder
import com.edu.quickaside.domain.reminders.ReminderTarget
import com.edu.quickaside.domain.tasks.Task
import com.edu.quickaside.domain.tasks.TaskSpace
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreDomainContractsTest {
    @Test
    fun mandadoIsSessionBased() {
        val definition = ListDefinition(
            id = ListDefinitionId("list-mandado"),
            name = "Mandado",
            behavior = ListBehavior.SESSION_BASED,
        )

        assertEquals(ListBehavior.SESSION_BASED, definition.behavior)
        assertEquals("Mandado", definition.name)
    }

    @Test
    fun comprasIsContinuous() {
        val definition = ListDefinition(
            id = ListDefinitionId("list-compras"),
            name = "Compras",
            behavior = ListBehavior.CONTINUOUS,
        )

        assertEquals(ListBehavior.CONTINUOUS, definition.behavior)
        assertEquals("Compras", definition.name)
    }

    @Test
    fun futureListDefinitionCanBeRepresentedWithoutAddingAnEnumValue() {
        val definition = ListDefinition(
            id = ListDefinitionId("list-viajes"),
            name = "Viajes",
            behavior = ListBehavior.CONTINUOUS,
        )

        assertEquals("Viajes", definition.name)
        assertEquals(ListBehavior.CONTINUOUS, definition.behavior)
    }

    @Test
    fun mandadoItemsCanBeAssociatedWithAHistoricalSession() {
        val definition = ListDefinition(
            id = ListDefinitionId("list-mandado"),
            name = "Mandado",
            behavior = ListBehavior.SESSION_BASED,
        )
        val session = ListSession(
            id = ListSessionId("session-2026-09-03"),
            listDefinitionId = definition.id,
            startedAt = Instant.parse("2026-09-03T15:00:00Z"),
            endedAt = Instant.parse("2026-09-03T16:00:00Z"),
        )
        val item = ListItem(
            id = ListItemId("item-1"),
            listDefinitionId = definition.id,
            text = "Jabón",
            listSessionId = session.id,
        )

        assertEquals(session.id, item.listSessionId)
        assertEquals(definition.id, session.listDefinitionId)
    }

    @Test
    fun personalAndTrabajoAreDistinctTaskSpaces() {
        assertNotEquals(TaskSpace.PERSONAL, TaskSpace.TRABAJO)
    }

    @Test
    fun taskDueDateIsIndependentFromExactReminderTime() {
        val dueDate = LocalDate.of(2026, 9, 4)
        val task = Task(
            id = TaskId("task-1"),
            title = "Revisar PR",
            space = TaskSpace.TRABAJO,
            dueDate = dueDate,
        )
        val reminder = Reminder(
            id = ReminderId("reminder-1"),
            target = ReminderTarget.Task(task.id),
            scheduledAt = Instant.parse("2026-09-04T16:00:00Z"),
        )

        assertEquals(dueDate, task.dueDate)
        assertEquals(Instant.parse("2026-09-04T16:00:00Z"), reminder.scheduledAt)
    }

    @Test
    fun reminderCanTargetANoteWithoutTurningItIntoATask() {
        val note = Note(
            id = NoteId("note-1"),
            text = "Llamar al taller",
            createdAt = Instant.parse("2026-09-04T12:00:00Z"),
        )
        val reminder = Reminder(
            id = ReminderId("reminder-1"),
            target = ReminderTarget.Note(note.id),
            scheduledAt = Instant.parse("2026-09-04T16:00:00Z"),
        )

        assertEquals(ReminderTarget.Note(note.id), reminder.target)
        assertTrue(reminder.target is ReminderTarget.Note)
        assertFalse(reminder.target is ReminderTarget.Task)
    }

    @Test
    fun capturesDistinguishTextAndVoiceAndRetainOriginalTextualInput() {
        val capturedAt = Instant.parse("2026-09-03T12:34:56Z")
        val textCapture = Capture(
            id = CaptureId("capture-text"),
            originalInput = CaptureInput.Text("Comprar leche"),
            capturedAt = capturedAt,
        )
        val voiceCapture = Capture(
            id = CaptureId("capture-voice"),
            originalInput = CaptureInput.Voice("Mañana revisa el PR"),
            capturedAt = capturedAt,
        )

        assertEquals(CaptureKind.TEXT, textCapture.kind)
        assertEquals("Comprar leche", (textCapture.originalInput as CaptureInput.Text).originalText)
        assertEquals(CaptureKind.VOICE, voiceCapture.kind)
        assertEquals(
            "Mañana revisa el PR",
            (voiceCapture.originalInput as CaptureInput.Voice).originalTranscript,
        )
        assertEquals(capturedAt, voiceCapture.capturedAt)
    }
}
