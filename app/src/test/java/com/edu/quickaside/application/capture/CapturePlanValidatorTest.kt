package com.edu.quickaside.application.capture

import com.edu.quickaside.domain.capture.CapturePlanAction
import com.edu.quickaside.domain.capture.CapturePlanActionDraft
import com.edu.quickaside.domain.capture.CapturePlanDraft
import com.edu.quickaside.domain.common.CaptureId
import com.edu.quickaside.domain.common.ListDefinitionId
import com.edu.quickaside.domain.tasks.TaskSpace
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CapturePlanValidatorTest {
    private val validator = CapturePlanValidator()

    @Test
    fun validAddListItemPlanProducesTypedPlan() {
        val result = validator.validate(
            CapturePlanDraft(
                sourceCaptureId = "capture-1",
                actions = listOf(
                    CapturePlanActionDraft.AddListItem(
                        listDefinitionId = "compras",
                        text = "Leche",
                    ),
                ),
            ),
        )

        val plan = validPlan(result)
        assertEquals(CaptureId("capture-1"), plan.sourceCaptureId)
        assertEquals(
            CapturePlanAction.AddListItem(
                listDefinitionId = ListDefinitionId("compras"),
                text = "Leche",
            ),
            plan.actions.single(),
        )
    }

    @Test
    fun validMultiActionPlanPreservesEveryTypedActionInDraftOrder() {
        val result = validator.validate(
            CapturePlanDraft(
                sourceCaptureId = "capture-multi",
                actions = listOf(
                    CapturePlanActionDraft.AddListItem("mandado", "Chobani"),
                    CapturePlanActionDraft.CreateTask(
                        space = TaskSpace.PERSONAL,
                        title = "Pagar Totalplay",
                    ),
                    CapturePlanActionDraft.CreateTask(
                        space = TaskSpace.TRABAJO,
                        title = "Revisar PR",
                        dueDate = LocalDate.of(2026, 9, 10),
                    ),
                    CapturePlanActionDraft.CreateNote("Llamar al taller"),
                    CapturePlanActionDraft.CreateStructuredLog(
                        fields = linkedMapOf("ejercicio" to "press inclinado"),
                    ),
                    CapturePlanActionDraft.UndoLast,
                ),
            ),
        )

        val plan = validPlan(result)
        assertEquals(
            listOf(
                CapturePlanAction.AddListItem(ListDefinitionId("mandado"), "Chobani"),
                CapturePlanAction.CreateTask(TaskSpace.PERSONAL, "Pagar Totalplay"),
                CapturePlanAction.CreateTask(
                    TaskSpace.TRABAJO,
                    "Revisar PR",
                    LocalDate.of(2026, 9, 10),
                ),
                CapturePlanAction.CreateNote("Llamar al taller"),
                CapturePlanAction.CreateStructuredLog(
                    linkedMapOf("ejercicio" to "press inclinado"),
                ),
                CapturePlanAction.UndoLast,
            ),
            plan.actions,
        )
    }

    @Test
    fun validPlanPreservesSourceAndSurroundingWhitespaceExactly() {
        val sourceCaptureId = "  capture-with-space  "
        val itemText = "  Cuerdas guitarra  "

        val plan = validPlan(
            validator.validate(
                CapturePlanDraft(
                    sourceCaptureId = sourceCaptureId,
                    actions = listOf(
                        CapturePlanActionDraft.AddListItem("  compras  ", itemText),
                    ),
                ),
            ),
        )
        val action = plan.actions.single() as CapturePlanAction.AddListItem

        assertEquals(sourceCaptureId, plan.sourceCaptureId.value)
        assertEquals("  compras  ", action.listDefinitionId.value)
        assertEquals(itemText, action.text)
    }

    @Test
    fun personalAndTrabajoRemainDistinctInValidatedTasks() {
        val plan = validPlan(
            validator.validate(
                CapturePlanDraft(
                    sourceCaptureId = "capture-spaces",
                    actions = listOf(
                        CapturePlanActionDraft.CreateTask(TaskSpace.PERSONAL, "Personal"),
                        CapturePlanActionDraft.CreateTask(TaskSpace.TRABAJO, "Trabajo"),
                    ),
                ),
            ),
        )

        val personal = plan.actions[0] as CapturePlanAction.CreateTask
        val trabajo = plan.actions[1] as CapturePlanAction.CreateTask
        assertEquals(TaskSpace.PERSONAL, personal.space)
        assertEquals(TaskSpace.TRABAJO, trabajo.space)
        assertFalse(personal.space == trabajo.space)
    }

    @Test
    fun nullableTaskDueDateIsAccepted() {
        val plan = validPlan(
            validator.validate(
                CapturePlanDraft(
                    sourceCaptureId = "capture-no-due-date",
                    actions = listOf(
                        CapturePlanActionDraft.CreateTask(
                            space = TaskSpace.PERSONAL,
                            title = "Sin fecha",
                            dueDate = null,
                        ),
                    ),
                ),
            ),
        )

        assertEquals(
            null,
            (plan.actions.single() as CapturePlanAction.CreateTask).dueDate,
        )
    }

    @Test
    fun nonNullTaskDueDateIsPreservedExactly() {
        val dueDate = LocalDate.of(2026, 12, 24)
        val plan = validPlan(
            validator.validate(
                CapturePlanDraft(
                    sourceCaptureId = "capture-due-date",
                    actions = listOf(
                        CapturePlanActionDraft.CreateTask(
                            space = TaskSpace.TRABAJO,
                            title = "Preparar reporte",
                            dueDate = dueDate,
                        ),
                    ),
                ),
            ),
        )

        assertEquals(dueDate, (plan.actions.single() as CapturePlanAction.CreateTask).dueDate)
    }

    @Test
    fun validNotePreservesExactText() {
        val text = "  Llamar al taller por la Forester  "
        val plan = validPlan(
            validator.validate(
                CapturePlanDraft(
                    sourceCaptureId = "capture-note",
                    actions = listOf(CapturePlanActionDraft.CreateNote(text)),
                ),
            ),
        )

        assertEquals(text, (plan.actions.single() as CapturePlanAction.CreateNote).text)
    }

    @Test
    fun validStructuredLogPreservesExactKeysAndValues() {
        val fields = linkedMapOf(
            "  ejercicio  " to "  press inclinado  ",
            "asiento" to " 7 ",
        )
        val plan = validPlan(
            validator.validate(
                CapturePlanDraft(
                    sourceCaptureId = "capture-log",
                    actions = listOf(CapturePlanActionDraft.CreateStructuredLog(fields)),
                ),
            ),
        )

        assertEquals(fields, (plan.actions.single() as CapturePlanAction.CreateStructuredLog).fields)
    }

    @Test
    fun undoLastValidatesWithoutTargetPayload() {
        val plan = validPlan(
            validator.validate(
                CapturePlanDraft(
                    sourceCaptureId = "capture-undo",
                    actions = listOf(CapturePlanActionDraft.UndoLast),
                ),
            ),
        )

        assertEquals(listOf(CapturePlanAction.UndoLast), plan.actions)
    }

    @Test
    fun blankSourceCaptureIdIsRejectedAsPlanIssue() {
        val issues = invalidIssues(
            CapturePlanDraft(
                sourceCaptureId = " \t\n ",
                actions = listOf(CapturePlanActionDraft.CreateNote("Valid note")),
            ),
        )

        assertEquals(
            listOf(
                CapturePlanValidationIssue.Plan(
                    CapturePlanValidationReason.BLANK_SOURCE_CAPTURE_ID,
                ),
            ),
            issues,
        )
        assertEquals(CapturePlanValidationScope.PLAN, issues.single().scope)
    }

    @Test
    fun emptyActionListIsRejectedAsPlanIssue() {
        val issues = invalidIssues(
            CapturePlanDraft(
                sourceCaptureId = "capture-empty",
                actions = emptyList(),
            ),
        )

        assertEquals(
            listOf(CapturePlanValidationIssue.Plan(CapturePlanValidationReason.EMPTY_ACTIONS)),
            issues,
        )
    }

    @Test
    fun blankListDefinitionIdIsRejectedAtTheActionIndex() {
        val issues = invalidIssues(
            CapturePlanDraft(
                sourceCaptureId = "capture-list-id",
                actions = listOf(
                    CapturePlanActionDraft.AddListItem(" \t\n ", "Producto"),
                ),
            ),
        )

        assertEquals(
            listOf(
                CapturePlanValidationIssue.Action(
                    actionIndex = 0,
                    reason = CapturePlanValidationReason.BLANK_LIST_DEFINITION_ID,
                ),
            ),
            issues,
        )
        assertEquals(CapturePlanValidationScope.ACTION, issues.single().scope)
    }

    @Test
    fun blankListItemTextIsRejectedAtTheActionIndex() {
        val issues = invalidIssues(
            CapturePlanDraft(
                sourceCaptureId = "capture-list-text",
                actions = listOf(CapturePlanActionDraft.AddListItem("compras", " \t\n ")),
            ),
        )

        assertEquals(
            listOf(
                CapturePlanValidationIssue.Action(
                    actionIndex = 0,
                    reason = CapturePlanValidationReason.BLANK_LIST_ITEM_TEXT,
                ),
            ),
            issues,
        )
    }

    @Test
    fun blankTaskTitleIsRejectedAtTheActionIndex() {
        val issues = invalidIssues(
            CapturePlanDraft(
                sourceCaptureId = "capture-task-title",
                actions = listOf(
                    CapturePlanActionDraft.CreateTask(TaskSpace.PERSONAL, " \t\n "),
                ),
            ),
        )

        assertEquals(
            listOf(
                CapturePlanValidationIssue.Action(
                    actionIndex = 0,
                    reason = CapturePlanValidationReason.BLANK_TASK_TITLE,
                ),
            ),
            issues,
        )
    }

    @Test
    fun blankNoteTextIsRejectedAtTheActionIndex() {
        val issues = invalidIssues(
            CapturePlanDraft(
                sourceCaptureId = "capture-note-text",
                actions = listOf(CapturePlanActionDraft.CreateNote(" \t\n ")),
            ),
        )

        assertEquals(
            listOf(
                CapturePlanValidationIssue.Action(
                    actionIndex = 0,
                    reason = CapturePlanValidationReason.BLANK_NOTE_TEXT,
                ),
            ),
            issues,
        )
    }

    @Test
    fun emptyStructuredLogFieldsAreRejectedAtTheActionIndex() {
        val issues = invalidIssues(
            CapturePlanDraft(
                sourceCaptureId = "capture-empty-log",
                actions = listOf(CapturePlanActionDraft.CreateStructuredLog(emptyMap())),
            ),
        )

        assertEquals(
            listOf(
                CapturePlanValidationIssue.Action(
                    actionIndex = 0,
                    reason = CapturePlanValidationReason.EMPTY_STRUCTURED_LOG_FIELDS,
                ),
            ),
            issues,
        )
    }

    @Test
    fun blankStructuredLogKeyIsRejectedAtTheActionIndex() {
        val issues = invalidIssues(
            CapturePlanDraft(
                sourceCaptureId = "capture-log-key",
                actions = listOf(
                    CapturePlanActionDraft.CreateStructuredLog(
                        linkedMapOf(" \t\n " to "value"),
                    ),
                ),
            ),
        )

        assertEquals(
            listOf(
                CapturePlanValidationIssue.Action(
                    actionIndex = 0,
                    reason = CapturePlanValidationReason.BLANK_STRUCTURED_LOG_KEY,
                ),
            ),
            issues,
        )
    }

    @Test
    fun blankStructuredLogValueIsRejectedAtTheActionIndex() {
        val issues = invalidIssues(
            CapturePlanDraft(
                sourceCaptureId = "capture-log-value",
                actions = listOf(
                    CapturePlanActionDraft.CreateStructuredLog(
                        linkedMapOf("key" to " \t\n "),
                    ),
                ),
            ),
        )

        assertEquals(
            listOf(
                CapturePlanValidationIssue.Action(
                    actionIndex = 0,
                    reason = CapturePlanValidationReason.BLANK_STRUCTURED_LOG_VALUE,
                ),
            ),
            issues,
        )
    }

    @Test
    fun invalidActionReportsItsDeterministicZeroBasedActionIndex() {
        val issues = invalidIssues(
            CapturePlanDraft(
                sourceCaptureId = "capture-index",
                actions = listOf(
                    CapturePlanActionDraft.CreateNote("first"),
                    CapturePlanActionDraft.CreateTask(TaskSpace.TRABAJO, " \t\n "),
                    CapturePlanActionDraft.CreateNote("third"),
                ),
            ),
        )

        assertEquals(
            listOf(
                CapturePlanValidationIssue.Action(
                    actionIndex = 1,
                    reason = CapturePlanValidationReason.BLANK_TASK_TITLE,
                ),
            ),
            issues,
        )
    }

    @Test
    fun validationIssuesDoNotEmbedRejectedOrSensitiveInputContent() {
        val sensitiveText = "Private note: do not echo this capture"
        val result = validator.validate(
            CapturePlanDraft(
                sourceCaptureId = " \t\n ",
                actions = listOf(
                    CapturePlanActionDraft.AddListItem(
                        listDefinitionId = " \t\n ",
                        text = sensitiveText,
                    ),
                ),
            ),
        )

        val invalid = result as? CapturePlanValidationResult.Invalid
            ?: throw AssertionError("Expected an invalid CapturePlan result")
        assertFalse(invalid.toString().contains(sensitiveText))
        assertTrue(invalid.issues.all { it.actionIndex == null || it.actionIndex == 0 })
    }

    @Test
    fun newProductionFilesUseOnlyPureKotlinAndApprovedDomainOrTimeImports() {
        val sourcePaths = listOf(
            "src/main/java/com/edu/quickaside/domain/capture/CapturePlan.kt",
            "src/main/java/com/edu/quickaside/application/capture/CapturePlanValidator.kt",
        )
        val forbiddenFragments = listOf(
            "android.",
            "androidx.",
            "com.google.",
            "okhttp",
            "retrofit",
            "gson",
            "moshi",
            "serialization",
            "mimo",
            "deepseek",
            "aiprovider",
            "captureinterpreter",
            "actionexecutor",
            "java.net",
            "java.io",
            "room",
            "sqlite",
            "google.",
            "http",
        )

        sourcePaths.forEach { sourcePath ->
            val source = readProductionSource(sourcePath)
            val imports = source.lineSequence()
                .filter(String::isNotBlank)
                .filter { it.startsWith("import ") }
            imports.forEach { importLine ->
                assertTrue(
                    "Unexpected import in $sourcePath: $importLine",
                    importLine.startsWith("import com.edu.quickaside.domain.") ||
                        importLine.startsWith("import java.time."),
                )
            }
            forbiddenFragments.forEach { forbiddenFragment ->
                assertFalse(
                    "Unexpected dependency marker in $sourcePath: $forbiddenFragment",
                    source.contains(forbiddenFragment, ignoreCase = true),
                )
            }
        }
    }

    private fun validPlan(result: CapturePlanValidationResult): com.edu.quickaside.domain.capture.CapturePlan =
        when (result) {
            is CapturePlanValidationResult.Valid -> result.plan
            is CapturePlanValidationResult.Invalid ->
                throw AssertionError("Expected a valid CapturePlan result")
        }

    private fun invalidIssues(draft: CapturePlanDraft): List<CapturePlanValidationIssue> {
        val result = validator.validate(draft)
        return when (result) {
            is CapturePlanValidationResult.Invalid -> result.issues
            is CapturePlanValidationResult.Valid ->
                throw AssertionError("Expected an invalid CapturePlan result")
        }
    }

    private fun readProductionSource(sourcePath: String): String {
        val sourceFile = listOf(File(sourcePath), File("app/$sourcePath"))
            .firstOrNull { it.isFile }
        assertNotNull("Could not locate production source: $sourcePath", sourceFile)
        return sourceFile!!.readText()
    }
}
