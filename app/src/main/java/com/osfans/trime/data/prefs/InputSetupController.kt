/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.data.prefs

import com.osfans.trime.core.RimeApi
import com.osfans.trime.core.RimeConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

enum class InputLayout { T9, FULL_PINYIN }

enum class ChineseVariant { SIMPLIFIED, TRADITIONAL }

data class InputSetupSelection(val layout: InputLayout, val variant: ChineseVariant)

data class InputSetupState(
    val schemaId: String,
    val schemaName: String,
    val selection: InputSetupSelection?,
)

enum class InputSetupFailure { UNAVAILABLE_SCHEMA, UNSUPPORTED_CONFIGURATION, PENDING_INPUT, APPLY_FAILED }

class InputSetupException(
    val reason: InputSetupFailure,
    val previousRestored: Boolean = true,
    cause: Throwable? = null,
) : IllegalStateException(reason.name, cause)

internal data class InputSetupEngineState(
    val schemaId: String,
    val schemaName: String,
    val t9: Boolean,
    val keyboard: String,
    val switches: Set<String>,
    val simplifierOption: String?,
)

internal interface InputSetupEngine {
    suspend fun state(): InputSetupEngineState

    suspend fun enabledSchemas(): Set<String>

    suspend fun isEmpty(): Boolean

    suspend fun selectSchema(id: String): Boolean

    suspend fun getOption(name: String): Boolean

    suspend fun setOption(name: String, value: Boolean)

    suspend fun clearPrediction()
}

internal class RimeInputSetupEngine(private val rime: RimeApi) : InputSetupEngine {
    override suspend fun state(): InputSetupEngineState {
        val id = rime.selectedSchemaId()
        val schema = rime.currentSchema()
        return InputSetupEngineState(
            id,
            rime.statusCached.schemaName,
            schema.isPinyinT9,
            schema.keyboard,
            schema.switches.flatMap { if (it.name.isEmpty()) it.options else listOf(it.name) }.toSet(),
            if (id in setOf("luna_pinyin", "luna_pinyin_simp", "luna_pinyin_t9")) {
                RimeConfig.openSchema(id).use { config ->
                    val filters = config.getList("engine/filters", RimeConfig::getString)
                    val conversion = config.getString("simplifier/opencc_config").orEmpty().ifEmpty { "t2s.json" }
                    if ("simplifier" in filters && conversion == "t2s.json") {
                        config.getString("simplifier/option_name").orEmpty().ifEmpty { "simplification" }
                    } else {
                        null
                    }
                }
            } else {
                null
            },
        )
    }

    override suspend fun enabledSchemas() = rime.selectedSchemata().map { it.id }.toSet()

    override suspend fun isEmpty() = rime.getRawInput().isEmpty() &&
        (!rime.statusCached.isComposing || rime.candidateSnapshotCached.isPrediction)

    override suspend fun selectSchema(id: String) = rime.selectSchema(id)

    override suspend fun getOption(name: String) = rime.getRuntimeOption(name)

    override suspend fun setOption(name: String, value: Boolean) = rime.setRuntimeOption(name, value)

    override suspend fun clearPrediction() = rime.clearPredictionContext()
}

/** Settings and onboarding use the same native schema/option selection, without deployment. */
object InputSetupController {
    suspend fun inspect(rime: RimeApi): InputSetupState = rime.inspectInputSetup()

    suspend fun apply(rime: RimeApi, selection: InputSetupSelection): InputSetupState = rime.configureInput(selection)

    internal suspend fun inspect(engine: InputSetupEngine): InputSetupState {
        val state = engine.state()
        val option = variantOption(state)
        return InputSetupState(
            state.schemaId,
            state.schemaName,
            option?.let {
                InputSetupSelection(
                    if (state.t9) InputLayout.T9 else InputLayout.FULL_PINYIN,
                    if (engine.getOption(it)) ChineseVariant.SIMPLIFIED else ChineseVariant.TRADITIONAL,
                )
            },
        )
    }

    internal suspend fun apply(engine: InputSetupEngine, selection: InputSetupSelection): InputSetupState {
        if (!engine.isEmpty()) throw InputSetupException(InputSetupFailure.PENDING_INPUT)
        val previous = engine.state()
        // Rime may reset named switches on a schema change. Restore them on failure as well.
        // The prediction setter represents a global preference, so it is deliberately excluded.
        val previousOptions = mutableMapOf<String, Boolean>()
        (previous.switches + "ascii_mode").filter { it.isNotEmpty() && it != "prediction" }.forEach {
            previousOptions[it] = engine.getOption(it)
        }
        val target = when (selection.layout) {
            InputLayout.T9 -> "luna_pinyin_t9"
            InputLayout.FULL_PINYIN -> if (selection.variant == ChineseVariant.SIMPLIFIED) "luna_pinyin_simp" else "luna_pinyin"
        }
        if (target !in engine.enabledSchemas()) throw InputSetupException(InputSetupFailure.UNAVAILABLE_SCHEMA)
        var changed = false
        try {
            if (target != previous.schemaId) {
                changed = true
                engine.clearPrediction()
                if (!engine.selectSchema(target)) throw InputSetupException(InputSetupFailure.APPLY_FAILED)
            }
            val current = engine.state()
            if (current.schemaId != target) throw InputSetupException(InputSetupFailure.APPLY_FAILED)
            val option = variantOption(current)
                ?: throw InputSetupException(InputSetupFailure.UNSUPPORTED_CONFIGURATION)
            val simplified = selection.variant == ChineseVariant.SIMPLIFIED
            if (engine.getOption(option) != simplified) {
                changed = true
                engine.clearPrediction()
                engine.setOption(option, simplified)
            }
            val confirmed = inspect(engine)
            if (confirmed.schemaId != target || confirmed.selection != selection) {
                throw InputSetupException(InputSetupFailure.APPLY_FAILED)
            }
            return confirmed
        } catch (failure: Exception) {
            val restored = if (!changed) {
                true
            } else {
                withContext(NonCancellable) {
                    runCatching {
                        if (engine.state().schemaId != previous.schemaId && !engine.selectSchema(previous.schemaId)) {
                            return@runCatching false
                        }
                        previousOptions.forEach { (name, value) -> engine.setOption(name, value) }
                        engine.state().schemaId == previous.schemaId &&
                            previousOptions.all { (name, value) -> engine.getOption(name) == value }
                    }.getOrDefault(false)
                }
            }
            if (failure is CancellationException) {
                if (!restored) failure.addSuppressed(InputSetupException(InputSetupFailure.APPLY_FAILED, previousRestored = false))
                throw failure
            }
            throw InputSetupException(
                (failure as? InputSetupException)?.reason ?: InputSetupFailure.APPLY_FAILED,
                restored,
                failure,
            )
        }
    }

    private fun variantOption(state: InputSetupEngineState): String? {
        val option = when (state.schemaId) {
            "luna_pinyin_t9" -> {
                if (!state.t9 || state.keyboard != "luna_pinyin_t9") return null
                "simplification"
            }

            "luna_pinyin" -> {
                if (state.t9 || state.keyboard == "luna_pinyin_t9") return null
                "simplification"
            }

            "luna_pinyin_simp" -> {
                if (state.t9 || state.keyboard == "luna_pinyin_t9") return null
                "zh_simp"
            }

            else -> return null
        }
        return option.takeIf { it in state.switches && it == state.simplifierOption }
    }
}
