package com.edu.quickaside.data.remote.gateway

import com.edu.quickaside.application.capture.AIInterpretationCandidate
import com.edu.quickaside.application.capture.AIInterpretationRequest
import com.edu.quickaside.domain.capture.CapturePlanActionDraft
import com.edu.quickaside.domain.tasks.TaskSpace
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import org.json.JSONException
import org.json.JSONObject

internal class GatewayMalformedResponseException(
    cause: Throwable? = null,
) : Exception("malformed gateway response", cause)

internal object GatewayJsonCodec {
    fun encodeInterpretRequest(request: AIInterpretationRequest): ByteArray =
        JSONObject()
            .put("inputText", request.inputText)
            .put("capturedAt", request.capturedAt.toString())
            .put("timeZone", request.timeZone)
            .toString()
            .toByteArray(StandardCharsets.UTF_8)

    fun encodePairRequest(
        pairingCode: String,
        deviceId: String,
        label: String,
        publicKeyPem: String,
    ): ByteArray = JSONObject()
        .put("pairingCode", pairingCode)
        .put("deviceId", deviceId)
        .put("label", label)
        .put("publicKeyPem", publicKeyPem)
        .toString()
        .toByteArray(StandardCharsets.UTF_8)

    fun decodeInterpretResponse(bytes: ByteArray): AIInterpretationCandidate = try {
        val root = JSONObject(strictUtf8(bytes))
        requireKeys(root, setOf("actions"))
        val actions = root.getJSONArray("actions")
        AIInterpretationCandidate(
            actions = List(actions.length()) { index -> decodeAction(actions.getJSONObject(index)) },
        )
    } catch (failure: Exception) {
        if (failure is GatewayMalformedResponseException) throw failure
        throw GatewayMalformedResponseException(failure)
    }

    fun decodePairResponse(bytes: ByteArray): String = try {
        val root = JSONObject(strictUtf8(bytes))
        requireKeys(root, setOf("deviceId", "status"))
        val deviceId = requireString(root, "deviceId")
        val status = requireString(root, "status")
        if (deviceId.isBlank() || status != "active") throw JSONException("invalid pair response")
        deviceId
    } catch (failure: Exception) {
        if (failure is GatewayMalformedResponseException) throw failure
        throw GatewayMalformedResponseException(failure)
    }

    private fun decodeAction(action: JSONObject): CapturePlanActionDraft = when (requireString(action, "type")) {
        "AddListItem" -> {
            requireKeys(action, setOf("type", "listDefinitionId", "text"))
            CapturePlanActionDraft.AddListItem(
                listDefinitionId = requireString(action, "listDefinitionId"),
                text = requireString(action, "text"),
            )
        }

        "CreateTask" -> {
            requireKeys(action, setOf("type", "space", "title", "dueDate"), optional = setOf("dueDate"))
            val dueDate = if (!action.has("dueDate") || action.isNull("dueDate")) {
                null
            } else {
                LocalDate.parse(requireString(action, "dueDate"))
            }
            CapturePlanActionDraft.CreateTask(
                space = TaskSpace.valueOf(requireString(action, "space")),
                title = requireString(action, "title"),
                dueDate = dueDate,
            )
        }

        "CreateNote" -> {
            requireKeys(action, setOf("type", "text"))
            CapturePlanActionDraft.CreateNote(text = requireString(action, "text"))
        }

        "CreateStructuredLog" -> {
            requireKeys(action, setOf("type", "fields"))
            val fieldsObject = action.getJSONObject("fields")
            val fields = linkedMapOf<String, String>()
            fieldsObject.keys().forEach { key -> fields[key] = requireString(fieldsObject, key) }
            CapturePlanActionDraft.CreateStructuredLog(fields)
        }

        "UndoLast" -> {
            requireKeys(action, setOf("type"))
            CapturePlanActionDraft.UndoLast
        }

        else -> throw JSONException("unsupported action type")
    }

    private fun requireKeys(
        objectValue: JSONObject,
        expected: Set<String>,
        optional: Set<String> = emptySet(),
    ) {
        val actual = objectValue.keys().asSequence().toSet()
        if (!actual.containsAll(expected - optional) || (actual - expected).isNotEmpty()) {
            throw JSONException("unexpected JSON fields")
        }
    }

    private fun requireString(
        objectValue: JSONObject,
        key: String,
    ): String {
        val rawValue = objectValue.get(key)
        if (rawValue !is String) throw JSONException("expected JSON string")
        return rawValue
    }

    private fun strictUtf8(bytes: ByteArray): String =
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
}
