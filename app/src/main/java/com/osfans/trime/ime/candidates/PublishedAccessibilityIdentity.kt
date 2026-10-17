/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.candidates

/**
 * A recycled view must publish its new identity before accepting a service action.
 * Android actions do not carry the original node's identity: after a new query we
 * cannot distinguish an older action from an action on that newly published node.
 */
internal class PublishedAccessibilityIdentity<T : Any> {
    private var published: T? = null

    fun publish(identity: T?) {
        published = identity
    }

    fun clear() {
        published = null
    }

    fun accepts(identity: T?): Boolean = identity != null && identity == published
}
