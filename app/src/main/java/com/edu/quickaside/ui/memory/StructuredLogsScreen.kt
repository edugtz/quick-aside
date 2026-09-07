package com.edu.quickaside.ui.memory

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.edu.quickaside.application.memory.MemoryStore
import com.edu.quickaside.application.memory.RECENT_MEMORY_LIMIT
import com.edu.quickaside.application.memory.StructuredLogCreationResult
import com.edu.quickaside.domain.memory.StructuredLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private sealed interface StructuredLogsState {
    data object Loading : StructuredLogsState

    data class Loaded(val logs: List<StructuredLog>) : StructuredLogsState

    data object Empty : StructuredLogsState

    data object Failed : StructuredLogsState
}

private data class FieldDraft(
    val id: Int,
    val key: String = "",
    val value: String = "",
)

private const val SAVE_LOG_ERROR = "No se pudo guardar el registro."

@Composable
fun StructuredLogsScreen(
    padding: PaddingValues,
    memoryStore: MemoryStore?,
    onBack: () -> Unit,
    timestampFormatter: NoteTimestampFormatter = NoteTimestampFormatter(),
    snackbarHostState: SnackbarHostState,
) {
    BackHandler { onBack() }

    var state by remember(memoryStore) {
        mutableStateOf<StructuredLogsState>(StructuredLogsState.Loading)
    }
    var retryToken by remember { mutableIntStateOf(0) }
    var drafts by remember { mutableStateOf(listOf(FieldDraft(id = 0))) }
    var nextDraftId by remember { mutableIntStateOf(1) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(memoryStore, retryToken) {
        state = StructuredLogsState.Loading
        val store = memoryStore
        state = if (store == null) {
            StructuredLogsState.Failed
        } else {
            try {
                store.readRecentStructuredLogs().toStructuredLogsState()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                StructuredLogsState.Failed
            }
        }
    }

    val validationMessage = drafts.validationMessage()
    val canSave = memoryStore != null && !isSaving && validationMessage == null
    val save = {
        val store = memoryStore
        if (store != null && canSave) {
            val submittedFields = drafts
                .filterNot { it.key.isBlank() && it.value.isBlank() }
                .associate { it.key to it.value }
            isSaving = true
            errorMessage = null
            scope.launch {
                val result = try {
                    store.createStructuredLog(
                        fields = submittedFields,
                        sourceCaptureId = null,
                    )
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    null
                }
                isSaving = false
                when (result) {
                    is StructuredLogCreationResult.Saved -> {
                        state = state.withSavedLog(result.log)
                        drafts = listOf(FieldDraft(id = 0))
                        nextDraftId = 1
                        errorMessage = null
                        snackbarHostState.showSnackbar("Registro guardado")
                    }

                    StructuredLogCreationResult.EmptyFields,
                    StructuredLogCreationResult.BlankFieldKey,
                    StructuredLogCreationResult.BlankFieldValue,
                    -> {
                        errorMessage = "Completa todos los campos."
                    }

                    StructuredLogCreationResult.MissingSourceCapture,
                    is StructuredLogCreationResult.Failed,
                    null,
                    -> {
                        errorMessage = SAVE_LOG_ERROR
                    }
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .testTag("StructuredLogsList"),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = 16.dp,
            end = 20.dp,
            bottom = 160.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Guarda datos con sus propios campos.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                drafts.forEachIndexed { index, draft ->
                    FieldEditorRow(
                        index = index,
                        draft = draft,
                        canRemove = drafts.size > 1,
                        enabled = !isSaving,
                        onKeyChange = { value ->
                            drafts = drafts.replaceDraft(draft.id) { it.copy(key = value) }
                            errorMessage = null
                        },
                        onValueChange = { value ->
                            drafts = drafts.replaceDraft(draft.id) { it.copy(value = value) }
                            errorMessage = null
                        },
                        onRemove = {
                            if (drafts.size > 1) {
                                drafts = drafts.filterNot { it.id == draft.id }
                                errorMessage = null
                            }
                        },
                    )
                }
                TextButton(
                    onClick = {
                        drafts = drafts + FieldDraft(id = nextDraftId)
                        nextDraftId += 1
                        errorMessage = null
                    },
                    enabled = !isSaving,
                    modifier = Modifier.semantics {
                        contentDescription = "Agregar campo"
                    },
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Text("Agregar campo")
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(
                        onClick = save,
                        enabled = canSave,
                        modifier = Modifier.semantics {
                            contentDescription = "Guardar registro"
                        },
                    ) {
                        Text("Guardar")
                    }
                }
                errorMessage?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        when (val currentState = state) {
            StructuredLogsState.Loading -> item {
                StructuredLogsLoadingState()
            }

            StructuredLogsState.Empty -> item {
                StructuredLogsEmptyState()
            }

            StructuredLogsState.Failed -> item {
                StructuredLogsFailedState(onRetry = { retryToken += 1 })
            }

            is StructuredLogsState.Loaded -> {
                item {
                    Text(
                        text = "Registros recientes",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                items(
                    items = currentState.logs,
                    key = { log -> log.id.value },
                ) { log ->
                    StructuredLogCard(
                        log = log,
                        timestampFormatter = timestampFormatter,
                    )
                }
            }
        }
    }
}

@Composable
private fun FieldEditorRow(
    index: Int,
    draft: FieldDraft,
    canRemove: Boolean,
    enabled: Boolean,
    onKeyChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            OutlinedTextField(
                value = draft.key,
                onValueChange = onKeyChange,
                modifier = Modifier.weight(1f),
                enabled = enabled,
                singleLine = true,
                label = { Text("Campo") },
            )
            OutlinedTextField(
                value = draft.value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                enabled = enabled,
                singleLine = true,
                label = { Text("Valor") },
            )
        }
        if (canRemove) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(
                    onClick = onRemove,
                    enabled = enabled,
                    modifier = Modifier.semantics {
                        contentDescription = "Eliminar campo ${index + 1}"
                    },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.RemoveCircleOutline,
                        contentDescription = null,
                    )
                }
            }
        }
    }
}

@Composable
private fun StructuredLogsLoadingState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator()
        Text("Cargando registros…")
    }
}

