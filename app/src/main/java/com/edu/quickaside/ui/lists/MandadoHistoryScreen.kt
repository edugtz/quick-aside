package com.edu.quickaside.ui.lists

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.edu.quickaside.application.lists.ListSessionWithItems
import com.edu.quickaside.application.lists.ListStore
import com.edu.quickaside.domain.lists.BuiltInListDefinitions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val mandadoHistoryDefinitionId = BuiltInListDefinitions.MANDADO.id

private sealed interface MandadoHistoryState {
    data object Loading : MandadoHistoryState

    data class Loaded(val sessions: List<ListSessionWithItems>) : MandadoHistoryState

    data object Empty : MandadoHistoryState

    data object Failed : MandadoHistoryState
}

/** Read-only list of completed Mandado sessions. */
@Composable
fun MandadoHistoryScreen(
    padding: PaddingValues,
    listStore: ListStore?,
    onBack: () -> Unit,
    onOpenDetail: (ListSessionWithItems) -> Unit,
    timestampFormatter: MandadoHistoryTimestampFormatter = MandadoHistoryTimestampFormatter(),
) {
    var state by remember(listStore) {
        mutableStateOf<MandadoHistoryState>(MandadoHistoryState.Loading)
    }
    val scope = rememberCoroutineScope()

    suspend fun loadHistory() {
        val store = listStore
        if (store == null) {
            state = MandadoHistoryState.Failed
            return
        }

        state = MandadoHistoryState.Loading
        try {
            val completedSessions = store.readRecentSessions(mandadoHistoryDefinitionId)
                .filter { sessionWithItems -> sessionWithItems.session.endedAt != null }
            state = if (completedSessions.isEmpty()) {
                MandadoHistoryState.Empty
            } else {
                MandadoHistoryState.Loaded(completedSessions)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            state = MandadoHistoryState.Failed
        }
    }

    LaunchedEffect(listStore) { loadHistory() }
    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Historial de mandados",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Consulta tus sesiones terminadas, de la más reciente a la más antigua.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when (val currentState = state) {
            MandadoHistoryState.Loading -> HistoryLoadingState()
            MandadoHistoryState.Empty -> HistoryEmptyState()
            MandadoHistoryState.Failed -> HistoryFailedState(
                onRetry = { scope.launch { loadHistory() } },
            )

            is MandadoHistoryState.Loaded -> HistoryLoadedState(
                sessions = currentState.sessions,
                timestampFormatter = timestampFormatter,
                onOpenDetail = onOpenDetail,
            )
        }
    }
}

@Composable
private fun ColumnScope.HistoryLoadingState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator()
        Text("Cargando historial…")
    }
}

@Composable
private fun ColumnScope.HistoryEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.History,
            contentDescription = null,
            modifier = Modifier.size(44.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "No hay mandados anteriores.",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ColumnScope.HistoryFailedState(
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
            text = "No se pudo cargar el historial.",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        OutlinedButton(
            onClick = onRetry,
            modifier = Modifier.semantics {
                contentDescription = "Reintentar carga del historial"
            },
        ) {
            Icon(Icons.Outlined.Refresh, contentDescription = null)
            Text("Reintentar")
        }
    }
}

@Composable
private fun ColumnScope.HistoryLoadedState(
    sessions: List<ListSessionWithItems>,
    timestampFormatter: MandadoHistoryTimestampFormatter,
    onOpenDetail: (ListSessionWithItems) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
    ) {
        items(
            items = sessions,
            key = { sessionWithItems -> sessionWithItems.session.id.value },
        ) { sessionWithItems ->
            MandadoHistoryRow(
                sessionWithItems = sessionWithItems,
                timestampFormatter = timestampFormatter,
                onClick = { onOpenDetail(sessionWithItems) },
            )
        }
    }
}

@Composable
private fun MandadoHistoryRow(
    sessionWithItems: ListSessionWithItems,
    timestampFormatter: MandadoHistoryTimestampFormatter,
    onClick: () -> Unit,
) {
    val session = sessionWithItems.session
    val itemCountLabel = itemCountLabel(sessionWithItems.items.size)
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription =
                    "Abrir mandado del ${timestampFormatter.format(session.startedAt)}, " +
                        itemCountLabel
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.History,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = timestampFormatter.format(session.startedAt),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = itemCountLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun itemCountLabel(itemCount: Int): String = if (itemCount == 1) {
    "1 producto"
} else {
    "$itemCount productos"
}
