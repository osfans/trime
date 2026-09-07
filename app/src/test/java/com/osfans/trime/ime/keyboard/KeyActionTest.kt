// SPDX-FileCopyrightText: 2015 - 2026 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

import android.view.KeyEvent
import com.osfans.trime.data.theme.ThemeTestSupport
import com.osfans.trime.data.theme.model.KeyActionToken
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * Pins the key-action grammar: how a plain token is resolved against the
 * theme's preset keys, key-code expressions and text-key sequences.
 */
class KeyActionTest :
    BehaviorSpec({
        // The Android key-name lookup is a platform call that throws when not
        // mocked; the Rime key table is the path under test.
        beforeTest { KeyCode.androidKeyNameToCode = { 0 } }

        val presetKeys = ThemeTestSupport.decodeBuiltinTheme("trime.yaml").presetKeys

        fun plain(token: String) = KeyAction(KeyActionToken.Plain(token), presetKeys)

        given("a token naming a preset key") {
            `when`("the preset sends a plain key") {
                then("the key code and the preset flags are applied") {
                    val action = plain("BackSpace")
                    action.code shouldBe KeyEvent.KEYCODE_DEL
                    action.modifier shouldBe 0
                    action.isRepeatable shouldBe true
                    action.isFunctional shouldBe false
                    action.command shouldBe ""
                }
            }
            `when`("the preset carries modifier send") {
                then("the key code carries the modifier mask") {
                    val action = plain("select_all")
                    action.code shouldBe KeyEvent.KEYCODE_A
                    action.modifier shouldBe KeyEvent.META_CTRL_ON
                }
            }
            `when`("the preset is a modifier key itself") {
                then("the shift-lock mode is applied and the mask reported") {
                    val action = plain("Shift_L")
                    action.code shouldBe KeyEvent.KEYCODE_SHIFT_LEFT
                    action.shiftLock shouldBe "ascii_long"
                    action.modifierKeyOnMask shouldBe KeyEvent.META_SHIFT_ON
                }
            }
            `when`("the preset has no send but a command") {
                then("the action degrades to a function key") {
                    val action = plain("Return")
                    action.code shouldBe KeyEvent.KEYCODE_ENTER
                }
            }
            `when`("the preset only simulates a key sequence") {
                then("no key code is set but the text is kept") {
                    val action = plain("Clear")
                    action.code shouldBe 0
                    action.command shouldBe ""
                    action.text shouldBe "{Control+a}{BackSpace}"
                }
            }
        }

        given("a plain token with key-code syntax") {
            `when`("it is a modifier expression without braces") {
                then("the key code and the modifier mask are resolved") {
                    val action = plain("Control+a")
                    action.code shouldBe KeyEvent.KEYCODE_A
                    action.modifier shouldBe KeyEvent.META_CTRL_ON
                    action.text shouldBe ""
                }
            }
            `when`("it is a single uppercase letter") {
                then("it resolves to the letter key with the shift mask") {
                    val action = plain("A")
                    action.code shouldBe KeyEvent.KEYCODE_A
                    action.modifier shouldBe KeyEvent.META_SHIFT_ON
                }
            }
            `when`("it is a key name") {
                then("the key code is resolved") {
                    val action = plain("Right")
                    action.code shouldBe KeyEvent.KEYCODE_DPAD_RIGHT
                    action.modifier shouldBe 0
                }
            }
            `when`("it is a single character") {
                then("the key code is resolved without modifier") {
                    val action = plain("q")
                    action.code shouldBe KeyEvent.KEYCODE_Q
                    action.modifier shouldBe 0
                    action.text shouldBe ""
                }
            }
        }

        given("a plain token that is not a key") {
            `when`("it is a braced key sequence") {
                then("it becomes a text key sequence") {
                    val action = plain("(){Left}")
                    action.code shouldBe 0
                    action.text shouldBe "(){Left}"
                }
            }
            `when`("it is a modifier expression wrapped in braces") {
                then("it stays a text key sequence for the sequence engine") {
                    val action = plain("{Control+a}")
                    action.code shouldBe 0
                    action.text shouldBe "{Control+a}"
                }
            }
            `when`("it is an arbitrary word") {
                then("it becomes plain text") {
                    val action = plain("hello")
                    action.code shouldBe 0
                    action.text shouldBe "hello"
                }
            }
        }

        given("an inline token") {
            `when`("it declares commit, text and label") {
                then("they are applied verbatim") {
                    val action =
                        KeyAction(
                            KeyActionToken.Inline(
                                KeyActionToken.Inline.Token(
                                    commit = "a",
                                    text = "b",
                                    label = "c",
                                ),
                            ),
                            presetKeys,
                        )
                    action.commit shouldBe "a"
                    action.text shouldBe "b"
                }
            }
        }

        given("a preset that repeats a send value") {
            `when`("the send value is spelled in the flow mapping") {
                then("it resolves like the canonical preset") {
                    val action = plain("Return1")
                    action.code shouldBe KeyEvent.KEYCODE_ENTER
                    action.command shouldBe ""
                }
            }
        }
    })
