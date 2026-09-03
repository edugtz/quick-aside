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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardVoice
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.edu.quickaside.ui.navigation.AppDestination

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAsideApp() {
    var currentDestination by remember { mutableStateOf(AppDestination.Inicio) }
    var captureRequested by remember { mutableStateOf(false) }

    Scaffold(
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
                    onClick = { captureRequested = true },
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
            ManagementScreen(currentDestination, padding)
        }
    }
}

@Composable
private fun ManagementScreen(destination: AppDestination, padding: PaddingValues) {
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
                onClick = {},
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
        }
        SummaryCard(destination)
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
