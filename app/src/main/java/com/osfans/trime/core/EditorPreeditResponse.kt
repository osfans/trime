/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.core

import com.osfans.trime.ime.core.InlinePreeditMode

internal data class EditorPreeditResponse(
    val inlinePreedit: InlinePreeditProto,
    val composition: CompositionProto,
)

/** Nine-key editing uses the displayed spelling, never the private engine envelope or preview. */
internal fun editorPreeditResponse(
    composition: CompositionProto,
    mode: InlinePreeditMode,
    t9: Boolean,
): EditorPreeditResponse {
    val inline = when {
        mode == InlinePreeditMode.DISABLE -> InlinePreeditProto("")

        mode == InlinePreeditMode.COMPOSING_TEXT || t9 ->
            InlinePreeditProto(composition.preedit.orEmpty(), composition.cursorPos)

        else -> InlinePreeditProto(composition.commitTextPreview.orEmpty())
    }
    return EditorPreeditResponse(
        inline,
        if (mode == InlinePreeditMode.COMPOSING_TEXT) CompositionProto() else composition,
    )
}
