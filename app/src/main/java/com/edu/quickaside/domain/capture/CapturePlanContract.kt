package com.edu.quickaside.domain.capture

/**
 * Provider-neutral bounds for values that may enter a CapturePlan.
 *
 * These limits mirror the gateway action contract. Keeping them here lets the
 * validator enforce the same trust boundary without making the codec or any
 * provider-specific adapter responsible for domain semantics.
 */
object CapturePlanContract {
    const val MAX_ACTIONS = 16
    const val MAX_LIST_ITEM_CHARS = 1_000
    const val MAX_TASK_TITLE_CHARS = 500
    const val MAX_NOTE_CHARS = 4_000
    const val MAX_STRUCTURED_LOG_FIELDS = 32
    const val MAX_STRUCTURED_LOG_KEY_CHARS = 128
    const val MAX_STRUCTURED_LOG_VALUE_CHARS = 1_000

    val SUPPORTED_LIST_DEFINITION_IDS: Set<String> = setOf("mandado", "compras")

    /** Counts Unicode code points, matching the gateway's string-length rules. */
    fun isWithinCharacterLimit(value: String, maximum: Int): Boolean {
        require(maximum >= 0) { "maximum character limit must not be negative" }
        return value.codePointCount(0, value.length) <= maximum
    }
}
