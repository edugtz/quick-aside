package com.edu.quickaside.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.KeyboardVoice
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.edu.quickaside.application.capture.CaptureReader
import com.edu.quickaside.application.capture.CaptureSubmission
import com.edu.quickaside.application.capture.CaptureSubmissionResult
import com.edu.quickaside.application.capture.CaptureTranscriptCorrector
import com.edu.quickaside.application.lists.ListStore
import com.edu.quickaside.application.speech.AndroidSpeechTranscriberFactory
import com.edu.quickaside.application.speech.MicrophonePermissionController
import com.edu.quickaside.application.speech.SpeechTranscriberFactory
import com.edu.quickaside.domain.capture.Capture
import com.edu.quickaside.domain.capture.CaptureInput
import com.edu.quickaside.ui.memory.CaptureTimestampFormatter
import com.edu.quickaside.ui.memory.TranscriptCorrectionEditor
import com.edu.quickaside.ui.lists.ListsScreen
import com.edu.quickaside.ui.lists.MandadoScreen
import com.edu.quickaside.ui.navigation.AppDestination
import com.edu.quickaside.ui.voice.VoiceCaptureScreen
import com.edu.quickaside.ui.voice.rememberAndroidMicrophonePermissionController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private enum class ListsRoute {
    Root,
    Mandado,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAsideApp(
    captureSubmission: CaptureSubmission,
    captureReader: CaptureReader,
    listStore: ListStore? = null,
    captureTranscriptCorrector: CaptureTranscriptCorrector? = null,
    speechTranscriberFactory: SpeechTranscriberFactory? = null,
    microphonePermissionController: MicrophonePermissionController? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val resolvedSpeechTranscriberFactory = speechTranscriberFactory ?: remember(context) {
        AndroidSpeechTranscriberFactory(context)
    }
    val resolvedMicrophonePermissionController = microphonePermissionController
        ?: rememberAndroidMicrophonePermissionController()
    var currentDestination by remember { mutableStateOf(AppDestination.Inicio) }
    var listsRoute by remember { mutableStateOf(ListsRoute.Root) }
    var captureRequested by remember { mutableStateOf(false) }
    var historyRefreshToken by remember { mutableStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val requestCapture = { captureRequested = true }
    val onVoiceCaptureSaved = {
        historyRefreshToken += 1
        captureRequested = false
        scope.launch {
            snackbarHostState.showSnackbar("Captura guardada")
        }
        Unit
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (!captureRequested) {
                val showingMandado =
                    currentDestination == AppDestination.Listas && listsRoute == ListsRoute.Mandado
                CenterAlignedTopAppBar(
                    title = { Text(if (showingMandado) "Mandado" else currentDestination.label) },
                    navigationIcon = {
                        if (showingMandado) {
                            IconButton(
                                onClick = { listsRoute = ListsRoute.Root },
                                modifier = Modifier.semantics {
                                    contentDescription = "Volver a Listas"
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = null,
                                )
                            }
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (!captureRequested) {
                NavigationBar {
                    AppDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = destination == currentDestination,
                            onClick = {
                                currentDestination = destination
                                listsRoute = ListsRoute.Root
                            },
                            icon = {
                                Icon(destination.icon, contentDescription = destination.label)
                            },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (!captureRequested) {
                FloatingActionButton(
                    onClick = requestCapture,
                    modifier = Modifier.semantics { contentDescription = "Capturar" },
                ) {
                    Icon(Icons.Outlined.KeyboardVoice, contentDescription = null)
                }
            }
        },
    ) { padding ->
        if (captureRequested) {
            VoiceCaptureScreen(
                padding = padding,
                captureSubmission = captureSubmission,
                speechTranscriberFactory = resolvedSpeechTranscriberFactory,
                microphonePermissionController = resolvedMicrophonePermissionController,
                onDismiss = { captureRequested = false },
                onSaved = onVoiceCaptureSaved,
            )
        } else {
            ManagementScreen(
                destination = currentDestination,
                listsRoute = listsRoute,
                padding = padding,
                onCapture = requestCapture,
                captureSubmission = captureSubmission,
                captureReader = captureReader,
                captureTranscriptCorrector = captureTranscriptCorrector,
                historyRefreshToken = historyRefreshToken,
                onCaptureSaved = { historyRefreshToken += 1 },
                snackbarHostState = snackbarHostState,
                listStore = listStore,
                onOpenMandado = { listsRoute = ListsRoute.Mandado },
                onBackToLists = { listsRoute = ListsRoute.Root },
            )
        }
    }
}

@Composable
private fun ManagementScreen(
    destination: AppDestination,
    listsRoute: ListsRoute,
    padding: PaddingValues,
    onCapture: () -> Unit,
    captureSubmission: CaptureSubmission,
    captureReader: CaptureReader,
    captureTranscriptCorrector: CaptureTranscriptCorrector?,
    historyRefreshToken: Int,
    onCaptureSaved: () -> Unit,
    snackbarHostState: SnackbarHostState,
    listStore: ListStore?,
    onOpenMandado: () -> Unit,
    onBackToLists: () -> Unit,
) {
    if (destination == AppDestination.Listas) {
        when (listsRoute) {
            ListsRoute.Root -> ListsScreen(
                padding = padding,
                onOpenMandado = onOpenMandado,
            )

            ListsRoute.Mandado -> MandadoScreen(
                padding = padding,
                listStore = listStore,
                snackbarHostState = snackbarHostState,
                onBack = onBackToLists,
            )
        }
        return
    }

    if (destination == AppDestination.Memoria) {
        CaptureHistoryScreen(
            padding = padding,
            captureReader = captureReader,
            captureTranscriptCorrector = captureTranscriptCorrector,
            snackbarHostState = snackbarHostState,
            refreshToken = historyRefreshToken,
        )
        return
    }

    val (headline, description) = when (destination) {
        AppDestination.Inicio -> "Captura lo que recuerdas" to "Habla o escribe algo rápido; la organización llegará en una próxima etapa."
        AppDestination.Pendientes -> "Tus pendientes" to "Personal y Trabajo aparecerán aquí cuando la gestión de tareas esté lista."
        AppDestination.Listas -> "Tus listas" to "Mandado y Compras tendrán un lugar claro para consultar y actualizar."
        AppDestination.Memoria -> error("Memoria is rendered by CaptureHistoryScreen")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(headline, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(description, style = MaterialTheme.typography.bodyLarge)
        if (destination == AppDestination.Inicio) {
            FilledIconButton(
                onClick = onCapture,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(104.dp)
                    .semantics { contentDescription = "Hablar" },
            ) {
                Icon(
                    imageVector = Icons.Outlined.KeyboardVoice,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                )
            }
            Text("Toca para hablar", modifier = Modifier.align(Alignment.CenterHorizontally))
            TextCaptureField(
                captureSubmission = captureSubmission,
                snackbarHostState = snackbarHostState,
                onCaptureSaved = onCaptureSaved,
            )
        }
        SummaryCard(destination)
    }
}

@Composable
private fun TextCaptureField(
    captureSubmission: CaptureSubmission,
    snackbarHostState: SnackbarHostState,
    onCaptureSaved: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val submit = {
        if (!isSaving) {
            val submittedText = text
            scope.launch {
                isSaving = true
                val message = when (val result = captureSubmission.submit(submittedText)) {
                    CaptureSubmissionResult.Blank -> "Escribe algo para guardar"
                    is CaptureSubmissionResult.Saved -> {
                        text = ""
                        onCaptureSaved()
                        "Captura guardada"
                    }

                    is CaptureSubmissionResult.Failed -> "No se pudo guardar la captura"
                }
                isSaving = false
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        modifier = Modifier.fillMaxWidth(),
        enabled = !isSaving,
        singleLine = true,
        label = { Text("¿Qué necesitas recordar?") },
        placeholder = { Text("Escribe algo rápido") },
        trailingIcon = {
            IconButton(
                onClick = submit,
                enabled = !isSaving && text.isNotBlank(),
                modifier = Modifier.semantics {
                    contentDescription = "Enviar captura"
                },
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Send,
                    contentDescription = null,
                )
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
    )
}

private sealed interface CaptureHistoryState {
    data object Loading : CaptureHistoryState

    data class Loaded(val captures: List<Capture>) : CaptureHistoryState

    data object Failed : CaptureHistoryState
}

@Composable
private fun CaptureHistoryScreen(
    padding: PaddingValues,
    captureReader: CaptureReader,
    captureTranscriptCorrector: CaptureTranscriptCorrector?,
    snackbarHostState: SnackbarHostState,
    refreshToken: Int,
) {
    var state by remember { mutableStateOf<CaptureHistoryState>(CaptureHistoryState.Loading) }
    var editorCapture by remember { mutableStateOf<Capture?>(null) }
    val timestampFormatter = remember { CaptureTimestampFormatter() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(captureReader, refreshToken) {
        state = CaptureHistoryState.Loading
        try {
            state = CaptureHistoryState.Loaded(captureReader.readRecent())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            state = CaptureHistoryState.Failed
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Capturas recientes",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Lo último que guardaste en Quick Aside.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when (val currentState = state) {
            CaptureHistoryState.Loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator()
                    Text("Cargando capturas…")
                }
            }

            CaptureHistoryState.Failed -> {
                Text(
                    text = "No se pudieron cargar tus capturas.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            is CaptureHistoryState.Loaded -> {
                if (currentState.captures.isEmpty()) {
                    CaptureHistoryEmptyState()
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                    ) {
                        items(
                            items = currentState.captures,
                            key = { capture -> capture.id.value },
                        ) { capture ->
                            CaptureHistoryCard(
                                capture = capture,
                                timestampFormatter = timestampFormatter,
                                onEdit = if (captureTranscriptCorrector != null) {
                                    { editorCapture = capture }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    val selectedCapture = editorCapture
    val corrector = captureTranscriptCorrector
    if (selectedCapture != null && corrector != null) {
        TranscriptCorrectionEditor(
            capture = selectedCapture,
            corrector = corrector,
            onDismiss = { editorCapture = null },
            onSaved = { savedCapture ->
                state = when (val currentState = state) {
                    is CaptureHistoryState.Loaded -> CaptureHistoryState.Loaded(
                        currentState.captures.map { capture ->
                            if (capture.id == savedCapture.id) savedCapture else capture
                        },
                    )

                    else -> currentState
                }
                editorCapture = null
                scope.launch {
                    snackbarHostState.showSnackbar("Corrección guardada")
                }
            },
        )
    }
}

@Composable
private fun CaptureHistoryEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.History,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "Aún no hay capturas",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Las capturas que guardes desde Inicio aparecerán aquí.",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun CaptureHistoryCard(
    capture: Capture,
    timestampFormatter: CaptureTimestampFormatter,
    onEdit: (() -> Unit)?,
) {
    val (kindLabel, icon, originalText) = when (val input = capture.originalInput) {
        is CaptureInput.Text -> Triple("Texto", Icons.Outlined.Description, input.originalText)
        is CaptureInput.Voice -> Triple(
            "Voz",
            Icons.Outlined.KeyboardVoice,
            capture.effectiveTranscript ?: input.originalTranscript,
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = kindLabel,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = originalText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "$kindLabel · ${timestampFormatter.format(capture.capturedAt)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onEdit != null && capture.originalInput is CaptureInput.Voice) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.semantics {
                        contentDescription = "Editar transcript"
                    },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = null,
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(destination: AppDestination) {
    val detail = when (destination) {
        AppDestination.Inicio -> "Mandado, próximo evento y pendientes se resumirán aquí."
        AppDestination.Pendientes -> "Sin pendientes todavía."
        AppDestination.Listas -> "Sin listas todavía."
        AppDestination.Memoria -> "Sin recuerdos todavía."
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Próximamente", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(detail)
        }
    }
}
