/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.data.theme.icons

import android.content.Context
import androidx.startup.Initializer
import com.mikepenz.iconics.typeface.ITypeface
import com.mikepenz.iconics.typeface.IconicsHolder
import com.mikepenz.iconics.typeface.IconicsInitializer

/** Replace the original cmd initializer rather than racing two registrations. */
class CompactCommunityMaterialInitializer : Initializer<ITypeface> {
    override fun create(context: Context): ITypeface {
        IconicsHolder.registerFont(CompactCommunityMaterial)
        return CompactCommunityMaterial
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = listOf(IconicsInitializer::class.java)
}
