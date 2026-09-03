package com.edu.quickaside.domain.reminders

import com.edu.quickaside.domain.common.NoteId
import com.edu.quickaside.domain.common.ReminderId
import com.edu.quickaside.domain.common.TaskId
import java.time.Instant

sealed interface ReminderTarget {
    data class Task(
        val taskId: TaskId,
    ) : ReminderTarget

    data class Note(
        val noteId: NoteId,
    ) : ReminderTarget
}

data class Reminder(
    val id: ReminderId,
    val target: ReminderTarget,
    val scheduledAt: Instant,
)

