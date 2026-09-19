package com.edu.quickaside.data.remote.gateway

import com.edu.quickaside.application.capture.AIInterpretationRequest
import com.edu.quickaside.application.capture.CapturePlanValidationResult
import com.edu.quickaside.application.capture.CapturePlanValidator
import com.edu.quickaside.domain.capture.CapturePlanActionDraft
import com.edu.quickaside.domain.capture.CapturePlanDraft
import com.edu.quickaside.domain.tasks.TaskSpace
import java.time.Instant
import java.time.LocalDate
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun createNoteTextRequiresAJsonString() {
        listOf(
            "null" to """{"actions":[{"type":"CreateNote","text":null}]}""",
            "number" to """{"actions":[{"type":"CreateNote","text":12}]}""",
            "boolean" to """{"actions":[{"type":"CreateNote","text":true}]}""",
        ).forEach { (_, body) -> assertMalformed(body) }
    }

    @Test
    fun addListItemFieldsRequireJsonStrings() {
        listOf(
            """{"actions":[{"type":"AddListItem","listDefinitionId":12,"text":"Leche"}]}""",
            """{"actions":[{"type":"AddListItem","listDefinitionId":"mandado","text":false}]}""",
        ).forEach(::assertMalformed)
    }

    @Test
    fun createTaskFieldsRequireJsonStringsExceptNullableDueDate() {
        listOf(
            """{"actions":[{"type":"CreateTask","space":false,"title":"Revisar","dueDate":null}]}""",
            """{"actions":[{"type":"CreateTask","space":"TRABAJO","title":12,"dueDate":null}]}""",
            """{"actions":[{"type":"CreateTask","space":"TRABAJO","title":"Revisar","dueDate":12}]}""",
            """{"actions":[{"type":"CreateTask","space":"TRABAJO","title":"Revisar","dueDate":true}]}""",
            """{"actions":[{"type":"CreateTask","space":"TRABAJO","title":"Revisar","dueDate":{}}]}""",
            """{"actions":[{"type":"CreateTask","space":"TRABAJO","title":"Revisar","dueDate":[]}]}""",
        ).forEach(::assertMalformed)

        val candidate = GatewayJsonCodec.decodeInterpretResponse(
            """{"actions":[{"type":"CreateTask","space":"TRABAJO","title":"Revisar","dueDate":null}]}"""
                .toByteArray(),
        )
        assertEquals(
            CapturePlanActionDraft.CreateTask(TaskSpace.TRABAJO, "Revisar", null),
            candidate.actions.single(),
        )
    }

    @Test
    fun structuredLogValuesRequireJsonStrings() {
        assertMalformed(
            """{"actions":[{"type":"CreateStructuredLog","fields":{"weight":210}}]}""",
        )
    }

    @Test
    fun actionTypeRequiresAJsonString() {
        assertMalformed("""{"actions":[{"type":true,"text":"Nota"}]}""")
    }

    @Test
    fun nonStringContractValuesFailStructuralDecodeBeforeValidation() {
        val decodeResult = runCatching {
            GatewayJsonCodec.decodeInterpretResponse(
                """{"actions":[{"type":"CreateNote","text":42}]}""".toByteArray(),
            )
        }

        assertTrue(decodeResult.exceptionOrNull() is GatewayMalformedResponseException)
        assertFalse(decodeResult.isSuccess)
    }

    @Test
    fun semanticallyRepresentableOversizedValueSurvivesDecodeButFailsValidation() {
        val candidate = GatewayJsonCodec.decodeInterpretResponse(
            """{"actions":[{"type":"CreateNote","text":"${"x".repeat(4_001)}"}]}"""
                .toByteArray(),
        )

        val result = CapturePlanValidator().validate(
            CapturePlanDraft(
                sourceCaptureId = "capture-codec-validator-boundary",
                actions = candidate.actions,
            ),
        )

        assertTrue(result is CapturePlanValidationResult.Invalid)
        assertFalse(result is CapturePlanValidationResult.Valid)
    }

    private fun assertMalformed(body: String) {
        assertThrows(GatewayMalformedResponseException::class.java) {
            GatewayJsonCodec.decodeInterpretResponse(body.toByteArray())
        }
    }
}
