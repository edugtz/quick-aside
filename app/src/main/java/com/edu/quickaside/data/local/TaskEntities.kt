package com.edu.quickaside.data.local

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import com.edu.quickaside.domain.common.TaskId
import com.edu.quickaside.domain.tasks.Task
import com.edu.quickaside.domain.tasks.TaskSpace
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val space: String,
    @ColumnInfo(name = "due_date")
    val dueDate: String? = null,
    @ColumnInfo(name = "completed_at_epoch_millis")
    val completedAtEpochMillis: Long? = null,
)

fun Task.toEntity(): TaskEntity = TaskEntity(
    id = id.value,
    title = title,
    space = space.name,
    dueDate = dueDate?.toString(),
    completedAtEpochMillis = completedAt?.toEpochMilli(),
)

fun TaskEntity.toDomain(): Task = Task(
    id = TaskId(id),
    title = title,
    space = TaskSpace.valueOf(space),
    dueDate = dueDate?.let(LocalDate::parse),
    completedAt = completedAtEpochMillis?.let(Instant::ofEpochMilli),
)
