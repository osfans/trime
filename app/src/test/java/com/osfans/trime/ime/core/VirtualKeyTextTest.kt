/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.core

import android.text.InputType
import android.view.KeyCharacterMap
import android.view.KeyEvent
import com.osfans.trime.core.KeyModifier
import com.osfans.trime.core.KeyModifiers
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class VirtualKeyTextTest :
    StringSpec({
        fun text(
            value: Int = 'a'.code,
            inputType: Int? = InputType.TYPE_CLASS_TEXT,
            isVirtual: Boolean = true,
            keyCode: Int = KeyEvent.KEYCODE_A,
            modifiers: KeyModifiers = KeyModifiers.Empty,
            metaState: Int = modifiers.metaState,
            resolveCharacter: (Int, Int) -> Int = { _, _ -> error("Unmodified native text must not query a character map") },
        ): String? = virtualKeyText(inputType, isVirtual, value, keyCode, modifiers, metaState, resolveCharacter)

        "unmodified ASCII text preserves every printable native value including space" {
            (0x20..0x7e).forEach { text(value = it) shouldBe it.toChar().toString() }
        }
        "controls delete non ASCII and Rime function keysyms cannot become text" {
            listOf(-1, 0, 9, 10, 13, 31, 127, 128, 0x4f60, 0xd800, 0xff08, 0xff0d, 0xff51, 0xffbe, 0x1000061).forEach {
                text(value = it) shouldBe null
            }
        }
        "physical keys always retain their key event behavior" {
            text(isVirtual = false) shouldBe null
            text(value = 'A'.code, isVirtual = false, modifiers = KeyModifiers(KeyModifier.Shift)) shouldBe null
        }
        "missing editor and every null input class retain key events" {
            text(inputType = null) shouldBe null
            text(inputType = InputType.TYPE_NULL) shouldBe null
            text(inputType = InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) shouldBe null
        }
        "text URI email password and numeric editors accept printable text without changing policy" {
            listOf(
                InputType.TYPE_CLASS_TEXT,
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_CLASS_NUMBER,
                InputType.TYPE_CLASS_PHONE,
            ).forEach { text(inputType = it) shouldBe "a" }
        }
        "native letter case and literal punctuation are preserved without a second transformation" {
            listOf('a', 'A', '!', '&', '_', '+', '?').forEach { text(value = it.code) shouldBe it.toString() }
        }
        "original Rime shortcut release and unknown modifier bits never become text" {
            KeyModifier.entries.filterNot { it in listOf(KeyModifier.None, KeyModifier.Shift, KeyModifier.Lock, KeyModifier.Mod2) }.forEach {
                text(modifiers = KeyModifiers(it)) shouldBe null
            }
            text(modifiers = KeyModifiers.of(1 shl 29)) shouldBe null
            text(modifiers = KeyModifiers(KeyModifier.Shift, KeyModifier.Control)) shouldBe null
        }
        "effective Android shortcut masks including directional modifiers retain key events" {
            listOf(
                KeyEvent.META_CTRL_ON, KeyEvent.META_CTRL_LEFT_ON, KeyEvent.META_CTRL_RIGHT_ON,
                KeyEvent.META_ALT_ON, KeyEvent.META_ALT_LEFT_ON, KeyEvent.META_ALT_RIGHT_ON,
                KeyEvent.META_META_ON, KeyEvent.META_META_LEFT_ON, KeyEvent.META_META_RIGHT_ON,
                KeyEvent.META_SYM_ON, KeyEvent.META_FUNCTION_ON,
            ).forEach { text(metaState = it) shouldBe null }
        }
        "Shift on mapped numbers and punctuation commits the resolved character" {
            listOf(
                Triple('1', KeyEvent.KEYCODE_1, '!'),
                Triple('7', KeyEvent.KEYCODE_7, '&'),
                Triple('-', KeyEvent.KEYCODE_MINUS, '_'),
                Triple('=', KeyEvent.KEYCODE_EQUALS, '+'),
                Triple('/', KeyEvent.KEYCODE_SLASH, '?'),
            ).forEach { (native, code, expected) ->
                var calls = 0
                text(value = native.code, keyCode = code, modifiers = KeyModifiers(KeyModifier.Shift)) { actualCode, state ->
                    actualCode shouldBe code
                    state shouldBe KeyModifiers(KeyModifier.Shift).metaState
                    calls++
                    expected.code
                } shouldBe expected.toString()
                calls shouldBe 1
            }
        }
        "already uppercase Shift letter keeps the mapped uppercase character" {
            text(value = 'A'.code, modifiers = KeyModifiers(KeyModifier.Shift)) { code, state ->
                code shouldBe KeyEvent.KEYCODE_A
                state shouldBe KeyModifiers(KeyModifier.Shift).metaState
                'A'.code
            } shouldBe "A"
        }
        "Caps Lock and Caps with Shift use the actual resolved case" {
            text(modifiers = KeyModifiers(KeyModifier.Lock)) { _, state ->
                state shouldBe KeyEvent.META_CAPS_LOCK_ON
                'A'.code
            } shouldBe "A"
            val combined = KeyModifiers(KeyModifier.Lock, KeyModifier.Shift)
            text(value = 'A'.code, modifiers = combined) { _, state ->
                state shouldBe combined.metaState
                'a'.code
            } shouldBe "a"
        }
        "Num Lock alone preserves native text and with Shift reaches the character resolver" {
            text(value = '7'.code, modifiers = KeyModifiers(KeyModifier.Mod2)) shouldBe "7"
            val combined = KeyModifiers(KeyModifier.Shift, KeyModifier.Mod2)
            text(value = '7'.code, keyCode = KeyEvent.KEYCODE_7, modifiers = combined) { _, state ->
                state shouldBe combined.metaState
                '&'.code
            } shouldBe "&"
        }
        "unmapped printable Shift or Caps values retain their native character" {
            text(value = '&'.code, keyCode = KeyEvent.KEYCODE_UNKNOWN, modifiers = KeyModifiers(KeyModifier.Shift)) shouldBe "&"
            text(value = 'A'.code, keyCode = KeyEvent.KEYCODE_UNKNOWN, modifiers = KeyModifiers(KeyModifier.Lock)) shouldBe "A"
        }
        "zero dead character controls and non ASCII resolver results retain key events" {
            listOf(0, 10, 127, 0x4f60, KeyCharacterMap.COMBINING_ACCENT or '\u0301'.code).forEach { resolved ->
                text(modifiers = KeyModifiers(KeyModifier.Shift)) { _, _ -> resolved } shouldBe null
            }
        }
        "a mapped key cannot turn a non printable Rime value into committed text" {
            text(value = 0xff0d, keyCode = KeyEvent.KEYCODE_ENTER, modifiers = KeyModifiers(KeyModifier.Shift)) shouldBe null
        }
    })
