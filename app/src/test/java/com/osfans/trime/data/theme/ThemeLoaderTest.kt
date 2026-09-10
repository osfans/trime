// SPDX-FileCopyrightText: 2015 - 2026 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme

import com.osfans.trime.util.yaml.Node
import com.osfans.trime.util.yaml.Yaml
import com.osfans.trime.util.yaml.mapping
import com.osfans.trime.util.yaml.string
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe

class ThemeLoaderTest :
    BehaviorSpec({
        fun node(yaml: String): Node = Yaml.parseToYamlNode(yaml)

        fun resources(vararg pairs: Pair<String, String>): (String) -> Node? {
            val map = pairs.toMap()
            return { id -> map[id]?.let(::node) }
        }

        fun theme(yaml: String): Theme = ThemeLoader.decodeSource("theme", node(yaml)) { null }

        val source = node(
            """
            name: base
            style: {}
            preset_keyboards:
              default: {name: default, keys: [{click: q}, {click: w}]}
            """.trimIndent(),
        )

        Given("the librime auto-patch convention") {
            Then("the patch node of '<id>.custom.yaml' is injected as an __patch directive") {
                val patched = ThemeLoader.applyCustomPatch(
                    "theme",
                    source,
                    resources("theme.custom" to "patch:\n  name: patched\n  extra: 1\n"),
                )
                patched.mapping!!["__patch"]!!.mapping!!["name"]!!.string shouldBe "patched"
            }

            Then("the injected patch wins over the resource") {
                val patched = ThemeLoader.applyCustomPatch(
                    "theme",
                    source,
                    resources("theme.custom" to "patch:\n  name: patched\n"),
                )
                val patchedTheme = ThemeLoader.decodeSource("theme", patched) { null }
                patchedTheme.name shouldBe "patched"
                patchedTheme.presetKeyboards shouldContainKey "default"
            }

            Then("a .schema resource is patched through its matching .custom file") {
                val patched = ThemeLoader.applyCustomPatch(
                    "sometheme.schema",
                    source,
                    resources("sometheme.custom" to "patch:\n  name: patched\n"),
                )
                ThemeLoader.decodeSource("sometheme.schema", patched) { null }.name shouldBe "patched"
            }

            Then("an explicit __patch in the resource wins") {
                val explicit = node("__patch:\n  name: explicit\nname: base\n")
                val patched = ThemeLoader.applyCustomPatch(
                    "theme",
                    explicit,
                    resources("theme.custom" to "patch:\n  name: patched\n"),
                )
                patched shouldBe explicit
            }

            Then("a missing custom file leaves the resource untouched") {
                ThemeLoader.applyCustomPatch("theme", source, resources()) shouldBe source
            }

            Then("a custom file without a patch node leaves the resource untouched") {
                ThemeLoader.applyCustomPatch(
                    "theme",
                    source,
                    resources("theme.custom" to "name: other\n"),
                ) shouldBe source
            }

            Then("custom files are never patched recursively") {
                val custom = node("patch:\n  name: patched\n")
                ThemeLoader.applyCustomPatch(
                    "theme.custom",
                    custom,
                    resources("theme.custom.custom" to "patch:\n  name: nested\n"),
                ) shouldBe custom
                ThemeLoader.applyCustomPatch("trime.custom", custom, resources()) shouldBe custom
            }
        }

        Given("a theme node using the supported DSL") {
            Then("decodeSource expands the DSL and decodes the result") {
                val decoded = theme(
                    """
                    name: base
                    style: {}
                    preset_keyboards:
                      default: {name: default, ascii_mode: 0, keys: [{click: q}]}
                      letter:
                        __include: /preset_keyboards/default
                        ascii_mode: 1
                    """.trimIndent(),
                )
                decoded.name shouldBe "base"
                val letter = decoded.presetKeyboards.getValue("letter")
                letter.name shouldBe "default"
                letter.asciiMode shouldBe true
            }

            Then("decodeSource resolves cross-resource references through the loader") {
                val source = node(
                    """
                    name: base
                    style: {}
                    preset_keyboards:
                      letter:
                        __include: sharedkeyboard.yaml:/letter
                    """.trimIndent(),
                )
                val decoded = ThemeLoader.decodeSource(
                    "theme",
                    source,
                    resources("sharedkeyboard" to "letter:\n  name: shared\n  ascii_mode: 1\n  keys: [{click: a}]\n"),
                )
                decoded.name shouldBe "base"
                val letter = decoded.presetKeyboards.getValue("letter")
                letter.name shouldBe "shared"
                letter.asciiMode shouldBe true
                letter.keys.size shouldBe 1
            }
        }
    })
