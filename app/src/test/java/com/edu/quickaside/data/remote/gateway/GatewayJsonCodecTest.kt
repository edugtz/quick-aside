package com.edu.quickaside.data.remote.gateway

import com.edu.quickaside.application.capture.AIInterpretationRequest
import com.edu.quickaside.domain.capture.CapturePlanActionDraft
import com.edu.quickaside.domain.tasks.TaskSpace
import java.time.Instant
import java.time.LocalDate
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class GatewayJsonCodecTest {
    @Test
    fun interpretRequestUsesTrustedCapturedAtAndTimezoneWithoutSourceCaptureId() {
        val bytes = GatewayJsonCodec.encodeInterpretRequest(
            AIInterpretationRequest(
                inputText = "Mañana revisa el PR",
                capturedAt = Instant.parse("2026-09-18T20:00:00Z"),
                timeZone = "America/Mexico_City",
            ),
        )
        val json = JSONObject(bytes.toString(Charsets.UTF_8))

        assertEquals("Mañana revisa el PR", json.getString("inputText"))
        assertEquals("2026-09-18T20:00:00Z", json.getString("capturedAt"))
        assertEquals("America/Mexico_City", json.getString("timeZone"))
        assertFalse(json.has("sourceCaptureId"))
    }

    @Test
    fun successResponseDecodesEverySupportedActionInOrder() {
        val candidate = GatewayJsonCodec.decodeInterpretResponse(
            """{"actions":[{"type":"AddListItem","listDefinitionId":"mandado","text":"Leche"},{"type":"CreateTask","space":"TRABAJO","title":"Revisar PR","dueDate":"2026-09-19"},{"type":"CreateNote","text":"Nota"},{"type":"CreateStructuredLog","fields":{"exercise":"press inclinado","weight":"210 lbs"}},{"type":"UndoLast"}]}""".toByteArray(),
        )

        assertEquals(
            listOf(
                CapturePlanActionDraft.AddListItem("mandado", "Leche"),
                CapturePlanActionDraft.CreateTask(
                    TaskSpace.TRABAJO,
                    "Revisar PR",
                    LocalDate.of(2026, 9, 19),
                ),
                CapturePlanActionDraft.CreateNote("Nota"),
                CapturePlanActionDraft.CreateStructuredLog(
                    linkedMapOf("exercise" to "press inclinado", "weight" to "210 lbs"),
                ),
                CapturePlanActionDraft.UndoLast,
            ),
            candidate.actions,
        )
    }

    @Test
    fun malformedOrUnexpectedSuccessResponseIsRejected() {
        listOf(
            "not-json",
            "{}",
            """{"actions":[{"type":"CreateNote","text":"Nota","extra":true}]}""",
            """{"actions":[{"type":"Unknown"}]}""",
        ).forEach { body ->
            assertThrows(GatewayMalformedResponseException::class.java) {
                GatewayJsonCodec.decodeInterpretResponse(body.toByteArray())
            }
        }
    }
}
