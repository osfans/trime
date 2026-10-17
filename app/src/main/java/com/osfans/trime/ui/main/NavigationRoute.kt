/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ui.main

import android.os.Parcelable
import androidx.navigation.NavController
import androidx.navigation.createGraph
import androidx.navigation.fragment.fragment
import com.osfans.trime.R
import com.osfans.trime.ui.main.settings.AdvancedSettingsFragment
import com.osfans.trime.ui.main.settings.CandidatesSettingsFragment
import com.osfans.trime.ui.main.settings.ClipboardSettingsFragment
import com.osfans.trime.ui.main.settings.GeneralSettingsFragment
import com.osfans.trime.ui.main.settings.KeyboardSettingsFragment
import com.osfans.trime.ui.main.settings.ProfileSettingsFragment
import com.osfans.trime.ui.main.settings.QuickSettingsFragment
import com.osfans.trime.ui.main.settings.schema.SchemaListFragment
import com.osfans.trime.ui.main.settings.theme.ThemeSettingsFragment
import com.osfans.trime.ui.main.settings.userdict.UserDictionaryFragment
import kotlinx.parcelize.Parcelize

@Parcelize
sealed class NavigationRoute(
    val path: String,
) : Parcelable {

    data object Main : NavigationRoute("com.osfans.trime.ui.main.NavigationRoute.Main")

    data object QuickSettings : NavigationRoute("com.osfans.trime.ui.main.NavigationRoute.QuickSettings")

    data object SchemaList : NavigationRoute("com.osfans.trime.ui.main.NavigationRoute.SchemaList")

    data object UserDict : NavigationRoute("com.osfans.trime.ui.main.NavigationRoute.UserDict")

    data object Profile : NavigationRoute("com.osfans.trime.ui.main.NavigationRoute.Profile")

    data object General : NavigationRoute("com.osfans.trime.ui.main.NavigationRoute.General")

    data object VirtualKeyboard : NavigationRoute("com.osfans.trime.ui.main.NavigationRoute.VirtualKeyboard")

    data object CandidatesWindow : NavigationRoute("com.osfans.trime.ui.main.NavigationRoute.CandidatesWindow")

    data object Theme : NavigationRoute("com.osfans.trime.ui.main.NavigationRoute.Theme")

    data object Clipboard : NavigationRoute("com.osfans.trime.ui.main.NavigationRoute.Clipboard")

    data object Advanced : NavigationRoute("com.osfans.trime.ui.main.NavigationRoute.Advanced")

    data object Developer : NavigationRoute("com.osfans.trime.ui.main.NavigationRoute.Developer")

    data object About : NavigationRoute("com.osfans.trime.ui.main.NavigationRoute.About")

    data object License : NavigationRoute("com.osfans.trime.ui.main.NavigationRoute.License")

    companion object {
        fun createGraph(controller: NavController) = controller.createGraph(Main.path) {
            val ctx = controller.context

            fragment<MainFragment>(Main.path) {
                label = ctx.getString(R.string.trime_app_name)
            }

            fragment<QuickSettingsFragment>(QuickSettings.path) {
                label = ctx.getString(R.string.quick_settings)
            }

            fragment<SchemaListFragment>(SchemaList.path) {
                label = ctx.getString(R.string.schemata)
            }
            fragment<UserDictionaryFragment>(UserDict.path) {
                label = ctx.getString(R.string.user_dictionary)
            }
            fragment<ProfileSettingsFragment>(Profile.path) {
                label = ctx.getString(R.string.profile)
            }

            fragment<GeneralSettingsFragment>(General.path) {
                label = ctx.getString(R.string.general)
            }
            fragment<KeyboardSettingsFragment>(VirtualKeyboard.path) {
                label = ctx.getString(R.string.virtual_keyboard)
            }
            fragment<CandidatesSettingsFragment>(CandidatesWindow.path) {
                label = ctx.getString(R.string.candidates_window)
            }
            fragment<ThemeSettingsFragment>(Theme.path) {
                label = ctx.getString(R.string.theme)
            }
            fragment<ClipboardSettingsFragment>(Clipboard.path) {
                label = ctx.getString(R.string.clipboard)
            }
            fragment<AdvancedSettingsFragment>(Advanced.path) {
                label = ctx.getString(R.string.advanced)
            }
            fragment<DeveloperFragment>(Developer.path) {
                label = ctx.getString(R.string.developer)
            }
            fragment<AboutFragment>(About.path) {
                label = ctx.getString(R.string.about)
            }
            fragment<LicenseFragment>(License.path) {
                label = ctx.getString(R.string.license)
            }
        }
    }
}
