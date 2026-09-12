package com.edu.quickaside.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.edu.quickaside.application.tasks.CreateTaskActionResult
import com.edu.quickaside.application.tasks.ReversibleTaskActions
import com.edu.quickaside.application.tasks.TaskCompletionActionResult
import com.edu.quickaside.application.tasks.TaskStore
import com.edu.quickaside.application.tasks.UndoTaskCompletionChangeResult
import com.edu.quickaside.application.tasks.UndoTaskCreateResult
import com.edu.quickaside.domain.common.TaskId
import com.edu.quickaside.domain.tasks.Task
import com.edu.quickaside.domain.tasks.TaskSpace
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private sealed interface PendientesState {
    data object Loading : PendientesState

    data class Loaded(val tasks: List<Task>) : PendientesState

    data object Failed : PendientesState
}

private val taskSpaces = listOf(TaskSpace.PERSONAL, TaskSpace.TRABAJO)

private val spanishMonthAbbreviations = listOf(
    "ene",
    "feb",
    "mar",
    "abr",
    "may",
    "jun",
    "jul",
    "ago",
    "sep",
    "oct",
    "nov",
    "dic",
)

/** The local-first task management surface for the root Pendientes destination. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendientesScreen(
    padding: PaddingValues,
    taskStore: TaskStore?,
    reversibleTaskActions: ReversibleTaskActions?,
    snackbarHostState: SnackbarHostState,
) {
    var state by remember(taskStore) {
        mutableStateOf<PendientesState>(PendientesState.Loading)
    }
    var selectedSpaceName by rememberSaveable { mutableStateOf(TaskSpace.PERSONAL.name) }
    var taskTitle by rememberSaveable { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }
    var isUndoingCreate by remember { mutableStateOf(false) }
    var updatingTaskIds by remember { mutableStateOf<Set<TaskId>>(emptySet()) }
    val selectedSpace = TaskSpace.valueOf(selectedSpaceName)
    val scope = rememberCoroutineScope()
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val canCreate = state is PendientesState.Loaded &&
        !isCreating &&
        !isUndoingCreate &&
        taskTitle.isNotBlank()

    suspend fun loadState() {
        val store = taskStore
        if (store == null) {
            state = PendientesState.Failed
            return
        }

        state = PendientesState.Loading
        try {
            state = PendientesState.Loaded(store.readAll())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            state = PendientesState.Failed
        }
    }

    suspend fun showMessage(message: String) {
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarHostState.showSnackbar(message)
    }

    suspend fun showCreateReceipt(result: CreateTaskActionResult.Saved) {
        snackbarHostState.currentSnackbarData?.dismiss()
        val snackbarResult = snackbarHostState.showSnackbar(
            message = "Pendiente agregado",
            actionLabel = "Deshacer",
            duration = SnackbarDuration.Long,
        )
        if (snackbarResult != SnackbarResult.ActionPerformed) return

        isUndoingCreate = true
        try {
            val actions = reversibleTaskActions
            val undoResult = actions?.undoCreate(
                actionLedgerEntryId = result.actionLedgerEntryId,
                expectedTaskId = result.task.id,
            )
            if (undoResult is UndoTaskCreateResult.Undone) {
                loadState()
            } else {
                loadState()
                showMessage("No se pudo deshacer.")
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            loadState()
            showMessage("No se pudo deshacer.")
        } finally {
            isUndoingCreate = false
        }
    }

    suspend fun showCompletionReceipt(
        result: TaskCompletionActionResult.Changed,
        requestedCompleted: Boolean,
    ) {
        snackbarHostState.currentSnackbarData?.dismiss()
        val snackbarResult = snackbarHostState.showSnackbar(
            message = if (requestedCompleted) {
                "Pendiente completado"
            } else {
                "Pendiente reabierto"
            },
            actionLabel = "Deshacer",
            duration = SnackbarDuration.Long,
        )
        if (snackbarResult != SnackbarResult.ActionPerformed) return

        updatingTaskIds = updatingTaskIds + result.task.id
        try {
            val actions = reversibleTaskActions
            val undoResult = actions?.undoCompletionChange(
                actionLedgerEntryId = result.actionLedgerEntryId,
                expectedTaskId = result.task.id,
            )
            if (undoResult is UndoTaskCompletionChangeResult.Undone) {
                loadState()
            } else {
                loadState()
                showMessage("No se pudo deshacer.")
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            loadState()
            showMessage("No se pudo deshacer.")
        } finally {
            updatingTaskIds = updatingTaskIds - result.task.id
        }
    }

    fun addTask() {
        if (!canCreate) return
        val submittedTitle = taskTitle
        val submittedSpace = selectedSpace
        scope.launch {
            isCreating = true
            try {
                val actions = reversibleTaskActions
                if (actions == null) {
                    isCreating = false
                    showMessage("No se pudo agregar el pendiente.")
                } else {
                    when (val result = actions.create(
                        title = submittedTitle,
                        space = submittedSpace,
                        dueDate = null,
                    )) {
                        is CreateTaskActionResult.Saved -> {
                            state = state.withTask(result.task)
                            taskTitle = ""
                            keyboardController?.hide()
                            isCreating = false
                            showCreateReceipt(result)
                        }

                        CreateTaskActionResult.BlankTitle,
                        is CreateTaskActionResult.Failed,
                        -> {
                            isCreating = false
                            showMessage("No se pudo agregar el pendiente.")
                        }
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                isCreating = false
                showMessage("No se pudo agregar el pendiente.")
            } finally {
                isCreating = false
            }
        }
    }

    fun setTaskCompleted(task: Task, requestedCompleted: Boolean) {
        if (task.id in updatingTaskIds) return
        updatingTaskIds = updatingTaskIds + task.id
        scope.launch {
            try {
                val actions = reversibleTaskActions
                if (actions == null) {
                    loadState()
                    showMessage("No se pudo actualizar el pendiente.")
                } else {
                    val result = if (requestedCompleted) {
                        actions.complete(task.id)
                    } else {
                        actions.reopen(task.id)
                    }
                    when (result) {
                        is TaskCompletionActionResult.Changed -> {
                            state = state.withTask(result.task)
                            updatingTaskIds = updatingTaskIds - task.id
                            showCompletionReceipt(result, requestedCompleted)
                        }

                        TaskCompletionActionResult.MissingTask -> {
                            updatingTaskIds = updatingTaskIds - task.id
                            loadState()
                            showMessage("El pendiente ya no está disponible.")
                        }

                        TaskCompletionActionResult.AlreadyInRequestedState -> {
                            updatingTaskIds = updatingTaskIds - task.id
                            loadState()
                            showMessage("El pendiente ya estaba actualizado.")
                        }

                        is TaskCompletionActionResult.Failed -> {
                            updatingTaskIds = updatingTaskIds - task.id
                            loadState()
                            showMessage("No se pudo actualizar el pendiente.")
                        }
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                updatingTaskIds = updatingTaskIds - task.id
                loadState()
                showMessage("No se pudo actualizar el pendiente.")
            } finally {
                updatingTaskIds = updatingTaskIds - task.id
            }
        }
    }

    LaunchedEffect(taskStore) { loadState() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PrimaryTabRow(
            selectedTabIndex = selectedSpace.ordinal,
            modifier = Modifier.fillMaxWidth(),
        ) {
            taskSpaces.forEach { space ->
                Tab(
                    selected = selectedSpace == space,
                    onClick = { selectedSpaceName = space.name },
                    text = { Text(space.displayName) },
                )
            }
        }

        Text(
            text = "Google Tasks aún no conectado",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = taskTitle,
            onValueChange = { taskTitle = it },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isCreating && !isUndoingCreate,
            singleLine = true,
            label = { Text("Agregar pendiente") },
            placeholder = { Text("Escribe un pendiente") },
            trailingIcon = {
                IconButton(
                    onClick = ::addTask,
                    enabled = canCreate,
                    modifier = Modifier.semantics {
                        contentDescription = "Agregar pendiente"
                    },
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                }
            },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = ImeAction.Done,
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onDone = { if (canCreate) addTask() },
            ),
        )

        when (val currentState = state) {
            PendientesState.Loading -> LoadingState(modifier = Modifier.weight(1f))
            PendientesState.Failed -> FailedState(
                modifier = Modifier.weight(1f),
                enabled = !isCreating && !isUndoingCreate,
                onRetry = { scope.launch { loadState() } },
            )

            is PendientesState.Loaded -> LoadedState(
                state = currentState,
                selectedSpace = selectedSpace,
                updatingTaskIds = updatingTaskIds,
                onTaskCheckedChange = ::setTaskCompleted,
            )
        }
    }
}

@Composable
private fun ColumnScope.LoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator()
        Text("Cargando pendientes…")
    }
}

@Composable
private fun ColumnScope.FailedState(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    onRetry: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "No se pudieron cargar tus pendientes.",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        OutlinedButton(
            onClick = onRetry,
            enabled = enabled,
            modifier = Modifier.semantics {
                contentDescription = "Reintentar carga de pendientes"
            },
        ) {
            Icon(Icons.Outlined.Refresh, contentDescription = null)
            Text("Reintentar")
        }
    }
}

@Composable
private fun ColumnScope.LoadedState(
    state: PendientesState.Loaded,
    selectedSpace: TaskSpace,
    updatingTaskIds: Set<TaskId>,
    onTaskCheckedChange: (Task, Boolean) -> Unit,
) {
    val visibleTasks = state.tasks.filter { it.space == selectedSpace }
    val pendingTasks = visibleTasks
        .filter { it.completedAt == null }
        .sortedWith(taskPendingComparator)
    val completedTasks = visibleTasks
        .filter { it.completedAt != null }
        .sortedWith(taskCompletedComparator)

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .testTag("PendientesList"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
    ) {
        if (pendingTasks.isEmpty()) {
            item(key = "empty-pending") {
                EmptyState(selectedSpace = selectedSpace)
            }
        } else {
            item(key = "pending-heading") {
                Text(
                    text = "Pendientes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(
                items = pendingTasks,
                key = { task -> "pending-${task.id.value}" },
            ) { task ->
                TaskRow(
                    task = task,
                    enabled = task.id !in updatingTaskIds,
                    onCheckedChange = { checked -> onTaskCheckedChange(task, checked) },
                )
            }
        }

        if (completedTasks.isNotEmpty()) {
            item(key = "completed-divider") {
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
            }
            item(key = "completed-heading") {
                Text(
                    text = "Completados",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(
                items = completedTasks,
                key = { task -> "completed-${task.id.value}" },
            ) { task ->
                TaskRow(
                    task = task,
                    enabled = task.id !in updatingTaskIds,
                    onCheckedChange = { checked -> onTaskCheckedChange(task, checked) },
                )
            }
        }
    }
}

@Composable
private fun EmptyState(selectedSpace: TaskSpace) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "No hay pendientes en ${selectedSpace.displayName}.",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Agrega uno arriba para verlo aquí.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TaskRow(
    task: Task,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val actionLabel = if (task.completedAt == null) {
        "Marcar ${task.title} como completado"
    } else {
        "Reabrir ${task.title}"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = task.completedAt != null,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                modifier = Modifier.semantics { contentDescription = actionLabel },
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp, end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (task.completedAt == null) {
                        TextDecoration.None
                    } else {
                        TextDecoration.LineThrough
                    },
                    color = if (task.completedAt == null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                task.dueDate?.let { dueDate ->
                    Text(
                        text = formatTaskDueDate(dueDate),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private val taskPendingComparator = compareBy<Task>(
    { it.dueDate == null },
    { it.dueDate ?: LocalDate.MAX },
    { it.title.normalizedForOrdering() },
    { it.id.value },
)

private val taskCompletedComparator = compareByDescending<Task> {
    it.completedAt?.toEpochMilli() ?: Long.MIN_VALUE
}.thenBy { it.title.normalizedForOrdering() }
    .thenBy { it.id.value }

private fun String.normalizedForOrdering(): String = lowercase(Locale.ROOT)

private fun PendientesState.withTask(task: Task): PendientesState = when (this) {
    is PendientesState.Loaded -> PendientesState.Loaded(tasks.upsert(task))
    PendientesState.Loading -> PendientesState.Loading
    PendientesState.Failed -> PendientesState.Failed
}

private fun List<Task>.upsert(task: Task): List<Task> {
    val existingIndex = indexOfFirst { it.id == task.id }
    return if (existingIndex < 0) {
        this + task
    } else {
        toMutableList().also { it[existingIndex] = task }
    }
}

private val TaskSpace.displayName: String
    get() = when (this) {
        TaskSpace.PERSONAL -> "Personal"
        TaskSpace.TRABAJO -> "Trabajo"
    }

private fun formatTaskDueDate(date: LocalDate): String =
    "${date.dayOfMonth} ${spanishMonthAbbreviations[date.monthValue - 1]} ${date.year}"
