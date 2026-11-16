package org.futo.inputmethod.latin.uix.actions.media

/** Pure layout policy for unit tests; dimensions are dp. */
internal object AiEditorSizing {
    fun resolve(
        mode: Int,
        visualLines: Int,
        manual: Int,
        hybridMin: Int,
        hybridMax: Int,
        screenHeight: Int
    ): Int {
        val maxAvailable = (screenHeight * 0.32f).toInt().coerceIn(64, 340)
        val defaultMin = 52
        val defaultMax = 220.coerceAtMost(maxAvailable).coerceAtLeast(defaultMin)
        val min = hybridMin.coerceIn(40, maxAvailable)
        val max = hybridMax.coerceIn(min, maxAvailable)
        val wanted = 24 + visualLines.coerceIn(1, 200) * 24
        return when (mode) {
            1 -> manual.coerceIn(40, maxAvailable)
            2 -> wanted.coerceIn(min, max)
            else -> wanted.coerceIn(defaultMin, defaultMax)
        }
    }
}
