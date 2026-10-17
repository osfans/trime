/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.candidates

/** The View applies bound semantics after its widget superclass and guards actions before it. */
internal interface BoundActionOwner {
    var clickBinding: VersionedClickBinding<*>?
}
