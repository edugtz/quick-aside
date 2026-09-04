package com.edu.quickaside.ui.voice

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardVoice
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.edu.quickaside.application.capture.CaptureSubmission
import com.edu.quickaside.application.capture.CaptureSubmissionResult
import com.edu.quickaside.application.speech.AndroidMicrophonePermissionController
import com.edu.quickaside.application.speech.MicrophonePermissionController
import com.edu.quickaside.application.speech.SpeechTranscriber
import com.edu.quickaside.application.speech.SpeechTranscriberError
import com.edu.quickaside.application.speech.SpeechTranscriberEvent
import com.edu.quickaside.application.speech.SpeechTranscriberFactory
import com.edu.quickaside.application.speech.SpeechTranscriberListener
import kotlinx.coroutines.launch

private val ListeningBackground = Color(0xFF061A2F)
private val ListeningSurface = Color(0xFF102A45)
private val ListeningPrimary = Color(0xFF8EC3FF)
private val ListeningOnSurface = Color(0xFFF4F7FF)
private val ListeningOnSurfaceVariant = Color(0xFFB7C8DC)

@Composable
fun rememberAndroidMicrophonePermissionController(): MicrophonePermissionController {
    val context = LocalContext.current
    var resultHandler by remember { mutableStateOf<(Boolean) -> Unit>({}) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val handler = resultHandler
        resultHandler = {}
        handler(granted)
    }

    return remember(context, permissionLauncher) {
        AndroidMicrophonePermissionController(
            context = context,
            requestPermission = { onResult ->
                resultHandler = onResult
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
        )
    }
}

private sealed interface VoiceCaptureUiState {
    data object PermissionNeeded : VoiceCaptureUiState

    data object PermissionRequesting : VoiceCaptureUiState

    data class PermissionDenied(
        val canRetry: Boolean,
    ) : VoiceCaptureUiState

    data object Starting : VoiceCaptureUiState

    data object Ready : VoiceCaptureUiState

    data object Listening : VoiceCaptureUiState

    data object Finalizing : VoiceCaptureUiState

    data object Saving : VoiceCaptureUiState

    data class Failed(
        val message: String,
        val canRetry: Boolean,
    ) : VoiceCaptureUiState
}

@Composable
fun VoiceCaptureScreen(
    padding: PaddingValues,
    captureSubmission: CaptureSubmission,
    speechTranscriberFactory: SpeechTranscriberFactory,
    microphonePermissionController: MicrophonePermissionController,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    var state by remember { mutableStateOf<VoiceCaptureUiState>(VoiceCaptureUiState.PermissionNeeded) }
    var transcript by remember { mutableStateOf("") }
    var activeTranscriber by remember { mutableStateOf<SpeechTranscriber?>(null) }
    var finalResultHandled by remember { mutableStateOf(false) }
    var isActive by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    fun releaseTranscriber(cancel: Boolean) {
        val transcriber = activeTranscriber ?: return
        if (cancel) {
            transcriber.cancel()
        }
        transcriber.destroy()
        activeTranscriber = null
    }

    fun handleSpeechEvent(event: SpeechTranscriberEvent) {
        if (!isActive || state is VoiceCaptureUiState.Saving || state is VoiceCaptureUiState.Failed) {
            return
        }

        when (event) {
            SpeechTranscriberEvent.Ready -> state = VoiceCaptureUiState.Ready
            SpeechTranscriberEvent.Listening -> state = VoiceCaptureUiState.Listening
            SpeechTranscriberEvent.Finalizing -> state = VoiceCaptureUiState.Finalizing
            is SpeechTranscriberEvent.PartialTranscript -> {
                transcript = event.transcript
                state = VoiceCaptureUiState.Listening
            }

            is SpeechTranscriberEvent.FinalTranscript -> {
                if (finalResultHandled) {
                    return
                }
                finalResultHandled = true
                transcript = event.transcript
                releaseTranscriber(cancel = false)
                state = VoiceCaptureUiState.Saving
                scope.launch {
                    when (val result = captureSubmission.submitVoice(event.transcript)) {
                        CaptureSubmissionResult.Blank -> {
                            state = VoiceCaptureUiState.Failed(
                                message = "No escuché una frase. Intenta de nuevo.",
                                canRetry = true,
                            )
                        }

                        is CaptureSubmissionResult.Saved -> onSaved()
                        is CaptureSubmissionResult.Failed -> {
                            state = VoiceCaptureUiState.Failed(
                                message = "No se pudo guardar la captura.",
                                canRetry = true,
                            )
                        }
                    }
                }
            }

            is SpeechTranscriberEvent.Error -> {
                releaseTranscriber(cancel = false)
                state = VoiceCaptureUiState.Failed(
                    message = event.reason.userMessage(),
                    canRetry = event.reason != SpeechTranscriberError.Unavailable,
                )
            }
        }
    }

    fun startRecognition() {
        if (!isActive || activeTranscriber != null) {
            return
        }

        transcript = ""
        finalResultHandled = false
        state = VoiceCaptureUiState.Starting
        try {
            val transcriber = speechTranscriberFactory.create()
            activeTranscriber = transcriber
            transcriber.start(SpeechTranscriberListener(::handleSpeechEvent))
        } catch (_: Exception) {
            releaseTranscriber(cancel = false)
            state = VoiceCaptureUiState.Failed(
                message = "No se pudo iniciar el reconocimiento de voz.",
                canRetry = true,
            )
        }
    }

    fun requestOrStartRecognition() {
        if (!isActive || activeTranscriber != null || state is VoiceCaptureUiState.PermissionRequesting) {
            return
        }

        if (microphonePermissionController.isGranted()) {
            startRecognition()
            return
        }

        state = VoiceCaptureUiState.PermissionRequesting
        try {
            microphonePermissionController.request { granted ->
                if (!isActive) {
                    return@request
                }
                if (granted) {
                    startRecognition()
                } else {
                    state = VoiceCaptureUiState.PermissionDenied(
                        canRetry = microphonePermissionController.shouldShowRationale(),
                    )
                }
            }
        } catch (_: Exception) {
            state = VoiceCaptureUiState.PermissionDenied(canRetry = false)
        }
    }

    fun dismissCapture() {
        releaseTranscriber(cancel = true)
        onDismiss()
    }

    fun retryCapture() {
        releaseTranscriber(cancel = true)
        state = VoiceCaptureUiState.PermissionNeeded
        requestOrStartRecognition()
    }

    LaunchedEffect(Unit) {
        requestOrStartRecognition()
    }

    DisposableEffect(Unit) {
        onDispose {
            isActive = false
            releaseTranscriber(cancel = true)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = ListeningBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = ::dismissCapture,
                    enabled = state !is VoiceCaptureUiState.Saving,
                    modifier = Modifier.semantics {
                        contentDescription = "Cancelar captura"
                    },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = null,
                        tint = ListeningOnSurface,
                    )
                }
                Text(
                    text = "Captura",
                    modifier = Modifier.padding(start = 8.dp),
                    color = ListeningOnSurface,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = state.title(),
                    color = ListeningOnSurface,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = state.description(),
                    color = ListeningOnSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.size(28.dp))
                VoiceCaptureOrb(active = state.isListeningLike())
                Spacer(Modifier.size(24.dp))

                if (transcript.isNotBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = ListeningSurface,
                        ),
                    ) {
                        Text(
                            text = transcript,
                            modifier = Modifier.padding(18.dp),
                            color = ListeningOnSurface,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                } else if (state.isListeningLike()) {
                    Text(
                        text = "Habla cuando quieras",
                        color = ListeningOnSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }

                when (val currentState = state) {
                    VoiceCaptureUiState.PermissionNeeded -> {
                        Spacer(Modifier.size(20.dp))
                        Button(onClick = ::requestOrStartRecognition) {
                            Text("Permitir micrófono")
                        }
                    }

                    VoiceCaptureUiState.PermissionRequesting -> {
                        Spacer(Modifier.size(20.dp))
                        CircularProgressIndicator(color = ListeningPrimary)
                    }

                    is VoiceCaptureUiState.PermissionDenied -> {
                        Spacer(Modifier.size(20.dp))
                        if (currentState.canRetry) {
                            Button(onClick = ::retryCapture) {
                                Text("Intentar de nuevo")
                            }
                        } else {
                            Text(
                                text = "Concede el permiso desde Ajustes y vuelve a intentarlo.",
                                color = ListeningOnSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }

                    is VoiceCaptureUiState.Failed -> {
                        Spacer(Modifier.size(20.dp))
                        Text(
                            text = currentState.message,
                            color = ListeningOnSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (currentState.canRetry) {
                            Spacer(Modifier.size(12.dp))
                            Button(onClick = ::retryCapture) {
                                Icon(Icons.Outlined.Refresh, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text("Reintentar")
                            }
                        }
                    }

                    VoiceCaptureUiState.Starting,
                    VoiceCaptureUiState.Ready,
                    VoiceCaptureUiState.Listening,
                    VoiceCaptureUiState.Finalizing,
                    VoiceCaptureUiState.Saving,
                    -> Unit
                }
            }

            TextButton(
                onClick = ::dismissCapture,
                enabled = state !is VoiceCaptureUiState.Saving,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = ListeningPrimary,
                    disabledContentColor = ListeningOnSurfaceVariant,
                ),
            ) {
                Text("Cancelar")
            }
        }
    }
}

@Composable
private fun VoiceCaptureOrb(active: Boolean) {
    val outerColor = if (active) Color(0xFF1D5EA1) else Color(0xFF243C58)
    val middleColor = if (active) Color(0xFF2C8BD7) else Color(0xFF385573)
    val innerColor = if (active) Color(0xFF2460DB) else Color(0xFF4A6581)

    Box(
        modifier = Modifier
            .size(208.dp)
            .clip(CircleShape)
            .background(outerColor)
            .border(1.dp, ListeningPrimary.copy(alpha = 0.45f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(172.dp)
                .clip(CircleShape)
                .background(middleColor),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(132.dp)
                    .clip(CircleShape)
                    .background(innerColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.KeyboardVoice,
                    contentDescription = "Micrófono",
                    modifier = Modifier.size(58.dp),
                    tint = ListeningOnSurface,
                )
            }
        }
    }
}

private fun VoiceCaptureUiState.title(): String = when (this) {
    VoiceCaptureUiState.PermissionNeeded -> "Permiso de micrófono"
    VoiceCaptureUiState.PermissionRequesting -> "Solicitando permiso…"
    is VoiceCaptureUiState.PermissionDenied -> "El micrófono está bloqueado"
    VoiceCaptureUiState.Starting -> "Preparando…"
    VoiceCaptureUiState.Ready -> "Listo para escuchar…"
    VoiceCaptureUiState.Listening -> "Escuchando…"
    VoiceCaptureUiState.Finalizing -> "Procesando…"
    VoiceCaptureUiState.Saving -> "Guardando…"
    is VoiceCaptureUiState.Failed -> "No se pudo capturar"
}

private fun VoiceCaptureUiState.description(): String = when (this) {
    VoiceCaptureUiState.PermissionNeeded,
    VoiceCaptureUiState.PermissionRequesting,
    is VoiceCaptureUiState.PermissionDenied,
    -> "El micrófono se usa solo durante esta captura."

    VoiceCaptureUiState.Starting,
    VoiceCaptureUiState.Ready,
    VoiceCaptureUiState.Listening,
    VoiceCaptureUiState.Finalizing,
    VoiceCaptureUiState.Saving,
    is VoiceCaptureUiState.Failed,
    -> "Di lo que quieras guardar en tu memoria local."
}

private fun VoiceCaptureUiState.isListeningLike(): Boolean = when (this) {
    VoiceCaptureUiState.Starting,
    VoiceCaptureUiState.Ready,
    VoiceCaptureUiState.Listening,
    VoiceCaptureUiState.Finalizing,
    VoiceCaptureUiState.Saving,
    -> true

    VoiceCaptureUiState.PermissionNeeded,
    VoiceCaptureUiState.PermissionRequesting,
    is VoiceCaptureUiState.PermissionDenied,
    is VoiceCaptureUiState.Failed,
    -> false
}

private fun SpeechTranscriberError.userMessage(): String = when (this) {
    SpeechTranscriberError.NoMatch -> "No encontré una frase clara. Intenta de nuevo."
    SpeechTranscriberError.Timeout -> "No escuché nada a tiempo. Intenta de nuevo."
    SpeechTranscriberError.PermissionDenied -> "El micrófono no tiene permiso."
    SpeechTranscriberError.Busy -> "El reconocimiento está ocupado. Intenta de nuevo."
    SpeechTranscriberError.NetworkFailure -> "No se pudo conectar con el servicio de voz."
    SpeechTranscriberError.ServiceFailure -> "El servicio de voz no respondió."
    SpeechTranscriberError.Unavailable -> "El reconocimiento de voz no está disponible aquí."
}
