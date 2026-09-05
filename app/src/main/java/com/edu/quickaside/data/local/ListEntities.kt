package com.edu.quickaside.data.local

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import com.edu.quickaside.domain.common.ListDefinitionId
import com.edu.quickaside.domain.common.ListItemId
import com.edu.quickaside.domain.common.ListSessionId
import com.edu.quickaside.domain.lists.ListBehavior
import com.edu.quickaside.domain.lists.ListDefinition
import com.edu.quickaside.domain.lists.ListItem
import com.edu.quickaside.domain.lists.ListSession
import java.time.Instant

@Entity(tableName = "list_definitions")
data class ListDefinitionEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val behavior: String,
)

@Entity(
    tableName = "list_sessions",
    foreignKeys = [
        ForeignKey(
            entity = ListDefinitionEntity::class,
            parentColumns = ["id"],
            childColumns = ["list_definition_id"],
            onDelete = ForeignKey.NO_ACTION,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["list_definition_id"]),
    ],
)
data class ListSessionEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "list_definition_id")
    val listDefinitionId: String,
    @ColumnInfo(name = "started_at_epoch_millis")
    val startedAtEpochMillis: Long,
    @ColumnInfo(name = "ended_at_epoch_millis")
    val endedAtEpochMillis: Long? = null,
)

@Entity(
    tableName = "list_items",
    foreignKeys = [
        ForeignKey(
            entity = ListDefinitionEntity::class,
            parentColumns = ["id"],
            childColumns = ["list_definition_id"],
            onDelete = ForeignKey.NO_ACTION,
            onUpdate = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = ListSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["list_session_id"],
            onDelete = ForeignKey.NO_ACTION,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["list_definition_id"]),
        Index(value = ["list_session_id"]),
    ],
)
data class ListItemEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "list_definition_id")
    val listDefinitionId: String,
    @ColumnInfo(name = "list_session_id")
    val listSessionId: String? = null,
    val text: String,
    @ColumnInfo(name = "is_completed")
    val isCompleted: Boolean,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,
)

fun ListDefinition.toEntity(): ListDefinitionEntity = ListDefinitionEntity(
    id = id.value,
    name = name,
    behavior = behavior.name,
)

fun ListDefinitionEntity.toDomain(): ListDefinition = ListDefinition(
    id = ListDefinitionId(id),
    name = name,
    // valueOf deliberately fails on an unknown persisted value.
    behavior = ListBehavior.valueOf(behavior),
)

fun ListSession.toEntity(): ListSessionEntity = ListSessionEntity(
    id = id.value,
    listDefinitionId = listDefinitionId.value,
    startedAtEpochMillis = startedAt.toEpochMilli(),
    endedAtEpochMillis = endedAt?.toEpochMilli(),
)

fun ListSessionEntity.toDomain(): ListSession = ListSession(
    id = ListSessionId(id),
    listDefinitionId = ListDefinitionId(listDefinitionId),
    startedAt = Instant.ofEpochMilli(startedAtEpochMillis),
    endedAt = endedAtEpochMillis?.let(Instant::ofEpochMilli),
)

fun ListItem.toEntity(): ListItemEntity = ListItemEntity(
    id = id.value,
    listDefinitionId = listDefinitionId.value,
    listSessionId = listSessionId?.value,
    text = text,
    isCompleted = isCompleted,
    createdAtEpochMillis = createdAt.toEpochMilli(),
)

fun ListItemEntity.toDomain(): ListItem = ListItem(
    id = ListItemId(id),
    listDefinitionId = ListDefinitionId(listDefinitionId),
    text = text,
    listSessionId = listSessionId?.let(::ListSessionId),
    isCompleted = isCompleted,
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
)
