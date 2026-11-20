package org.futo.inputmethod.latin.uix.actions.emoji

import kotlin.math.abs

/**
 * Hysteresis for scroll-driven action chrome. Only actual drag/fling scroll
 * deltas are accepted; programmatic category jumps must pass userScrolling=false.
 */
internal class EmojiScrollChromeController(
    private val collapseAfterPx: Int,
    private val expandAfterPx: Int
) {
    init {
        require(collapseAfterPx > 0 && expandAfterPx > 0)
    }

    private var direction = 0
    private var distance = 0

    var expanded: Boolean = true
        private set

    /** Returns the new visibility only when a threshold causes a transition. */
    fun onScrolled(dy: Int, userScrolling: Boolean): Boolean? {
        if (!userScrolling || dy == 0) return null

        val newDirection = if (dy > 0) 1 else -1
        if (direction != newDirection) {
            direction = newDirection
            distance = 0
        }
        distance = (distance + abs(dy)).coerceAtMost(
            maxOf(collapseAfterPx, expandAfterPx)
        )

        if (newDirection > 0 && expanded && distance >= collapseAfterPx) {
            expanded = false
            distance = 0
            return false
        }
        if (newDirection < 0 && !expanded && distance >= expandAfterPx) {
            expanded = true
            distance = 0
            return true
        }
        return null
    }

    /** Search and freshly mounted lists always restore the title bar. */
    fun expand(): Boolean? {
        direction = 0
        distance = 0
        if (expanded) return null
        expanded = true
        return true
    }
}
