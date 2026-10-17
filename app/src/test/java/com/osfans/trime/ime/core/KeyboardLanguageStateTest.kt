/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

private class ModeEngine(
    var editor: Long = 41L,
    var schema: String = "luna_pinyin_t9",
    var ascii: Boolean = false,
    var composition: String = "",
    var selected: String = "你",
) {
    val operations = mutableListOf<String>()
    val commits = mutableListOf<String>()
    var commitSucceeds = true
    var afterCommit: (() -> Unit)? = null

    suspend fun apply(command: KeyboardModeCommand): KeyboardModeResult = applyKeyboardMode(
        command,
        currentEditor = { editor },
        currentSchema = { schema },
        composing = { composition.isNotEmpty() },
        asciiMode = { ascii },
        commitComposition = {
            operations += "commit:$ascii"
            if (commitSucceeds && composition.isNotEmpty()) {
                commits += selected
                composition = ""
            }
            afterCommit?.invoke()
            commitSucceeds
        },
        setAsciiMode = {
            operations += "ascii:$it"
            ascii = it
        },
    )
}

private fun KeyboardLanguageState.command(
    engine: ModeEngine,
    target: Boolean? = null,
    explicit: Boolean = true,
    restricted: Boolean = false,
    effect: KeyboardModeEffect = KeyboardModeEffect.COMMIT,
) = accept(engine.editor, engine.schema, engine.ascii, target, restricted, explicit, effect)

