/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.data.prefs

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException

class InputSetupControllerTest :
    StringSpec({
        "full-pinyin simplified uses the actual zh_simp switch" {
            val engine = SetupEngine()
            val chosen = InputSetupSelection(InputLayout.FULL_PINYIN, ChineseVariant.SIMPLIFIED)
            InputSetupController.apply(engine, chosen).selection shouldBe chosen
            engine.schema shouldBe "luna_pinyin_simp"
            engine.writes.shouldContainExactly(listOf("zh_simp=true"))
            engine.selections.shouldContainExactly(listOf("luna_pinyin_simp"))
        }

        "nine-key simplified uses simplification and an actual nine-key schema" {
            val engine = SetupEngine()
            val chosen = InputSetupSelection(InputLayout.T9, ChineseVariant.SIMPLIFIED)
            InputSetupController.apply(engine, chosen).selection shouldBe chosen
            engine.schema shouldBe "luna_pinyin_t9"
            engine.writes.shouldContainExactly(listOf("simplification=true"))
        }

        "changing characters within nine-key does not select or deploy another schema" {
            val engine = SetupEngine("luna_pinyin_t9")
            engine.values.getValue(engine.schema)["simplification"] = true
            InputSetupController.apply(engine, InputSetupSelection(InputLayout.T9, ChineseVariant.TRADITIONAL))
            engine.selections shouldBe emptyList()
            engine.writes.shouldContainExactly(listOf("simplification=false"))
            engine.predictionClears shouldBe 1
        }

        "reapplying a confirmed selection changes no native state" {
            val engine = SetupEngine("luna_pinyin_t9")
            InputSetupController.apply(engine, InputSetupSelection(InputLayout.T9, ChineseVariant.TRADITIONAL))
            engine.selections shouldBe emptyList()
            engine.writes shouldBe emptyList()
            engine.predictionClears shouldBe 0
        }

        "a custom scheme is inspectable and is not silently assigned Chinese options" {
            val engine = SetupEngine("my_custom")
            val state = InputSetupController.inspect(engine)
            state.schemaId shouldBe "my_custom"
            state.selection shouldBe null
            engine.writes shouldBe emptyList()
            engine.selections shouldBe emptyList()
        }

        "a disabled bundled scheme leaves the current custom configuration untouched" {
            val engine = SetupEngine("my_custom")
            engine.enabled = setOf("my_custom")
            shouldThrow<InputSetupException> {
                InputSetupController.apply(engine, InputSetupSelection(InputLayout.T9, ChineseVariant.SIMPLIFIED))
            }.reason shouldBe InputSetupFailure.UNAVAILABLE_SCHEMA
            engine.schema shouldBe "my_custom"
            engine.writes shouldBe emptyList()
            engine.selections shouldBe emptyList()
        }

        "unfinished input is rejected before selecting or clearing anything" {
            val engine = SetupEngine()
            engine.empty = false
            shouldThrow<InputSetupException> {
                InputSetupController.apply(engine, InputSetupSelection(InputLayout.T9, ChineseVariant.SIMPLIFIED))
            }.reason shouldBe InputSetupFailure.PENDING_INPUT
            engine.schema shouldBe "luna_pinyin"
            engine.writes shouldBe emptyList()
            engine.selections shouldBe emptyList()
            engine.predictionClears shouldBe 0
        }

        "a customized target with the wrong keyboard rolls back the old scheme and switches" {
            val engine = SetupEngine()
            engine.states["luna_pinyin_t9"] = engine.states.getValue("luna_pinyin_t9").copy(keyboard = "qwerty")
            engine.values.getValue("luna_pinyin")["ascii_mode"] = true
            engine.values.getValue("luna_pinyin")["full_shape"] = true
            val failure = shouldThrow<InputSetupException> {
                InputSetupController.apply(engine, InputSetupSelection(InputLayout.T9, ChineseVariant.SIMPLIFIED))
            }
            failure.reason shouldBe InputSetupFailure.UNSUPPORTED_CONFIGURATION
            failure.previousRestored shouldBe true
            engine.schema shouldBe "luna_pinyin"
            engine.values.getValue(engine.schema)["ascii_mode"] shouldBe true
            engine.values.getValue(engine.schema)["full_shape"] shouldBe true
            engine.writes.none { it.startsWith("prediction=") } shouldBe true
        }

        "an option setter failing after changing its value restores the previous character mode" {
            val engine = SetupEngine("luna_pinyin_t9")
            engine.setterFailure = "simplification"
            val failure = shouldThrow<InputSetupException> {
                InputSetupController.apply(engine, InputSetupSelection(InputLayout.T9, ChineseVariant.SIMPLIFIED))
            }
            failure.previousRestored shouldBe true
            engine.schema shouldBe "luna_pinyin_t9"
            engine.values.getValue(engine.schema)["simplification"] shouldBe false
        }

        "a familiar switch name without the matching converter cannot certify simplified output" {
            val engine = SetupEngine("luna_pinyin_t9")
            engine.states[engine.schema] = engine.states.getValue(engine.schema).copy(simplifierOption = null)
            InputSetupController.inspect(engine).selection shouldBe null
            shouldThrow<InputSetupException> {
                InputSetupController.apply(engine, InputSetupSelection(InputLayout.T9, ChineseVariant.SIMPLIFIED))
            }.reason shouldBe InputSetupFailure.UNSUPPORTED_CONFIGURATION
            engine.writes shouldBe emptyList()
            engine.selections shouldBe emptyList()
        }

        "a native option that ignores the requested value cannot mark configuration successful" {
            val engine = SetupEngine("luna_pinyin_t9")
            engine.ignoredOption = "simplification"
            shouldThrow<InputSetupException> {
                InputSetupController.apply(engine, InputSetupSelection(InputLayout.T9, ChineseVariant.SIMPLIFIED))
            }.reason shouldBe InputSetupFailure.APPLY_FAILED
            InputSetupController.inspect(engine).selection shouldBe InputSetupSelection(InputLayout.T9, ChineseVariant.TRADITIONAL)
        }

        "failure to restore the previous scheme is explicitly reported" {
            val engine = SetupEngine()
            engine.states["luna_pinyin_t9"] = engine.states.getValue("luna_pinyin_t9").copy(switches = setOf("ascii_mode"))
            engine.failedSelection = "luna_pinyin"
            shouldThrow<InputSetupException> {
                InputSetupController.apply(engine, InputSetupSelection(InputLayout.T9, ChineseVariant.SIMPLIFIED))
            }.previousRestored shouldBe false
        }

        "cancellation after a native change rolls back and remains cancellation" {
            val engine = SetupEngine("luna_pinyin_t9")
            engine.cancelSetter = "simplification"
            shouldThrow<CancellationException> {
                InputSetupController.apply(engine, InputSetupSelection(InputLayout.T9, ChineseVariant.SIMPLIFIED))
            }
            engine.values.getValue(engine.schema)["simplification"] shouldBe false
        }
    })

