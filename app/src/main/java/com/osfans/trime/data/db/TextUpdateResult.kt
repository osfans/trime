/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.data.db

/** Business outcome of an edit; database failures and privacy rejection still throw. */
enum class TextUpdateResult {
    /** Exactly one row committed; eligible caches may now publish the edited text. */
    SAVED,

    /** The initial read was absent or the guarded update affected zero rows. */
    NOT_FOUND,
}
