package com.edu.quickaside.application.capture

import com.edu.quickaside.domain.capture.CapturePlanContract
import com.edu.quickaside.domain.common.CaptureId
import java.time.LocalDate

/** Locally validated proposed data with Android-owned Capture provenance. In-memory only. */
sealed interface CaptureClarification {
    @ConsistentCopyVisibility
    data class TaskSpace internal constructor(
        val sourceCaptureId: CaptureId,
        val title: String,
        val dueDate: LocalDate? = null,
    ) : CaptureClarification {
        init {
            require(title.isNotBlank()) { "Clarification Task title must not be blank" }
            require(CapturePlanContract.isWithinCharacterLimit(title, CapturePlanContract.MAX_TASK_TITLE_CHARS)) {
                "Clarification Task title exceeds the CapturePlan limit"
            }
        }
    }
}
