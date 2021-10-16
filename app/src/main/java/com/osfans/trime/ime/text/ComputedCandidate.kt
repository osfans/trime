package com.osfans.trime.ime.text

import android.graphics.Rect

/**
 * Data class describing a computed candidate item.
 *
 * @property geometry The geometry of the computed candidate, used to position and size the item correctly when
 *  being drawn on a canvas.
 */
sealed class ComputedCandidate(val geometry: Rect) {
    /**
     * Computed word candidate, used for suggestions provided by the librime backend.
     *
     * @property word The word this computed candidate item represents. Used in the callback to provide which word
     *  should be filled out.
     */
    class Word(
        val word: String,
        val comment: String?,
        geometry: Rect
    ) : ComputedCandidate(geometry) {
        override fun toString(): String {
            return "Word { word=\"$word\", comment=\"$comment\", geometry=$geometry }"
        }
    }

    /**
     * Computed word candidate, used for clipboard paste suggestions.
     *
     * @property arrow The page button text this computed candidate item represents. Used in the callback to
     *  provide which page button should be filled out.
     */
    class Symbol(
        val arrow: String,
        geometry: Rect
    ) : ComputedCandidate(geometry) {
        override fun toString(): String {
            return "Symbol { arrow=$arrow, geometry=$geometry }"
        }
    }
}
