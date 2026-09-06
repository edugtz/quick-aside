package com.edu.quickaside.ui.lists

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.edu.quickaside.application.lists.AddListItemResult
import com.edu.quickaside.application.lists.ItemCompletionResult
import com.edu.quickaside.application.lists.ListStore
import com.edu.quickaside.application.lists.SessionFinishResult
import com.edu.quickaside.application.lists.SessionStartResult
import com.edu.quickaside.domain.common.ListItemId
import com.edu.quickaside.domain.lists.BuiltInListDefinitions
import com.edu.quickaside.domain.lists.ListItem
import com.edu.quickaside.domain.lists.ListSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val mandadoDefinitionId = BuiltInListDefinitions.MANDADO.id

private sealed interface MandadoState {
    data object Loading : MandadoState

    data object NoActiveSession : MandadoState

    data class Active(
        val session: ListSession,
        val items: List<ListItem>,
    ) : MandadoState

    data object Failed : MandadoState
}

/**
 * Current Mandado management only. History and Compras intentionally stay
 * outside this surface for Change 010.
 */
@Composable
fun MandadoScreen(
    padding: PaddingValues,
    listStore: ListStore?,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
) {
    var state by remember(listStore) { mutableStateOf<MandadoState>(MandadoState.Loading) }
    var itemText by rememberSaveable { mutableStateOf("") }
    var isMutating by remember { mutableStateOf(false) }
    var finishConfirmationVisible by remember { mutableStateOf(false) }
    var updatingItemIds by remember { mutableStateOf<Set<ListItemId>>(emptySet()) }
    val scope = rememberCoroutineScope()

    suspend fun loadState() {
        val store = listStore
        if (store == null) {
            state = MandadoState.Failed
            return
        }

        state = MandadoState.Loading
        try {
            val session = store.getActiveSession(mandadoDefinitionId)
            state = if (session == null) {
                MandadoState.NoActiveSession
            } else {
                MandadoState.Active(
                    session = session,
                    items = store.readCurrentItems(mandadoDefinitionId),
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            state = MandadoState.Failed
        }
    }

    fun showFeedback(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    fun startMandado() {
        if (isMutating) return
        scope.launch {
            isMutating = true
            try {
                val store = listStore
                if (store == null) {
                    state = MandadoState.Failed
                    showFeedback("No se pudo iniciar el mandado.")
                } else {
                    when (val result = store.startSession(mandadoDefinitionId)) {
                        is SessionStartResult.Created,
                        is SessionStartResult.Existing,
                        -> {
                            val session = when (result) {
                                is SessionStartResult.Created -> result.session
                                is SessionStartResult.Existing -> result.session
                                else -> error("Unreachable session start result")
                            }
                            state = MandadoState.Active(
                                session = session,
                                items = store.readCurrentItems(mandadoDefinitionId),
                            )
                        }

                        SessionStartResult.MissingDefinition,
                        SessionStartResult.NotSessionBased,
                        is SessionStartResult.Failed,
                        -> {
                            state = MandadoState.NoActiveSession
                            showFeedback("No se pudo iniciar el mandado.")
                        }
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                state = MandadoState.NoActiveSession
                showFeedback("No se pudo iniciar el mandado.")
            } finally {
                isMutating = false
            }
        }
    }

    fun addItem() {
        if (isMutating || itemText.isBlank()) return
        val active = state as? MandadoState.Active ?: return
        val submittedText = itemText
        scope.launch {
            isMutating = true
            try {
                val store = listStore
                if (store == null) {
                    showFeedback("No se pudo agregar el producto.")
                } else {
                    when (val result = store.addItem(
                        mandadoDefinitionId,
                        submittedText,
                        active.session.id,
                    )) {
                        is AddListItemResult.Saved -> {
                            state = when (val current = state) {
                                is MandadoState.Active -> current.copy(
                                    items = current.items.upsert(result.item),
                                )

                                else -> current
                            }
                            itemText = ""
                        }

                        AddListItemResult.BlankText,
                        AddListItemResult.MissingDefinition,
                        AddListItemResult.NoActiveSession,
                        AddListItemResult.MissingSession,
                        AddListItemResult.SessionNotActive,
                        AddListItemResult.SessionDefinitionMismatch,
                        AddListItemResult.SessionNotAllowed,
                        is AddListItemResult.Failed,
                        -> showFeedback("No se pudo agregar el producto.")
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                showFeedback("No se pudo agregar el producto.")
            } finally {
                isMutating = false
            }
        }
    }

    fun setItemCompleted(item: ListItem, checked: Boolean) {
        if (isMutating || item.id in updatingItemIds) return
        updatingItemIds = updatingItemIds + item.id
        scope.launch {
            try {
                val store = listStore
                if (store == null) {
                    showFeedback("No se pudo actualizar el producto.")
                } else {
                    when (val result = store.setItemCompleted(item.id, checked)) {
                        is ItemCompletionResult.Updated -> {
                            state = when (val current = state) {
                                is MandadoState.Active -> current.copy(
                                    items = current.items.replaceById(result.item),
                                )

                                else -> current
                            }
                        }

                        ItemCompletionResult.Missing,
                        is ItemCompletionResult.Failed,
                        -> showFeedback("No se pudo actualizar el producto.")
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                showFeedback("No se pudo actualizar el producto.")
            } finally {
                updatingItemIds = updatingItemIds - item.id
            }
        }
    }

    fun finishMandado() {
        if (isMutating) return
        finishConfirmationVisible = false
        scope.launch {
            isMutating = true
            try {
                val store = listStore
                if (store == null) {
                    showFeedback("No se pudo terminar el mandado.")
                } else {
                    when (store.finishActiveSession(mandadoDefinitionId)) {
                        is SessionFinishResult.Finished -> {
                            state = MandadoState.NoActiveSession
                            showFeedback("Mandado terminado")
                        }

                        SessionFinishResult.NoActiveSession,
                        SessionFinishResult.MissingSession,
                        SessionFinishResult.AlreadyEnded,
                        SessionFinishResult.MissingDefinition,
                        SessionFinishResult.NotSessionBased,
                        is SessionFinishResult.Failed,
                        -> {
                            loadState()
                            showFeedback("No se pudo terminar el mandado.")
                        }
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                showFeedback("No se pudo terminar el mandado.")
            } finally {
                isMutating = false
            }
        }
    }

    LaunchedEffect(listStore) { loadState() }
    BackHandler(enabled = finishConfirmationVisible) {
        finishConfirmationVisible = false
    }
    BackHandler(enabled = !finishConfirmationVisible) { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Mandado actual",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        when (val currentState = state) {
            MandadoState.Loading -> LoadingState()
            MandadoState.NoActiveSession -> NoActiveSessionState(
                enabled = !isMutating,
                onStart = ::startMandado,
            )

            MandadoState.Failed -> FailedState(
                enabled = !isMutating,
                onRetry = { scope.launch { loadState() } },
            )

            is MandadoState.Active -> ActiveMandadoState(
                state = currentState,
                itemText = itemText,
                onItemTextChanged = { itemText = it },
                onAddItem = ::addItem,
                canAddItem = !isMutating && itemText.isNotBlank(),
                canEditItemText = !isMutating && updatingItemIds.isEmpty(),
                updatingItemIds = updatingItemIds,
                onItemCheckedChange = ::setItemCompleted,
                canFinish = !isMutating && updatingItemIds.isEmpty(),
                onFinish = { finishConfirmationVisible = true },
            )
        }
    }

    if (finishConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { finishConfirmationVisible = false },
            title = { Text("¿Terminar este mandado?") },
            text = { Text("Los productos permanecerán en tu historial.") },
            dismissButton = {
                TextButton(
                    onClick = { finishConfirmationVisible = false },
                    enabled = !isMutating,
                ) {
                    Text("Cancelar")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = ::finishMandado,
                    enabled = !isMutating,
                ) {
                    Text("Terminar")
                }
            },
        )
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator()
        Text("Cargando mandado…")
    }
}

@Composable
private fun NoActiveSessionState(
    enabled: Boolean,
    onStart: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.ShoppingCart,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "No hay un mandado activo.",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Inicia uno cuando quieras comenzar tu lista.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onStart,
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = "Iniciar mandado" },
        ) {
            Text("Iniciar mandado")
        }
    }
}

@Composable
private fun FailedState(
    enabled: Boolean,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "No se pudo cargar el mandado.",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        OutlinedButton(
            onClick = onRetry,
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = "Reintentar carga del mandado" },
        ) {
            Icon(Icons.Outlined.Refresh, contentDescription = null)
            Text("Reintentar")
        }
    }
}

@Composable
private fun ColumnScope.ActiveMandadoState(
    state: MandadoState.Active,
    itemText: String,
    onItemTextChanged: (String) -> Unit,
    onAddItem: () -> Unit,
    canAddItem: Boolean,
    canEditItemText: Boolean,
    updatingItemIds: Set<ListItemId>,
    onItemCheckedChange: (ListItem, Boolean) -> Unit,
    canFinish: Boolean,
    onFinish: () -> Unit,
) {
    OutlinedTextField(
        value = itemText,
        onValueChange = onItemTextChanged,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Texto del producto" },
        enabled = canEditItemText,
        singleLine = true,
        label = { Text("Agregar producto") },
        placeholder = { Text("Ej. leche") },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (canAddItem) onAddItem() }),
    )
    Button(
        onClick = onAddItem,
        enabled = canAddItem,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Agregar producto" },
    ) {
        Icon(Icons.Outlined.Add, contentDescription = null)
        Text("Agregar")
    }

    if (state.items.isEmpty()) {
        EmptyItemsState(modifier = Modifier.weight(1f))
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            items(
                items = state.items,
                key = { item -> item.id.value },
            ) { item ->
                MandadoItemRow(
                    item = item,
                    enabled = item.id !in updatingItemIds,
                    onCheckedChange = { checked -> onItemCheckedChange(item, checked) },
                )
            }
        }
    }

    HorizontalDivider()
    OutlinedButton(
        onClick = onFinish,
        enabled = canFinish,
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 96.dp)
            .semantics { contentDescription = "Terminar mandado" },
    ) {
        Icon(Icons.Outlined.Checklist, contentDescription = null)
        Text("Terminar mandado")
    }
}

@Composable
private fun EmptyItemsState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Checklist,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Aún no hay productos.",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Agrega el primero para comenzar.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MandadoItemRow(
    item: ListItem,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val actionLabel = if (item.isCompleted) {
        "Desmarcar ${item.text}"
    } else {
        "Marcar ${item.text} como completado"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = item.isCompleted,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                modifier = Modifier.semantics { contentDescription = actionLabel },
            )
            Text(
                text = item.text,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                textDecoration = if (item.isCompleted) {
                    TextDecoration.LineThrough
                } else {
                    TextDecoration.None
                },
                color = if (item.isCompleted) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

private fun List<ListItem>.upsert(item: ListItem): List<ListItem> {
    val existingIndex = indexOfFirst { it.id == item.id }
    return if (existingIndex < 0) {
        this + item
    } else {
        toMutableList().also { it[existingIndex] = item }
    }
}

private fun List<ListItem>.replaceById(item: ListItem): List<ListItem> = map { visibleItem ->
    if (visibleItem.id == item.id) item else visibleItem
}