private class SetupEngine(initialSchema: String = "luna_pinyin") : InputSetupEngine {
    var schema = initialSchema
    var empty = true
    var setterFailure: String? = null
    var cancelSetter: String? = null
    var ignoredOption: String? = null
    var failedSelection: String? = null
    var predictionClears = 0
    val selections = mutableListOf<String>()
    val writes = mutableListOf<String>()
    val states = mutableMapOf(
        "luna_pinyin" to InputSetupEngineState("luna_pinyin", "Full", false, "", setOf("simplification", "ascii_mode", "full_shape", "prediction"), "simplification"),
        "luna_pinyin_simp" to InputSetupEngineState("luna_pinyin_simp", "Simplified", false, "", setOf("zh_simp", "ascii_mode"), "zh_simp"),
        "luna_pinyin_t9" to InputSetupEngineState("luna_pinyin_t9", "Nine-key", true, "luna_pinyin_t9", setOf("simplification", "ascii_mode"), "simplification"),
        "my_custom" to InputSetupEngineState("my_custom", "Custom", false, "custom", setOf("ascii_mode", "custom_option"), null),
    )
    var enabled = states.keys.toSet()
    val values = states.mapValues { (_, state) -> state.switches.associateWith { false }.toMutableMap() }.toMutableMap()

    override suspend fun state() = states.getValue(schema)

    override suspend fun enabledSchemas() = enabled

    override suspend fun isEmpty() = empty

    override suspend fun selectSchema(id: String): Boolean {
        selections += id
        if (id == failedSelection) return false
        schema = id
        // Simulate a native schema's reset switches; rollback must restore cached values.
        values.getValue(id)["ascii_mode"] = false
        values.getValue(id)["full_shape"] = false
        return true
    }

    override suspend fun getOption(name: String) = values.getValue(schema)[name] ?: false

    override suspend fun setOption(name: String, value: Boolean) {
        writes += "$name=$value"
        if (name != ignoredOption) values.getValue(schema)[name] = value
        if (name == setterFailure) {
            setterFailure = null
            throw IllegalStateException("synthetic option failure")
        }
        if (name == cancelSetter) {
            cancelSetter = null
            throw CancellationException("synthetic cancellation")
        }
    }

    override suspend fun clearPrediction() {
        predictionClears += 1
    }
}
