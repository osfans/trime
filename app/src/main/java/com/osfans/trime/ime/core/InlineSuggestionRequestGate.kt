/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.core

/** Associates the platform's token-less inline response callback with the requesting editor. */
internal class InlineSuggestionRequestGate(
    private val isCurrentEditor: (Long) -> Boolean,
) {
    @Volatile
    private var requestEditorToken = 0L

    fun record(
        editorToken: Long,
        privateInput: Boolean,
    ): Boolean {
        if (privateInput || editorToken == 0L || !isCurrentEditor(editorToken)) {
            invalidate()
            return false
        }
        requestEditorToken = editorToken
        return true
    }

    fun invalidate() {
        requestEditorToken = 0L
    }

    fun acceptedEditorToken(): Long? {
        val token = requestEditorToken
        return token.takeIf { it != 0L && isCurrentEditor(it) }
    }
}
