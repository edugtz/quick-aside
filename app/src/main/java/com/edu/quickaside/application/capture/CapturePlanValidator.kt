package com.edu.quickaside.application.capture

import com.edu.quickaside.domain.capture.CapturePlan
import com.edu.quickaside.domain.capture.CapturePlanAction
import com.edu.quickaside.domain.capture.CapturePlanActionDraft
import com.edu.quickaside.domain.capture.CapturePlanDraft
import com.edu.quickaside.domain.common.CaptureId
import com.edu.quickaside.domain.common.ListDefinitionId

/**
 * Validates decoded CapturePlan candidates before any future execution
 * boundary. Issue order is deterministic: plan-level checks first, followed by
 * actions in their original order and each action's fixed field-check order.
 */
class CapturePlanValidator {
    fun validate(draft: CapturePlanDraft): CapturePlanValidationResult {
        val issues = buildList {
            if (draft.sourceCaptureId.isBlank()) {
                add(
                    CapturePlanValidationIssue.Plan(
                        reason = CapturePlanValidationReason.BLANK_SOURCE_CAPTURE_ID,
                    ),
                )
            }
            if (draft.actions.isEmpty()) {
                add(
                    CapturePlanValidationIssue.Plan(
                        reason = CapturePlanValidationReason.EMPTY_ACTIONS,
                    ),
                )
            }

            draft.actions.forEachIndexed { index, action ->
                when (action) {
                    is CapturePlanActionDraft.AddListItem -> {
                        if (action.listDefinitionId.isBlank()) {
                            add(
                                CapturePlanValidationIssue.Action(
                                    actionIndex = index,
                                    reason = CapturePlanValidationReason.BLANK_LIST_DEFINITION_ID,
                                ),
                            )
                        }
                        if (action.text.isBlank()) {
                            add(
                                CapturePlanValidationIssue.Action(
                                    actionIndex = index,
                                    reason = CapturePlanValidationReason.BLANK_LIST_ITEM_TEXT,
                                ),
                            )
                        }
                    }

                    is CapturePlanActionDraft.CreateTask -> {
                        if (action.title.isBlank()) {
                            add(
                                CapturePlanValidationIssue.Action(
                                    actionIndex = index,
                                    reason = CapturePlanValidationReason.BLANK_TASK_TITLE,
                                ),
                            )
                        }
                    }

                    is CapturePlanActionDraft.CreateNote -> {
                        if (action.text.isBlank()) {
                            add(
                                CapturePlanValidationIssue.Action(
                                    actionIndex = index,
                                    reason = CapturePlanValidationReason.BLANK_NOTE_TEXT,
                                ),
                            )
                        }
                    }

                    is CapturePlanActionDraft.CreateStructuredLog -> {
                        if (action.fields.isEmpty()) {
                            add(
                                CapturePlanValidationIssue.Action(
                                    actionIndex = index,
                                    reason = CapturePlanValidationReason.EMPTY_STRUCTURED_LOG_FIELDS,
                                ),
                            )
                        } else {
                            if (action.fields.keys.any(String::isBlank)) {
                                add(
                                    CapturePlanValidationIssue.Action(
                                        actionIndex = index,
                                        reason = CapturePlanValidationReason.BLANK_STRUCTURED_LOG_KEY,
                                    ),
                                )
                            }
                            if (action.fields.values.any(String::isBlank)) {
                                add(
                                    CapturePlanValidationIssue.Action(
                                        actionIndex = index,
                                        reason = CapturePlanValidationReason.BLANK_STRUCTURED_LOG_VALUE,
                                    ),
                                )
                            }
                        }
                    }

                    CapturePlanActionDraft.UndoLast -> Unit
                }
            }
        }

        if (issues.isNotEmpty()) {
            return CapturePlanValidationResult.Invalid(issues)
        }

        return CapturePlanValidationResult.Valid(
            plan = CapturePlan(
                sourceCaptureId = CaptureId(draft.sourceCaptureId),
                actions = draft.actions.map(::toValidatedAction),
            ),
        )
    }

    private fun toValidatedAction(action: CapturePlanActionDraft): CapturePlanAction =
        when (action) {
            is CapturePlanActionDraft.AddListItem -> CapturePlanAction.AddListItem(
                listDefinitionId = ListDefinitionId(action.listDefinitionId),
                text = action.text,
            )

            is CapturePlanActionDraft.CreateTask -> CapturePlanAction.CreateTask(
                space = action.space,
                title = action.title,
                dueDate = action.dueDate,
            )

            is CapturePlanActionDraft.CreateNote -> CapturePlanAction.CreateNote(
                text = action.text,
            )

            is CapturePlanActionDraft.CreateStructuredLog ->
                CapturePlanAction.CreateStructuredLog(fields = action.fields)

            CapturePlanActionDraft.UndoLast -> CapturePlanAction.UndoLast
        }
}

enum class CapturePlanValidationScope {
    PLAN,
    ACTION,
}

enum class CapturePlanValidationReason {
    BLANK_SOURCE_CAPTURE_ID,
    EMPTY_ACTIONS,
    BLANK_LIST_DEFINITION_ID,
    BLANK_LIST_ITEM_TEXT,
    BLANK_TASK_TITLE,
    BLANK_NOTE_TEXT,
    EMPTY_STRUCTURED_LOG_FIELDS,
    BLANK_STRUCTURED_LOG_KEY,
    BLANK_STRUCTURED_LOG_VALUE,
}

sealed interface CapturePlanValidationIssue {
    val scope: CapturePlanValidationScope
    val reason: CapturePlanValidationReason
    val actionIndex: Int?

    data class Plan(
        override val reason: CapturePlanValidationReason,
    ) : CapturePlanValidationIssue {
        override val scope: CapturePlanValidationScope = CapturePlanValidationScope.PLAN
        override val actionIndex: Int? = null
    }

    data class Action(
        override val actionIndex: Int,
        override val reason: CapturePlanValidationReason,
    ) : CapturePlanValidationIssue {
        override val scope: CapturePlanValidationScope = CapturePlanValidationScope.ACTION

        init {
            require(actionIndex >= 0) { "Capture plan action index must not be negative" }
        }
    }
}

sealed interface CapturePlanValidationResult {
    data class Valid(val plan: CapturePlan) : CapturePlanValidationResult

    data class Invalid(
        val issues: List<CapturePlanValidationIssue>,
    ) : CapturePlanValidationResult {
        init {
            require(issues.isNotEmpty()) {
                "Invalid CapturePlan validation result must contain an issue"
            }
        }
    }
}
