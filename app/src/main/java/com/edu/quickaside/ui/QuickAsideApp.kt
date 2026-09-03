package com.edu.quickaside.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.KeyboardVoice
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
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
import com.edu.quickaside.application.capture.CaptureSubmission
import com.edu.quickaside.application.capture.CaptureSubmissionResult
import com.edu.quickaside.ui.navigation.AppDestination
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAsideApp(captureSubmission: CaptureSubmission) {
    var currentDestination by remember { mutableStateOf(AppDestination.Inicio) }
    var captureRequested by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val requestCapture = { captureRequested = true }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(title = { Text(currentDestination.label) })
        },
        bottomBar = {
            NavigationBar {
                AppDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = destination == currentDestination,
                        onClick = { currentDestination = destination },
                        icon = {
                            Icon(destination.icon, contentDescription = destination.label)
                        },
                        label = { Text(destination.label) },
                    )
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
            CapturePlaceholder(
                padding = padding,
                onDismiss = { captureRequested = false },
            )
        } else {
            ManagementScreen(
                destination = currentDestination,
                padding = padding,
                onCapture = requestCapture,
                captureSubmission = captureSubmission,
                snackbarHostState = snackbarHostState,
            )
        }
    }
}

@Composable
private fun ManagementScreen(
    destination: AppDestination,
    padding: PaddingValues,
    onCapture: () -> Unit,
    captureSubmission: CaptureSubmission,
    snackbarHostState: SnackbarHostState,
) {
    val (headline, description) = when (destination) {
        AppDestination.Inicio -> "Captura lo que recuerdas" to "Habla o escribe algo rápido; la organización llegará en una próxima etapa."
        AppDestination.Pendientes -> "Tus pendientes" to "Personal y Trabajo aparecerán aquí cuando la gestión de tareas esté lista."
        AppDestination.Listas -> "Tus listas" to "Mandado y Compras tendrán un lugar claro para consultar y actualizar."
        AppDestination.Memoria -> "Tu memoria" to "Busca notas, registros e historial desde una sola vista."
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
            )
        }
        SummaryCard(destination)
    }
}

@Composable
private fun TextCaptureField(
    captureSubmission: CaptureSubmission,
    snackbarHostState: SnackbarHostState,
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

@Composable
private fun CapturePlaceholder(padding: PaddingValues, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.KeyboardVoice,
            contentDescription = null,
            modifier = Modifier.size(88.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(20.dp))
        Text("Capturar", style = MaterialTheme.typography.headlineSmall)
        Text("La captura de voz y texto se incorporará en una próxima etapa.")
        Spacer(Modifier.height(16.dp))
        AssistChip(onClick = onDismiss, label = { Text("Volver a gestionar") })
    }
}
