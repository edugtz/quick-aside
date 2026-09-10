package com.edu.quickaside.application.capture

import com.edu.quickaside.domain.capture.Capture
import com.edu.quickaside.domain.capture.CaptureInput
import com.edu.quickaside.domain.capture.CapturePlanAction
import com.edu.quickaside.domain.capture.CapturePlanActionDraft
import com.edu.quickaside.domain.common.CaptureId
import com.edu.quickaside.domain.common.ListDefinitionId
import com.edu.quickaside.domain.tasks.TaskSpace
import java.io.File
import java.lang.reflect.Modifier
import java.time.Instant
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureInterpreterTest {
    private val capturedAt = Instant.parse("2026-09-09T16:00:00Z")

    @Test
    fun textCaptureSendsExactOriginalTextToProvider() = runBlocking {
        val originalText = "  Compra Chobani, pollo y leche  "
        val provider = RecordingProvider(validNoteCandidate())

        val result = interpreter(provider).interpret(textCapture(originalText))

        assertTrue(result is CaptureInterpretationResult.Success)
        assertEquals(
            AIInterpretationRequest(inputText = originalText),
            provider.requests.single(),
        )
    }

    @Test
    fun voiceCaptureWithoutCorrectionSendsExactOriginalTranscript() = runBlocking {
        val originalTranscript = "  comprar leche mañana  "
        val provider = RecordingProvider(validNoteCandidate())

        interpreter(provider).interpret(voiceCapture(originalTranscript))

        assertEquals(
            AIInterpretationRequest(inputText = originalTranscript),
            provider.requests.single(),
        )
    }

    @Test
    fun correctedVoiceCaptureSendsCorrectionInsteadOfOriginalTranscript() = runBlocking {
        val provider = RecordingProvider(validNoteCandidate())
        val capture = Capture(
            id = CaptureId("voice-corrected"),
            originalInput = CaptureInput.Voice("comprar leche manana"),
            capturedAt = capturedAt,
            transcriptCorrection = "  Comprar leche mañana  ",
        )

        interpreter(provider).interpret(capture)

        assertEquals(
            AIInterpretationRequest(inputText = "  Comprar leche mañana  "),
            provider.requests.single(),
        )
    }

    @Test
    fun validSurroundingWhitespaceIsPreservedWhenPassedToProvider() = runBlocking {
        val input = "\t  Revisar PR mañana  \n"
        val provider = RecordingProvider(validNoteCandidate())

        interpreter(provider).interpret(textCapture(input))

        assertEquals(input, provider.requests.single().inputText)
    }

    @Test
    fun blankEffectiveInputReturnsBlankInputWithoutInvokingProvider() = runBlocking {
        val provider = RecordingProvider(validNoteCandidate())

        val result = interpreter(provider).interpret(textCapture(" \t\n "))

        assertEquals(CaptureInterpretationResult.BlankInput, result)
        assertTrue(provider.requests.isEmpty())
    }

    @Test
    fun providerRequestAndCandidateExposeOnlyProviderNeutralInterpretationData() {
        assertEquals(
            listOf("inputText"),
            AIInterpretationRequest::class.java.declaredFields
                .filterNot { it.isSynthetic || Modifier.isStatic(it.modifiers) }
                .map { it.name },
        )
        assertEquals(
            listOf("actions"),
            AIInterpretationCandidate::class.java.declaredFields
                .filterNot { it.isSynthetic || Modifier.isStatic(it.modifiers) }
                .map { it.name },
        )
    }

    @Test
    fun providerCandidateCannotSupplySourceCaptureId() = runBlocking {
        val trustedCaptureId = CaptureId("trusted-capture-id")
        val provider = RecordingProvider(validNoteCandidate())
        val capture = Capture(
            id = trustedCaptureId,
            originalInput = CaptureInput.Text("Guardar esto"),
            capturedAt = capturedAt,
        )

        val result = interpreter(provider).interpret(capture)
        val plan = (result as CaptureInterpretationResult.Success).plan

        assertEquals(trustedCaptureId, plan.sourceCaptureId)
        assertFalse(
            AIInterpretationCandidate::class.java.declaredFields
                .any { it.name == "sourceCaptureId" },
        )
    }

    @Test
    fun trustedCaptureIdBecomesPlanSourceIdWithoutNormalization() = runBlocking {
        val trustedCaptureId = CaptureId("  trusted-capture-id  ")
        val provider = RecordingProvider(validNoteCandidate())
        val capture = Capture(
            id = trustedCaptureId,
            originalInput = CaptureInput.Text("Contenido válido"),
            capturedAt = capturedAt,
        )

        val result = interpreter(provider).interpret(capture)
        val plan = (result as CaptureInterpretationResult.Success).plan

        assertEquals(trustedCaptureId.value, plan.sourceCaptureId.value)
    }

    @Test
    fun validProviderCandidateProducesSuccessWithValidatedPlan() = runBlocking {
        val provider = RecordingProvider(
            AIInterpretationCandidate(
                actions = listOf(CapturePlanActionDraft.CreateNote("  Nota exacta  ")),
            ),
        )

        val result = interpreter(provider).interpret(textCapture("Guardar nota"))

        val success = result as CaptureInterpretationResult.Success
        assertEquals("capture-text", success.plan.sourceCaptureId.value)
        assertEquals(
            listOf(CapturePlanAction.CreateNote("  Nota exacta  ")),
            success.plan.actions,
        )
    }

    @Test
    fun multiActionCandidatePreservesExactOrderAndFieldsEndToEnd() = runBlocking {
        val dueDate = LocalDate.of(2026, 9, 12)
        val fields = linkedMapOf(
            "  tipo  " to "  trabajo  ",
            "prioridad" to "alta ",
        )
        val actions = listOf(
            CapturePlanActionDraft.AddListItem("  compras  ", "  Cuerdas Fender  "),
            CapturePlanActionDraft.CreateTask(TaskSpace.TRABAJO, "  Revisar PR  ", dueDate),
            CapturePlanActionDraft.CreateNote("  Llamar al taller  "),
            CapturePlanActionDraft.CreateStructuredLog(fields),
            CapturePlanActionDraft.UndoLast,
        )
        val provider = RecordingProvider(AIInterpretationCandidate(actions))

        val result = interpreter(provider).interpret(textCapture("Interpretar todo"))

        val plan = (result as CaptureInterpretationResult.Success).plan
        assertEquals(
            listOf(
                CapturePlanAction.AddListItem(
                    ListDefinitionId("  compras  "),
                    "  Cuerdas Fender  ",
                ),
                CapturePlanAction.CreateTask(
                    TaskSpace.TRABAJO,
                    "  Revisar PR  ",
                    dueDate,
                ),
                CapturePlanAction.CreateNote("  Llamar al taller  "),
                CapturePlanAction.CreateStructuredLog(fields),
                CapturePlanAction.UndoLast,
            ),
            plan.actions,
        )
    }

    @Test
    fun emptyProviderActionListReturnsInvalidPlanWithEmptyActionsIssue() = runBlocking {
        val provider = RecordingProvider(AIInterpretationCandidate(emptyList()))

        val result = interpreter(provider).interpret(textCapture("Sin acciones"))

        val invalid = result as CaptureInterpretationResult.InvalidPlan
        assertEquals(
            listOf(
                CapturePlanValidationIssue.Plan(
                    CapturePlanValidationReason.EMPTY_ACTIONS,
                ),
            ),
            invalid.issues,
        )
    }

    @Test
    fun invalidAddListItemCandidateReturnsExpectedValidatorIssue() = runBlocking {
        val provider = RecordingProvider(
            AIInterpretationCandidate(
                listOf(CapturePlanActionDraft.AddListItem(" \t\n ", "Producto")),
            ),
        )

        val result = interpreter(provider).interpret(textCapture("Lista"))

        assertEquals(
            listOf(
                CapturePlanValidationIssue.Action(
                    actionIndex = 0,
                    reason = CapturePlanValidationReason.BLANK_LIST_DEFINITION_ID,
                ),
            ),
            (result as CaptureInterpretationResult.InvalidPlan).issues,
        )
    }

    @Test
    fun invalidNoteCandidateReturnsExpectedValidatorIssue() = runBlocking {
        val provider = RecordingProvider(
            AIInterpretationCandidate(
                listOf(CapturePlanActionDraft.CreateNote(" \t\n ")),
            ),
        )

        val result = interpreter(provider).interpret(textCapture("Nota"))

        assertEquals(
            listOf(
                CapturePlanValidationIssue.Action(
                    actionIndex = 0,
                    reason = CapturePlanValidationReason.BLANK_NOTE_TEXT,
                ),
            ),
            (result as CaptureInterpretationResult.InvalidPlan).issues,
        )
    }

    @Test
    fun validationIssueActionIndexesSurviveIntoInvalidPlan() = runBlocking {
        val provider = RecordingProvider(
            AIInterpretationCandidate(
                listOf(
                    CapturePlanActionDraft.CreateNote("valid"),
                    CapturePlanActionDraft.CreateTask(TaskSpace.PERSONAL, " \t\n "),
                    CapturePlanActionDraft.CreateNote(" \t\n "),
                ),
            ),
        )

        val result = interpreter(provider).interpret(textCapture("Varias acciones"))

        assertEquals(
            listOf(
                CapturePlanValidationIssue.Action(
                    actionIndex = 1,
                    reason = CapturePlanValidationReason.BLANK_TASK_TITLE,
                ),
                CapturePlanValidationIssue.Action(
                    actionIndex = 2,
                    reason = CapturePlanValidationReason.BLANK_NOTE_TEXT,
                ),
            ),
            (result as CaptureInterpretationResult.InvalidPlan).issues,
        )
    }

    @Test
    fun providerFailureBecomesProviderFailureResult() = runBlocking {
        val failure = IllegalStateException("provider unavailable")
        val provider = AIProvider { throw failure }

        val result = interpreter(provider).interpret(textCapture("Intentar"))

        val providerFailure = result as CaptureInterpretationResult.ProviderFailure
        assertSame(failure, providerFailure.cause)
    }

    @Test
    fun providerCancellationIsRethrown() = runBlocking {
        val cancellation = CancellationException("cancelled")
        val provider = AIProvider { throw cancellation }

        val thrown = runCatching {
            interpreter(provider).interpret(textCapture("Cancelar"))
        }.exceptionOrNull()

        assertSame(cancellation, thrown)
    }

    @Test
    fun providerIsInvokedExactlyOnceForNormalInterpretation() = runBlocking {
        val provider = RecordingProvider(validNoteCandidate())

        interpreter(provider).interpret(textCapture("Una vez"))

        assertEquals(1, provider.requests.size)
    }

    @Test
    fun newProductionFilesStayInProviderNeutralApplicationBoundary() {
        val sourceFiles = listOf(
            "src/main/java/com/edu/quickaside/application/capture/AIProvider.kt",
            "src/main/java/com/edu/quickaside/application/capture/CaptureInterpreter.kt",
        )
        val forbiddenMarkers = listOf(
            "import android.",
            "import androidx.",
            "import com.google.",
            "import okhttp",
            "import kotlinx.serialization",
            "MiMo",
            "DeepSeek",
            "ActionExecutor",
            "Room",
            "SQLite",
        )

        sourceFiles.forEach { path ->
            val source = File(path).readText()
            forbiddenMarkers.forEach { marker ->
                assertFalse("$path must not contain $marker", source.contains(marker))
            }
        }
    }

    private fun interpreter(provider: AIProvider): CaptureInterpreter =
        ProviderCaptureInterpreter(
            provider = provider,
            validator = CapturePlanValidator(),
        )

    private fun textCapture(text: String): Capture = Capture(
        id = CaptureId("capture-text"),
        originalInput = CaptureInput.Text(text),
        capturedAt = capturedAt,
    )

    private fun voiceCapture(transcript: String): Capture = Capture(
        id = CaptureId("capture-voice"),
        originalInput = CaptureInput.Voice(transcript),
        capturedAt = capturedAt,
    )

    private fun validNoteCandidate(): AIInterpretationCandidate =
        AIInterpretationCandidate(
            actions = listOf(CapturePlanActionDraft.CreateNote("Nota válida")),
        )

    private class RecordingProvider(
        private val candidate: AIInterpretationCandidate,
    ) : AIProvider {
        val requests = mutableListOf<AIInterpretationRequest>()

        override suspend fun interpret(
            request: AIInterpretationRequest,
        ): AIInterpretationCandidate {
            requests += request
            return candidate
        }
    }
}
