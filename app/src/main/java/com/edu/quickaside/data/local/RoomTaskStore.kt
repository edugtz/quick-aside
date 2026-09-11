package com.edu.quickaside.data.local

import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import com.edu.quickaside.application.tasks.TaskStore
import com.edu.quickaside.domain.common.TaskId
import com.edu.quickaside.domain.tasks.Task
import kotlinx.coroutines.CancellationException

class RoomTaskStore(
    private val database: QuickAsideDatabase,
) : TaskStore {
    override suspend fun save(task: Task) {
        try {
            database.withWriteTransaction<Unit> {
                database.taskDao().upsert(task.toEntity())
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        }
    }

    override suspend fun getById(id: TaskId): Task? = database.withReadTransaction {
        database.taskDao().getById(id.value)?.toDomain()
    }

    override suspend fun readAll(): List<Task> = database.withReadTransaction {
        database.taskDao().getAll().map(TaskEntity::toDomain)
    }
}
