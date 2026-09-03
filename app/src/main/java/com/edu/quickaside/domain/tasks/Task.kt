package com.edu.quickaside.domain.tasks

import com.edu.quickaside.domain.common.TaskId
import java.time.LocalDate

enum class TaskSpace {
    PERSONAL,
    TRABAJO,
}

data class Task(
    val id: TaskId,
    val title: String,
    val space: TaskSpace,
    val dueDate: LocalDate? = null,
)

