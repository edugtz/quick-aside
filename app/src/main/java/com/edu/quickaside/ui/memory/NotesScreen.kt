package com.edu.quickaside.ui.memory

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.edu.quickaside.application.memory.MemoryStore
import com.edu.quickaside.application.memory.NoteCreationResult
import com.edu.quickaside.application.memory.RECENT_MEMORY_LIMIT
import com.edu.quickaside.domain.memory.Note
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private sealed interface NotesState {
    data object Loading : NotesState

    data class Loaded(val notes: List<Note>) : NotesState

    data object Empty : NotesState

    data object Failed : NotesState
}

private const val SAVE_NOTE_ERROR = "No se pudo guardar la nota."

@Composable
fun NotesScreen(
    padding: PaddingValues,
    memoryStore: MemoryStore?,
    onBack: () -> Unit,
    timestampFormatter: NoteTimestampFormatter = NoteTimestampFormatter(),
    snackbarHostState: SnackbarHostState,
) {
    BackHandler { onBack() }

    var state by remember(memoryStore) { mutableStateOf<NotesState>(NotesState.Loading) }
    var retryToken by remember { mutableIntStateOf(0) }
    var draft by rememberSaveable { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(memoryStore, retryToken) {
        state = NotesState.Loading
        val store = memoryStore
        state = if (store == null) {
            NotesState.Failed
        } else {
            try {
                store.readRecentNotes().toNotesState()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                NotesState.Failed
            }
        }
    }

    val canSave = memoryStore != null && !isSaving && draft.isNotBlank()
    val save = {
        val store = memoryStore
        if (store != null && canSave) {
            val submittedText = draft
            isSaving = true
            errorMessage = null
            scope.launch {
                val result = try {
                    store.createNote(
                        text = submittedText,
                        sourceCaptureId = null,
                    )
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    null
                }
                isSaving = false
                when (result) {
                    is NoteCreationResult.Saved -> {
                        state = state.withSavedNote(result.note)
                        draft = ""
                        errorMessage = null
                        snackbarHostState.showSnackbar("Nota guardada")
                    }

                    NoteCreationResult.BlankText -> {
                        errorMessage = "Escribe algo para guardar."
                    }

                    NoteCreationResult.MissingSourceCapture,
                    is NoteCreationResult.Failed,
                    null,
                    -> {
                        errorMessage = SAVE_NOTE_ERROR
                    }
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .testTag("NotesList"),
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
                    text = "Guarda una idea para volver a ella después.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = draft,
                    onValueChange = {
                        draft = it
                        errorMessage = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving,
                    label = { Text("Nota") },
                    placeholder = { Text("Escribe algo que quieras recordar") },
                    minLines = 4,
                    maxLines = 8,
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { message ->
                        { Text(message) }
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(
                        onClick = save,
                        enabled = canSave,
                        modifier = Modifier.semantics {
                            contentDescription = "Guardar nota"
                        },
                    ) {
                        Text("Guardar nota")
                    }
                }
            }
        }

        when (val currentState = state) {
            NotesState.Loading -> item {
                NotesLoadingState()
            }

            NotesState.Empty -> item {
                NotesEmptyState()
            }

            NotesState.Failed -> item {
                NotesFailedState(onRetry = { retryToken += 1 })
            }

            is NotesState.Loaded -> {
                item {
                    Text(
                        text = "Notas recientes",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                items(
                    items = currentState.notes,
                    key = { note -> note.id.value },
                ) { note ->
                    NoteRow(
                        note = note,
                        timestampFormatter = timestampFormatter,
                    )
                }
            }
        }
    }
}

private fun List<Note>.toNotesState(): NotesState = if (isEmpty()) {
    NotesState.Empty
} else {
    NotesState.Loaded(this)
}

private fun NotesState.withSavedNote(savedNote: Note): NotesState {
    val existingNotes = when (this) {
        is NotesState.Loaded -> notes
        NotesState.Empty,
        NotesState.Failed,
        NotesState.Loading,
        -> emptyList()
    }
    return NotesState.Loaded(
        (existingNotes.filterNot { it.id == savedNote.id } + savedNote)
            .sortedWith(
                compareByDescending<Note> { it.createdAt }
                    .thenByDescending { it.id.value },
            )
            .take(RECENT_MEMORY_LIMIT),
    )
}

@Composable
private fun NotesLoadingState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator()
        Text("Cargando notas…")
    }
}

@Composable
private fun NotesEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Description,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Aún no tienes notas.",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Las notas que guardes aparecerán aquí.",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun NotesFailedState(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "No se pudieron cargar tus notas.",
            style = MaterialTheme.typography.bodyLarge,
        )
        TextButton(
            onClick = onRetry,
            modifier = Modifier.semantics {
                contentDescription = "Reintentar carga de notas"
            },
        ) {
            Text("Reintentar")
        }
    }
}

@Composable
private fun NoteRow(
    note: Note,
    timestampFormatter: NoteTimestampFormatter,
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("NoteCard-${note.id.value}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = note.text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "Creada · ${timestampFormatter.format(note.createdAt)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
