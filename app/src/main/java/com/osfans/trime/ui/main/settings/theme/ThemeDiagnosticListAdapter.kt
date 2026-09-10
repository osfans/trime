/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main.settings.theme

import android.content.Context
import android.graphics.Typeface
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.osfans.trime.data.theme.ThemeDiagnostics
import splitties.resources.resolveThemeAttribute
import splitties.resources.styledColor
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.textView
import splitties.views.dsl.core.verticalLayout
import splitties.views.dsl.core.wrapContent
import splitties.views.setPaddingDp
import splitties.views.textAppearance

/** Lists the [ThemeDiagnostics.Finding]s of the theme in use, warnings showing. */
class ThemeDiagnosticListAdapter(
    private val findings: List<ThemeDiagnostics.Finding>,
) : RecyclerView.Adapter<ThemeDiagnosticListAdapter.ViewHolder>() {
    class ViewHolder(
        val ui: EntryUi,
    ) : RecyclerView.ViewHolder(ui.root)

    class EntryUi(
        override val ctx: Context,
    ) : Ui {
        private val warningColor = ctx.styledColor(android.R.attr.colorAccent)
        private val mutedColor = ctx.styledColor(android.R.attr.textColorSecondary)

        private val message =
            textView {
                textAppearance = ctx.resolveThemeAttribute(android.R.attr.textAppearanceListItem)
            }

        private val messageColor = message.currentTextColor

        private val path =
            textView {
                textSize = 12f
                typeface = Typeface.MONOSPACE
                setTextColor(mutedColor)
            }

        override val root =
            verticalLayout {
                layoutParams = ViewGroup.LayoutParams(matchParent, wrapContent)
                setPaddingDp(16, 8, 16, 8)
                add(message, lParams(matchParent, wrapContent))
                add(path, lParams(matchParent, wrapContent))
            }

        fun bind(finding: ThemeDiagnostics.Finding) {
            val warning = finding.severity == ThemeDiagnostics.Severity.WARNING
            message.setTextColor(if (warning) warningColor else messageColor)
            message.text = finding.message
            path.isVisible = finding.path != null
            path.text = finding.path.orEmpty()
        }
    }

    override fun getItemCount() = findings.size

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder = ViewHolder(EntryUi(parent.context))

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        holder.ui.bind(findings[position])
    }
}
