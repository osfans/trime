/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.core

/** Retains every process-wide editor token a service may have bound in native Rime. */
internal class EditorOwnership {
    private val tokens = linkedSetOf<Long>()

    fun add(token: Long) {
        require(token != 0L)
        tokens += token
    }

    fun snapshot(): LongArray = tokens.toLongArray()
}

/** A service instance must never share its daemon client with a replacement instance. */
internal fun imeRimeSessionName(serviceClassName: String, initialEditorToken: Long): String {
    require(initialEditorToken != 0L)
    return "$serviceClassName#$initialEditorToken"
}

/** Content-bearing native caches are readable only after the current editor finished binding. */
internal fun canAccessEditorContent(
    editorActive: Boolean,
    policyAllowsContent: Boolean,
    processPrivacyActive: Boolean,
    currentEditorToken: Long,
    nativeBoundEditorToken: Long,
): Boolean = editorActive &&
    policyAllowsContent &&
    !processPrivacyActive &&
    currentEditorToken != 0L &&
    nativeBoundEditorToken == currentEditorToken
