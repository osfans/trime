// SPDX-FileCopyrightText: 2015 - 2026 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme

import com.osfans.trime.data.theme.model.PresetKey
import com.osfans.trime.ime.keyboard.KeyCode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

class KeyActionManagerTest :
    BehaviorSpec({
        // The Android key-name lookup is a platform call that throws when not
        // mocked; the Rime key table is the path under test.
        beforeTest { KeyCode.androidKeyNameToCode = { 0 } }

        fun diagnostics(vararg presets: Pair<String, PresetKey>) = KeyActionManager.presetDiagnostics(mapOf(*presets))

        given("the built-in themes") {
            `when`("every preset is inspected at activation") {
                then("only platform key names are reported") {
                    // The Android key-name lookup is stubbed out below, so the
                    // all-uppercase platform keys (BRIGHTNESS_DOWN, ...) resolve
                    // only on a device. Any other hit would be a real typo.
                    listOf("trime.yaml", "tongwenfeng.trime.yaml").forEach { file ->
                        val theme = ThemeTestSupport.decodeBuiltinTheme(file)
                        KeyActionManager.presetDiagnostics(theme.presetKeys).forEach { hit ->
                            hit.substringAfter("send '").substringBefore("'") shouldBe
                                hit.substringAfter("send '").substringBefore("'").uppercase()
                        }
                    }
                }
            }
        }

        given("a preset with a send value") {
            `when`("the send does not resolve to a key") {
                then("it is reported") {
                    diagnostics("broken" to PresetKey(send = "Bogus_Key")) shouldBe
                        listOf("preset 'broken' has an unrecognized send 'Bogus_Key'")
                }
            }
            `when`("the send resolves to a key") {
                then("it is not reported") {
                    diagnostics("fine" to PresetKey(send = "q")).shouldBeEmpty()
                }
            }
            `when`("the send is empty") {
                then("the preset is left to the command or text fields") {
                    diagnostics(
                        "cmd" to PresetKey(command = "run"),
                        "text" to PresetKey(text = "hello"),
                    ).shouldBeEmpty()
                }
            }
        }
    })