@Composable
private fun StructuredLogsEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.DataObject,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = "Aún no tienes registros.",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Los registros que guardes aparecerán aquí.",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun StructuredLogsFailedState(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "No se pudieron cargar tus registros.",
            style = MaterialTheme.typography.bodyLarge,
        )
        TextButton(
            onClick = onRetry,
            modifier = Modifier.semantics {
                contentDescription = "Reintentar carga de registros"
            },
        ) {
            Text("Reintentar")
        }
    }
}

@Composable
private fun StructuredLogCard(
    log: StructuredLog,
    timestampFormatter: NoteTimestampFormatter,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("StructuredLogCard-${log.id.value}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Creado · ${timestampFormatter.format(log.createdAt)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                log.fields.entries
                    .sortedBy { it.key }
                    .forEach { (key, value) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                text = key,
                                modifier = Modifier.weight(0.8f),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "→",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = value,
                                modifier = Modifier.weight(1.2f),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
            }
        }
    }
}

private fun List<FieldDraft>.validationMessage(): String? {
    val populatedRows = filterNot { it.key.isBlank() && it.value.isBlank() }
    if (populatedRows.isEmpty()) {
        return "Completa todos los campos."
    }
    if (populatedRows.any { it.key.isBlank() }) {
        return "Completa todos los campos."
    }
    if (populatedRows.any { it.value.isBlank() }) {
        return "Completa todos los campos."
    }
    if (populatedRows.map { it.key }.distinct().size != populatedRows.size) {
        return "Usa nombres de campo distintos."
    }
    return null
}

private fun List<FieldDraft>.replaceDraft(
    id: Int,
    transform: (FieldDraft) -> FieldDraft,
): List<FieldDraft> = map { draft ->
    if (draft.id == id) transform(draft) else draft
}

private fun List<StructuredLog>.toStructuredLogsState(): StructuredLogsState = if (isEmpty()) {
    StructuredLogsState.Empty
} else {
    StructuredLogsState.Loaded(this)
}

private fun StructuredLogsState.withSavedLog(savedLog: StructuredLog): StructuredLogsState {
    val existingLogs = when (this) {
        is StructuredLogsState.Loaded -> logs
        StructuredLogsState.Empty,
        StructuredLogsState.Failed,
        StructuredLogsState.Loading,
        -> emptyList()
    }
    return StructuredLogsState.Loaded(
        (existingLogs.filterNot { it.id == savedLog.id } + savedLog)
            .sortedWith(
                compareByDescending<StructuredLog> { it.createdAt }
                    .thenByDescending { it.id.value },
            )
            .take(RECENT_MEMORY_LIMIT),
    )
}
