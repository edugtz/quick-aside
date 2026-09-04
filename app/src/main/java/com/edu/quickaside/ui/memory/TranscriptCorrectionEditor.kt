package com.edu.quickaside.ui.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.edu.quickaside.application.capture.CaptureTranscriptCorrectionResult
import com.edu.quickaside.application.capture.CaptureTranscriptCorrector
import com.edu.quickaside.domain.capture.Capture
import com.edu.quickaside.domain.capture.CaptureInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TranscriptCorrectionEditor(
    capture: Capture,
    corrector: CaptureTranscriptCorrector,
    onDismiss: () -> Unit,
    onSaved: (Capture) -> Unit,
) {
    val voiceInput = when (val input = capture.originalInput) {
        is CaptureInput.Voice -> input
        is CaptureInput.Text -> return
    }
    val initialTranscript = capture.effectiveTranscript ?: voiceInput.originalTranscript
    var editedTranscript by remember(capture.id, initialTranscript) {
        mutableStateOf(initialTranscript)
    }
    var isSaving by remember(capture.id) { mutableStateOf(false) }
    var errorMessage by remember(capture.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val canSave = !isSaving &&
        editedTranscript.isNotBlank() &&
        editedTranscript != initialTranscript

    val save = {
        if (canSave) {
            scope.launch {
                isSaving = true
                errorMessage = null
                val result = try {
                    corrector.correct(capture.id, editedTranscript)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    CaptureTranscriptCorrectionResult.Failed(
                        IllegalStateException("Transcript correction failed"),
                    )
                }
                isSaving = false
                when (result) {
                    is CaptureTranscriptCorrectionResult.Saved -> onSaved(result.capture)
                    CaptureTranscriptCorrectionResult.Blank -> {
                        errorMessage = "Escribe una corrección para guardar."
                    }

                    CaptureTranscriptCorrectionResult.Missing -> {
                        errorMessage = "No se encontró la captura."
                    }

                    CaptureTranscriptCorrectionResult.NotVoice -> {
                        errorMessage = "Solo las capturas de voz se pueden editar."
                    }

                    is CaptureTranscriptCorrectionResult.Failed -> {
                        errorMessage = "No se pudo guardar la corrección."
                    }
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!isSaving) onDismiss() },
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Editar transcript",
                style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = editedTranscript,
                onValueChange = {
                    editedTranscript = it
                    errorMessage = null
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaving,
                label = { Text("Transcript") },
                minLines = 4,
                maxLines = 8,
                isError = errorMessage != null,
                supportingText = errorMessage?.let { message ->
                    { Text(message) }
                },
            )
            Text(
                text = "Original: ${voiceInput.originalTranscript}",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !isSaving,
                ) {
                    Text("Cancelar")
                }
                Button(
                    onClick = save,
                    enabled = canSave,
                ) {
                    Text("Guardar")
                }
            }
        }
    }
}
