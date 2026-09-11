package com.edu.quickaside.application.tasks

import com.edu.quickaside.domain.common.TaskId
import com.edu.quickaside.domain.tasks.Task

interface TaskStore {
    suspend fun save(task: Task)

    suspend fun getById(id: TaskId): Task?

    suspend fun readAll(): List<Task>
}
