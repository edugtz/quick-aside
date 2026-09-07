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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.KeyboardVoice
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.edu.quickaside.application.search.LocalSearch
import com.edu.quickaside.application.search.LocalSearchResult
import com.edu.quickaside.domain.capture.CaptureKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private sealed interface SearchState {
    data object Initial : SearchState

    data class Loading(val submittedInput: String) : SearchState

    data class Results(
        val submittedInput: String,
        val results: List<LocalSearchResult>,
    ) : SearchState

    data class Empty(val submittedInput: String) : SearchState

    data class Failed(val submittedInput: String) : SearchState
}

@Composable
fun SearchScreen(
    padding: PaddingValues,
    localSearch: LocalSearch?,
    onBack: () -> Unit,
    timestampFormatter: NoteTimestampFormatter = NoteTimestampFormatter(),
) {
    BackHandler { onBack() }

    var draft by rememberSaveable { mutableStateOf("") }
    var state by remember(localSearch) { mutableStateOf<SearchState>(SearchState.Initial) }
    val scope = rememberCoroutineScope()

    fun startSearch(submittedInput: String) {
        if (state is SearchState.Loading) {
            return
        }

        state = SearchState.Loading(submittedInput)
        scope.launch {
            val nextState = if (localSearch == null) {
                SearchState.Failed(submittedInput)
            } else {
                try {
                    val results = localSearch.search(submittedInput)
                    if (results.isEmpty()) {
                        SearchState.Empty(submittedInput)
                    } else {
                        SearchState.Results(submittedInput, results)
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    SearchState.Failed(submittedInput)
                }
            }
            state = nextState
        }
    }

    fun submit() {
        if (state is SearchState.Loading) {
            return
        }
        val submittedInput = draft
        if (submittedInput.trim().isBlank()) {
            return
        }
        startSearch(submittedInput)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Buscar en Memoria" },
            label = { Text("Buscar en Memoria") },
            placeholder = { Text("Escribe para buscar") },
            singleLine = true,
            trailingIcon = {
                IconButton(
                    onClick = { submit() },
                    enabled = state !is SearchState.Loading,
                    modifier = Modifier.semantics {
                        contentDescription = "Buscar"
                    },
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = null)
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit() }),
        )

        when (val currentState = state) {
            SearchState.Initial -> SearchInitialState(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            is SearchState.Loading -> SearchLoadingState(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            is SearchState.Results -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Resultados para «${currentState.submittedInput.trim()}»",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("SearchResultsList"),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 160.dp),
                    ) {
                        itemsIndexed(
                            items = currentState.results,
                            key = { _, result ->
                                "${result.resultKind.name}:${result.sourceId}"
                            },
                        ) { index, result ->
                            SearchResultCard(
                                result = result,
                                index = index,
                                timestampFormatter = timestampFormatter,
                            )
                        }
                    }
                }
            }

            is SearchState.Empty -> SearchEmptyState(
                query = currentState.submittedInput.trim(),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            is SearchState.Failed -> SearchFailedState(
                onRetry = { startSearch(currentState.submittedInput) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}

@Composable
private fun SearchInitialState(modifier: Modifier) {
    Column(
        modifier = modifier.padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Busca en tus capturas, notas, registros y listas.",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun SearchLoadingState(modifier: Modifier) {
    Column(
        modifier = modifier.padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator()
        Text("Buscando…")
    }
}

@Composable
private fun SearchEmptyState(
    query: String,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "No encontramos resultados para «$query».",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SearchFailedState(
    onRetry: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "No se pudo buscar en Memoria.",
            style = MaterialTheme.typography.bodyLarge,
        )
        TextButton(
            onClick = onRetry,
            modifier = Modifier.semantics {
                contentDescription = "Reintentar búsqueda"
            },
        ) {
            Text("Reintentar búsqueda")
        }
    }
}

@Composable
private fun SearchResultCard(
    result: LocalSearchResult,
    index: Int,
    timestampFormatter: NoteTimestampFormatter,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("SearchResultCard-$index"),
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
            when (result) {
                is LocalSearchResult.Capture -> {
                    val label = when (result.captureKind) {
                        CaptureKind.TEXT -> "Texto"
                        CaptureKind.VOICE -> "Voz"
                    }
                    SearchResultHeader(
                        icon = when (result.captureKind) {
                            CaptureKind.TEXT -> Icons.Outlined.Description
                            CaptureKind.VOICE -> Icons.Outlined.KeyboardVoice
                        },
                        label = label,
                    )
                    SearchResultDisplayText(result.displayText)
                    SearchResultTimestamp(
                        label = "$label · ${timestampFormatter.format(result.capturedAt)}",
                    )
                }

                is LocalSearchResult.Note -> {
                    SearchResultHeader(
                        icon = Icons.Outlined.Description,
                        label = "Nota",
                    )
                    SearchResultDisplayText(result.displayText)
                    SearchResultTimestamp(
                        label = "Nota · ${timestampFormatter.format(result.createdAt)}",
                    )
                }

                is LocalSearchResult.StructuredLog -> {
                    SearchResultHeader(
                        icon = Icons.Outlined.DataObject,
                        label = "Registro",
                    )
                    result.fields.forEach { field ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                text = field.key,
                                modifier = Modifier.weight(0.8f),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "→",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = field.value,
                                modifier = Modifier.weight(1.2f),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                    SearchResultTimestamp(
                        label = "Registro · ${timestampFormatter.format(result.createdAt)}",
                    )
                }

                is LocalSearchResult.ListItem -> {
                    SearchResultHeader(
                        icon = Icons.AutoMirrored.Outlined.List,
                        label = result.listDefinitionName,
                    )
                    SearchResultDisplayText(result.displayText)
                    val completionLabel = if (result.isCompleted) {
                        "Completado"
                    } else {
                        "Pendiente"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                contentDescription = completionLabel
                            },
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (result.isCompleted) {
                                Icons.Outlined.CheckCircle
                            } else {
                                Icons.Outlined.RadioButtonUnchecked
                            },
                            contentDescription = completionLabel,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(completionLabel)
                    }
                    if (result.listSessionStartedAt != null) {
                        val sessionLabel = if (result.listSessionEndedAt != null) {
                            "Sesión histórica"
                        } else {
                            "Sesión actual"
                        }
                        Text(
                            text = "$sessionLabel · " +
                                timestampFormatter.format(result.listSessionStartedAt),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    SearchResultTimestamp(
                        label = "Creado · ${timestampFormatter.format(result.createdAt)}",
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultHeader(
    icon: ImageVector,
    label: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SearchResultDisplayText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun SearchResultTimestamp(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
