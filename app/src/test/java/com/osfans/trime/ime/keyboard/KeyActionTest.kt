// SPDX-FileCopyrightText: 2015 - 2026 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.ime.keyboard

import android.view.KeyEvent
import com.osfans.trime.data.theme.ThemeTestSupport
import com.osfans.trime.data.theme.model.KeyActionToken
import com.osfans.trime.data.theme.model.PresetKey
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import timber.log.Timber

/**
 * Pins the key-action grammar: how a plain token is resolved against the
 * theme's preset keys, key-code expressions and text-key sequences.
 */
class KeyActionTest :
    BehaviorSpec({
        val platformLookup = KeyCode.androidKeyNameToCode
        beforeSpec { KeyCode.androidKeyNameToCode = KeyCodeTestSupport::androidKeyNameToCode }
        afterSpec { KeyCode.androidKeyNameToCode = platformLookup }

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
            `when`("the preset sends Shift+Return") {
                then("enter keeps the shift mask and carries no text or command") {
                    val action = plain("CommitScriptText")
                    action.code shouldBe KeyEvent.KEYCODE_ENTER
                    action.modifier shouldBe KeyEvent.META_SHIFT_ON
                    action.text shouldBe ""
                    action.command shouldBe ""
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
            `when`("the T9 literal-digit preset has no send but a command") {
                then("both themes preserve the function command and exact digit") {
                    listOf("trime.yaml", "tongwenfeng.trime.yaml").forEach { file ->
                        val presets = ThemeTestSupport.decodeBuiltinTheme(file).presetKeys
                        for (digit in 0..9) {
                            val action = KeyAction(KeyActionToken.Plain("T9_digit_$digit"), presets)
                            action.code shouldBe KeyEvent.KEYCODE_FUNCTION
                            action.command shouldBe "t9_digit"
                            action.option shouldBe digit.toString()
                            action.text shouldBe ""
                        }
                    }
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
            `when`("the key is a literal plus with or without Control") {
                then("the final plus remains the key rather than an empty modifier") {
                    plain("+").code shouldBe KeyEvent.KEYCODE_PLUS
                    plain("+").modifier shouldBe 0
                    plain("Control++").code shouldBe KeyEvent.KEYCODE_PLUS
                    plain("Control++").modifier shouldBe KeyEvent.META_CTRL_ON
                    plain("Control++").text shouldBe ""
                }
            }
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
                    for (literal in listOf("hello", "你好", "abc+xyz")) {
                        val action = plain(literal)
                        action.code shouldBe 0
                        action.text shouldBe literal
                    }
                }
            }
        }

        given("the supplied theme presets") {
            `when`("the same token has different meanings in two themes") {
                then("each action retains only the explicitly supplied preset") {
                    val first = KeyAction(KeyActionToken.Plain("custom"), mapOf("custom" to PresetKey(commit = "first")))
                    val second = KeyAction(KeyActionToken.Plain("custom"), mapOf("custom" to PresetKey(text = "second")))
                    first.commit shouldBe "first"
                    first.text shouldBe ""
                    second.commit shouldBe ""
                    second.text shouldBe "second"
                }
            }
            `when`("the bundled T9 editing keys are decoded") {
                then("space, Enter and repeatable deletion retain their actions") {
                    listOf("trime.yaml", "tongwenfeng.trime.yaml").forEach { file ->
                        val presets = ThemeTestSupport.decodeBuiltinTheme(file).presetKeys
                        fun action(name: String) = KeyAction(KeyActionToken.Plain(name), presets)
                        action("T9_space").code shouldBe KeyEvent.KEYCODE_SPACE
                        action("T9_space").isSlideCursor shouldBe true
                        action("space1").code shouldBe KeyEvent.KEYCODE_SPACE
                        action("T9_enter").code shouldBe KeyEvent.KEYCODE_ENTER
                        action("T9_backspace").code shouldBe KeyEvent.KEYCODE_DEL
                        action("T9_backspace").isRepeatable shouldBe true
                        action("T9_backspace").isSlideDelete shouldBe true
                    }
                }
            }
        }

        given("an inline token") {
            `when`("it declares commit, text and label") {
                then("they are applied verbatim") {
                    val action =
                        KeyAction(
                            KeyActionToken.Inline(
                                commit = "a",
                                text = "b",
                                label = "c",
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
            `when`("the send value does not resolve to a key") {
                then("the action stays inert and emits one named diagnostic") {
                    val messages = mutableListOf<String>()
                    val tree = object : Timber.Tree() {
                        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                            messages += message
                        }
                    }
                    Timber.plant(tree)
                    try {
                        val action =
                            KeyAction(KeyActionToken.Plain("broken"), mapOf("broken" to PresetKey(send = "Bogus_Key")))
                        action.code shouldBe 0
                        action.command shouldBe ""
                        messages shouldBe listOf("preset 'broken' has an unrecognized send 'Bogus_Key'")
                    } finally {
                        Timber.uproot(tree)
                    }
                }
            }
        }
    })