class KeyboardLanguageStateTest :
    StringSpec({
        "persistent values have an explicit migration default and no layout identifier" {
            ManualLanguageMode.entries.forEach { ManualLanguageMode.fromStored(it.storedValue) shouldBe it }
            ManualLanguageMode.fromStored("letter") shouldBe null
            ManualLanguageMode.fromStored("TRUE") shouldBe null
            ManualLanguageMode.UNSET.asciiMode shouldBe null
            ManualLanguageMode.NATIVE.asciiMode shouldBe false
            ManualLanguageMode.ASCII.asciiMode shouldBe true
        }

        "EN key CN accepted before execution keeps the middle key in English" {
            val engine = ModeEngine()
            val state = KeyboardLanguageState()
            val english = state.command(engine, true)
            val chinese = state.command(engine, false)
            state.acceptsUi(english, engine.editor, engine.schema) shouldBe false
            state.acceptsUi(chinese, engine.editor, engine.schema) shouldBe true

            // These immutable commands use the real production executor. Its guard must never
            // replace acceptsNative with acceptsUi; that would send the middle key in Chinese.
            val enResult = engine.apply(english)
            state.complete(english, enResult, engine.editor, engine.schema) shouldBe false
            engine.operations += "key:a:${engine.ascii}"
            val cnResult = engine.apply(chinese)
            state.complete(chinese, cnResult, engine.editor, engine.schema) shouldBe true
            engine.operations shouldBe listOf("commit:false", "ascii:true", "key:a:true", "commit:true", "ascii:false")
            state.remembered shouldBe ManualLanguageMode.NATIVE
        }

        "two rapid toggles derive opposite targets from accepted intent rather than cached native mode" {
            val engine = ModeEngine()
            val state = KeyboardLanguageState()
            val first = state.command(engine)
            val second = state.command(engine)
            engine.ascii shouldBe false
            first.asciiMode shouldBe true
            second.asciiMode shouldBe false
            engine.apply(first).applied shouldBe true
            engine.apply(second).applied shouldBe true
            engine.operations shouldBe listOf("commit:false", "ascii:true", "commit:true", "ascii:false")
        }

        "native composition is finalized before language mode including T9 raw digits" {
            listOf("64", "ni").forEach { raw ->
                val engine = ModeEngine(composition = raw)
                val state = KeyboardLanguageState()
                engine.apply(state.command(engine, true)).applied shouldBe true
                engine.commits shouldBe listOf("你")
                engine.composition shouldBe ""
                engine.operations shouldBe listOf("commit:false", "ascii:true")
            }
        }

        "symbol navigation preserves both full pinyin and T9 composition without changing language preference" {
            listOf("64", "ni").forEach { raw ->
                val engine = ModeEngine(composition = raw)
                val state = KeyboardLanguageState(ManualLanguageMode.NATIVE)
                val symbols = state.command(engine, true, explicit = false, effect = KeyboardModeEffect.PRESERVE_COMPOSITION)
                val result = engine.apply(symbols)
                result shouldBe KeyboardModeResult(true, false)
                state.complete(symbols, result, engine.editor, engine.schema) shouldBe true
                val back = state.command(engine, false, explicit = false, effect = KeyboardModeEffect.MODE_ONLY)
                engine.apply(back) shouldBe KeyboardModeResult(true, false)
                engine.operations shouldBe emptyList()
                engine.commits shouldBe emptyList()
                engine.composition shouldBe raw
                state.remembered shouldBe ManualLanguageMode.NATIVE
            }
        }

        "English number and symbol navigation restores English without persisting temporary layouts" {
            val engine = ModeEngine(ascii = true)
            val state = KeyboardLanguageState(ManualLanguageMode.ASCII)
            val symbol = state.command(engine, false, explicit = false, effect = KeyboardModeEffect.PRESERVE_COMPOSITION)
            engine.apply(symbol).applied shouldBe true
            val back = state.command(engine, true, explicit = false, effect = KeyboardModeEffect.MODE_ONLY)
            engine.apply(back).asciiMode shouldBe true
            state.remembered shouldBe ManualLanguageMode.ASCII
            val number = state.command(engine, true, explicit = false)
            engine.apply(number).applied shouldBe true
            state.remembered shouldBe ManualLanguageMode.ASCII
        }

        "legacy Chinese layout navigation retains composition while number mode changes finalize natively" {
            val engine = ModeEngine(composition = "64")
            val state = KeyboardLanguageState()
            val sameLanguage = state.command(engine, false, explicit = false, effect = KeyboardModeEffect.COMMIT_ON_CHANGE)
            engine.apply(sameLanguage) shouldBe KeyboardModeResult(true, false)
            engine.operations shouldBe emptyList()
            engine.composition shouldBe "64"
            val number = state.command(engine, true, explicit = false, effect = KeyboardModeEffect.COMMIT_ON_CHANGE)
            engine.apply(number) shouldBe KeyboardModeResult(true, true)
            engine.commits shouldBe listOf("你")
            engine.operations shouldBe listOf("commit:false", "ascii:true")
            state.remembered shouldBe ManualLanguageMode.UNSET
        }

        "forced editor actions remain ASCII and never overwrite prior Chinese or unset memory" {
            listOf(ManualLanguageMode.UNSET, ManualLanguageMode.NATIVE, ManualLanguageMode.ASCII).forEach { memory ->
                val engine = ModeEngine()
                val state = KeyboardLanguageState(memory)
                val forced = state.command(engine, false, restricted = true)
                forced.asciiMode shouldBe true
                forced.remembersLanguage shouldBe false
                state.remembered shouldBe memory
                val result = engine.apply(forced)
                state.complete(forced, result, engine.editor, engine.schema) shouldBe true
                state.remembered shouldBe memory
            }
        }

        "new editor and schema reject whole old native commands without text or option effects" {
            listOf(true, false).forEach { changeEditor ->
                val engine = ModeEngine(composition = "64")
                val state = KeyboardLanguageState()
                val old = state.command(engine, true)
                if (changeEditor) engine.editor += 1 else engine.schema = "luna_pinyin"
                engine.apply(old).applied shouldBe false
                engine.operations shouldBe emptyList()
                engine.commits shouldBe emptyList()
                state.complete(old, KeyboardModeResult(true, true), engine.editor, engine.schema) shouldBe false
            }
        }

        "a boundary reached during native commit rejects the following setOption" {
            val engine = ModeEngine(composition = "64")
            val state = KeyboardLanguageState()
            val old = state.command(engine, true)
            engine.afterCommit = { engine.editor += 1 }
            engine.apply(old).applied shouldBe false
            engine.operations shouldBe listOf("commit:false")
            engine.ascii shouldBe false
        }

        "automatic mode restore cannot clear composition left by a failed prior manual commit" {
            val engine = ModeEngine(composition = "64")
            val state = KeyboardLanguageState()
            val restore = state.command(engine, true, explicit = false, effect = KeyboardModeEffect.MODE_ONLY)
            engine.apply(restore) shouldBe KeyboardModeResult(false, false)
            engine.operations shouldBe emptyList()
            engine.composition shouldBe "64"
            state.remembered shouldBe ManualLanguageMode.UNSET
        }

        "failed composition commit preserves old native mode and restores the last successful language preference" {
            val engine = ModeEngine(composition = "64")
            val state = KeyboardLanguageState()
            val first = state.command(engine, true)
            val second = state.command(engine, false)
            engine.commitSucceeds = false
            val firstResult = engine.apply(first)
            state.complete(first, firstResult, engine.editor, engine.schema) shouldBe false
            val secondResult = engine.apply(second)
            state.complete(second, secondResult, engine.editor, engine.schema) shouldBe true
            engine.ascii shouldBe false
            engine.composition shouldBe "64"
            engine.commits shouldBe emptyList()
            engine.operations shouldBe listOf("commit:false", "commit:false")
            state.remembered shouldBe ManualLanguageMode.UNSET
        }

        "an earlier successful native action cannot repaint or persist over a newer manual intent" {
            val engine = ModeEngine()
            val state = KeyboardLanguageState()
            val english = state.command(engine, true)
            val chinese = state.command(engine, false)
            state.complete(english, engine.apply(english), engine.editor, engine.schema) shouldBe false
            state.remembered shouldBe ManualLanguageMode.NATIVE
            state.complete(chinese, engine.apply(chinese), engine.editor, engine.schema) shouldBe true
            state.remembered shouldBe ManualLanguageMode.NATIVE
        }

        "a failed latest manual intent restores earlier confirmed language even after a newer navigation revision" {
            val engine = ModeEngine()
            val state = KeyboardLanguageState()
            val english = state.command(engine, true)
            val chinese = state.command(engine, false)
            val navigation = state.command(engine, true, explicit = false, effect = KeyboardModeEffect.MODE_ONLY)
            state.complete(english, engine.apply(english), engine.editor, engine.schema) shouldBe false
            engine.composition = "unfinalized"
            engine.commitSucceeds = false
            state.complete(chinese, engine.apply(chinese), engine.editor, engine.schema) shouldBe false
            state.remembered shouldBe ManualLanguageMode.ASCII
            state.complete(navigation, engine.apply(navigation), engine.editor, engine.schema) shouldBe true
            state.remembered shouldBe ManualLanguageMode.ASCII
        }

        "settled completions cannot run twice or let old failure roll back successful language" {
            val engine = ModeEngine()
            val state = KeyboardLanguageState()
            val english = state.command(engine, true)
            state.complete(english, engine.apply(english), engine.editor, engine.schema) shouldBe true
            state.complete(english, KeyboardModeResult(false, false), engine.editor, engine.schema) shouldBe false
            state.remembered shouldBe ManualLanguageMode.ASCII
            state.plannedAscii(engine.editor, engine.schema, false) shouldBe true
        }

        "pending intents reject old native option callbacks and settled sync does not write memory" {
            val engine = ModeEngine()
            val state = KeyboardLanguageState()
            val english = state.command(engine, true)
            state.synchronizeNative(engine.editor, engine.schema, false) shouldBe null
            state.plannedAscii(engine.editor, engine.schema, false) shouldBe true
            state.complete(english, engine.apply(english), engine.editor, engine.schema) shouldBe true
            state.synchronizeNative(engine.editor, engine.schema, false) shouldBe false
            state.remembered shouldBe ManualLanguageMode.ASCII
        }

        "process reconstruction retains explicit language and schema-theme changes resolve fresh IDs" {
            val engine = ModeEngine()
            val firstProcess = KeyboardLanguageState()
            firstProcess.command(engine, true)
            val savedPreference = firstProcess.remembered.storedValue
            val replacement = KeyboardLanguageState(ManualLanguageMode.fromStored(savedPreference) ?: ManualLanguageMode.UNSET)
            rememberedMainKeyboard(replacement.remembered, "new-native", "new-letter", "old-lock") shouldBe "new-letter"
            rememberedMainKeyboard(ManualLanguageMode.NATIVE, "new-native", "new-letter", "old-lock") shouldBe "new-native"
            rememberedMainKeyboard(ManualLanguageMode.UNSET, "new-native", "new-letter", "legacy-reset") shouldBe "legacy-reset"
        }

        "temporary snapshots reuse layout only in the same schema and loaded theme" {
            val keyboards = listOf("default", "letter", "custom-lock")
            canReuseMainLayout("t9", 17, "custom-lock", "t9", 17, keyboards) shouldBe true
            canReuseMainLayout("t9", 17, "custom-lock", "full", 17, keyboards) shouldBe false
            canReuseMainLayout("t9", 17, "custom-lock", "t9", 18, keyboards) shouldBe false
            canReuseMainLayout("t9", 0, "custom-lock", "t9", 17, keyboards) shouldBe false
            canReuseMainLayout("t9", 17, "removed-lock", "t9", 17, keyboards) shouldBe false
        }
    })
