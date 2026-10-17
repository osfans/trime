/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.core

import com.osfans.trime.ime.core.InlinePreeditMode

internal data class EditorPreeditResponse(
    val inlinePreedit: InlinePreeditProto,
    val composition: CompositionProto,
)

/** A suggested next word is readable only from its candidate row until explicitly selected. */
internal fun compositionForEditor(composition: CompositionProto, isPrediction: Boolean): CompositionProto = if (isPrediction) CompositionProto() else composition

internal fun editorPreeditResponse(
    composition: CompositionProto,
    mode: InlinePreeditMode,
    t9: Boolean,
    isPrediction: Boolean,
): EditorPreeditResponse {
    val readable = compositionForEditor(composition, isPrediction)
    if (isPrediction) return EditorPreeditResponse(InlinePreeditProto(""), readable)
    val inline = when {
        mode == InlinePreeditMode.DISABLE -> InlinePreeditProto("")

        mode == InlinePreeditMode.COMPOSING_TEXT || t9 ->
            InlinePreeditProto(readable.preedit ?: "", readable.cursorPos)

        else -> InlinePreeditProto(readable.commitTextPreview ?: "")
    }
    return EditorPreeditResponse(
        inline,
        if (mode == InlinePreeditMode.COMPOSING_TEXT) CompositionProto() else readable,
    )
}
