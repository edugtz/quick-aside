package com.edu.quickaside.ui.lists

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.edu.quickaside.application.lists.AddListItemResult
import com.edu.quickaside.application.lists.ItemCompletionResult
import com.edu.quickaside.application.lists.ListStore
import com.edu.quickaside.domain.common.ListItemId
import com.edu.quickaside.domain.lists.BuiltInListDefinitions
import com.edu.quickaside.domain.lists.ListItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val comprasDefinitionId = BuiltInListDefinitions.COMPRAS.id

private sealed interface ComprasState {
    data object Loading : ComprasState

    data class Loaded(val items: List<ListItem>) : ComprasState

    data object Failed : ComprasState
}

/** Focused management surface for the always-available continuous Compras list. */
@Composable
fun ComprasScreen(
    padding: PaddingValues,
    listStore: ListStore?,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
) {
    var state by remember(listStore) { mutableStateOf<ComprasState>(ComprasState.Loading) }
    var itemText by rememberSaveable { mutableStateOf("") }
    var isAddingItem by remember { mutableStateOf(false) }
    var updatingItemIds by remember { mutableStateOf<Set<ListItemId>>(emptySet()) }
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    suspend fun loadState() {
        val store = listStore
        if (store == null) {
            state = ComprasState.Failed
            return
        }

        state = ComprasState.Loading
        try {
            state = ComprasState.Loaded(
                items = store.readCurrentItems(comprasDefinitionId),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            state = ComprasState.Failed
        }
    }

    fun showFeedback(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    fun addItem() {
        if (isAddingItem || itemText.isBlank()) return
        val submittedText = itemText
        scope.launch {
            isAddingItem = true
            try {
                val store = listStore
                if (store == null) {
                    showFeedback("No se pudo agregar el producto.")
                } else {
                    when (val result = store.addItem(
                        listDefinitionId = comprasDefinitionId,
                        text = submittedText,
                        listSessionId = null,
                    )) {
                        is AddListItemResult.Saved -> {
                            state = when (val current = state) {
                                is ComprasState.Loaded -> current.copy(
                                    items = current.items.upsert(result.item),
                                )

                                else -> current
                            }
                            itemText = ""
                            keyboardController?.hide()
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
                isAddingItem = false
            }
        }
    }

    fun setItemCompleted(item: ListItem, checked: Boolean) {
        if (item.id in updatingItemIds) return
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
                                is ComprasState.Loaded -> current.copy(
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

    LaunchedEffect(listStore) { loadState() }
    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Tu lista de compras",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Siempre disponible para agregar lo que necesitas.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when (val currentState = state) {
            ComprasState.Loading -> ComprasLoadingState()
            ComprasState.Failed -> ComprasFailedState(
                enabled = !isAddingItem,
                onRetry = { scope.launch { loadState() } },
            )

            is ComprasState.Loaded -> LoadedComprasState(
                state = currentState,
                itemText = itemText,
                onItemTextChanged = { itemText = it },
                onAddItem = ::addItem,
                canAddItem = !isAddingItem && itemText.isNotBlank(),
                canEditItemText = !isAddingItem,
                updatingItemIds = updatingItemIds,
                onItemCheckedChange = ::setItemCompleted,
            )
        }
    }
}

@Composable
private fun ColumnScope.ComprasLoadingState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator()
        Text("Cargando compras…")
    }
}

@Composable
private fun ColumnScope.ComprasFailedState(
    enabled: Boolean,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "No se pudieron cargar tus compras.",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        OutlinedButton(
            onClick = onRetry,
            enabled = enabled,
            modifier = Modifier.semantics {
                contentDescription = "Reintentar carga de compras"
            },
        ) {
            Icon(Icons.Outlined.Refresh, contentDescription = null)
            Text("Reintentar")
        }
    }
}

@Composable
private fun ColumnScope.LoadedComprasState(
    state: ComprasState.Loaded,
    itemText: String,
    onItemTextChanged: (String) -> Unit,
    onAddItem: () -> Unit,
    canAddItem: Boolean,
    canEditItemText: Boolean,
    updatingItemIds: Set<ListItemId>,
    onItemCheckedChange: (ListItem, Boolean) -> Unit,
) {
    OutlinedTextField(
        value = itemText,
        onValueChange = onItemTextChanged,
        modifier = Modifier.fillMaxWidth(),
        enabled = canEditItemText,
        singleLine = true,
        label = { Text("Agregar producto") },
        placeholder = { Text("Ej. cuerdas para guitarra") },
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
        ComprasEmptyState(modifier = Modifier.weight(1f))
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
        ) {
            items(
                items = state.items,
                key = { item -> item.id.value },
            ) { item ->
                ComprasItemRow(
                    item = item,
                    enabled = item.id !in updatingItemIds,
                    onCheckedChange = { checked -> onItemCheckedChange(item, checked) },
                )
            }
        }
    }

}

@Composable
private fun ComprasEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.ShoppingCart,
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
            text = "Agrega algo que necesites comprar.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ComprasItemRow(
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
        Row(
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
