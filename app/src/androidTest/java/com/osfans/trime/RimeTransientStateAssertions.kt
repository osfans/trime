// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime

import com.osfans.trime.core.RimeApi

/** Assert that process-global snapshots retain no editor-owned text. */
internal fun RimeApi.checkTransientEditorStateCleared(boundary: String) {
    check(
        compositionCached.length == 0 &&
            compositionCached.preedit.isNullOrEmpty() &&
            compositionCached.commitTextPreview.isNullOrEmpty(),
    ) { "$boundary retained a composition snapshot: $compositionCached" }
    check(
        t9Cached.input.isEmpty() &&
            t9Cached.segments.isEmpty() &&
            t9Cached.choices.isEmpty(),
    ) { "$boundary retained a T9 snapshot" }
    check(!hasMenu && !paging && !statusCached.isComposing) {
        "$boundary retained menu/paging/composing state"
    }
}
