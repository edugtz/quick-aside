package com.edu.quickaside.domain.capture

import com.edu.quickaside.domain.common.CaptureId
import com.edu.quickaside.domain.common.ListDefinitionId
import com.edu.quickaside.domain.tasks.TaskSpace
import java.time.LocalDate

/**
 * A provider-independent, validated set of ordered actions for one Capture.
 *
 * Construction is reserved for values that have already passed the
 * CapturePlanValidator boundary. CapturePlanDraft is the intentionally looser
 * candidate representation for decoded input.
 */
data class CapturePlan(
    val sourceCaptureId: CaptureId,
    val actions: List<CapturePlanAction>,
) {
    init {
        require(sourceCaptureId.value.isNotBlank()) {
            "Capture plan source Capture ID must not be blank"
        }
        require(actions.isNotEmpty()) {
            "Capture plan must contain at least one action"
        }
    }
}

/** The supported typed actions in a validated CapturePlan. */
sealed interface CapturePlanAction {
    data class AddListItem(
        val listDefinitionId: ListDefinitionId,
        val text: String,
    ) : CapturePlanAction {
        init {
            require(listDefinitionId.value.isNotBlank()) {
                "Capture plan list definition ID must not be blank"
            }
            require(text.isNotBlank()) {
                "Capture plan list item text must not be blank"
            }
        }
    }

    data class CreateTask(
        val space: TaskSpace,
        val title: String,
        val dueDate: LocalDate? = null,
    ) : CapturePlanAction {
        init {
            require(title.isNotBlank()) {
                "Capture plan task title must not be blank"
            }
        }
    }

    data class CreateNote(
        val text: String,
    ) : CapturePlanAction {
        init {
            require(text.isNotBlank()) {
                "Capture plan note text must not be blank"
            }
        }
    }

    data class CreateStructuredLog(
        val fields: Map<String, String>,
    ) : CapturePlanAction {
        init {
            require(fields.isNotEmpty()) {
                "Capture plan structured log must contain at least one field"
            }
            require(fields.keys.none(String::isBlank)) {
                "Capture plan structured log field keys must not be blank"
            }
            require(fields.values.none(String::isBlank)) {
                "Capture plan structured log field values must not be blank"
            }
        }
    }

    data object UndoLast : CapturePlanAction
}

/**
 * The structurally decoded candidate accepted by the validation boundary.
 * Raw strings are intentional: invalid blank values must be representable
 * without depending on exceptions from validated constructors.
 */
data class CapturePlanDraft(
    val sourceCaptureId: String,
    val actions: List<CapturePlanActionDraft>,
)

sealed interface CapturePlanActionDraft {
    data class AddListItem(
        val listDefinitionId: String,
        val text: String,
    ) : CapturePlanActionDraft

    data class CreateTask(
        val space: TaskSpace,
        val title: String,
        val dueDate: LocalDate? = null,
    ) : CapturePlanActionDraft

    data class CreateNote(
        val text: String,
    ) : CapturePlanActionDraft

    data class CreateStructuredLog(
        val fields: Map<String, String>,
    ) : CapturePlanActionDraft

    data object UndoLast : CapturePlanActionDraft
}
