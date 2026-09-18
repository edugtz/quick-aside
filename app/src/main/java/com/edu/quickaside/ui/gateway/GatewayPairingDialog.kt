package com.edu.quickaside.ui.gateway

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.edu.quickaside.application.gateway.DevicePairer
import com.edu.quickaside.application.gateway.DevicePairingFailureReason
import com.edu.quickaside.application.gateway.DevicePairingResult
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

@Composable
fun GatewayPairingDialog(
    pairer: DevicePairer,
    onDismiss: () -> Unit,
    onPaired: () -> Unit,
) {
    var code by remember { mutableStateOf("") }
    var isPairing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!isPairing) onDismiss() },
        title = { Text("Vincular Quick Aside") },
        text = {
            androidx.compose.foundation.layout.Column {
                Text("Ingresa el código temporal generado por tu gateway privado.")
                OutlinedTextField(
                    value = code,
                    onValueChange = {
                        code = it
                        error = null
                    },
                    enabled = !isPairing,
                    singleLine = true,
                    label = { Text("Código de vinculación") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                )
                if (error != null) Text(checkNotNull(error))
            }
        },
        confirmButton = {
            Button(
                enabled = !isPairing && code.isNotBlank(),
                onClick = {
                    scope.launch {
                        isPairing = true
                        try {
                            when (val result = pairer.pair(code)) {
                                is DevicePairingResult.Success -> {
                                    code = ""
                                    onPaired()
                                }

                                is DevicePairingResult.Failure -> {
                                    error = result.reason.userMessage()
                                }
                            }
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } finally {
                            isPairing = false
                        }
                    }
                },
            ) {
                Text(if (isPairing) "Vinculando…" else "Vincular")
            }
        },
        dismissButton = {
            TextButton(enabled = !isPairing, onClick = onDismiss) {
                Text("Cancelar")
            }
        },
    )
}

private fun DevicePairingFailureReason.userMessage(): String = when (this) {
    DevicePairingFailureReason.PAIRING_FAILED -> "El código no es válido o ya expiró."
    DevicePairingFailureReason.RATE_LIMITED -> "Demasiados intentos. Intenta más tarde."
    DevicePairingFailureReason.DNS_FAILURE,
    DevicePairingFailureReason.NETWORK_UNAVAILABLE,
    DevicePairingFailureReason.TIMEOUT,
    -> "No se pudo alcanzar el gateway privado."

    DevicePairingFailureReason.TLS_FAILURE -> "No se pudo validar la conexión segura."
    DevicePairingFailureReason.REQUEST_TOO_LARGE,
    DevicePairingFailureReason.INVALID_REQUEST,
    DevicePairingFailureReason.UNEXPECTED_RESPONSE,
    DevicePairingFailureReason.MALFORMED_RESPONSE,
    -> "No se pudo vincular el dispositivo."
}
