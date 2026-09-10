// SPDX-FileCopyrightText: 2015 - 2026 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme

import com.osfans.trime.util.yaml.Node
import com.osfans.trime.util.yaml.Yaml
import com.osfans.trime.util.yaml.get
import com.osfans.trime.util.yaml.mapping
import com.osfans.trime.util.yaml.string
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.io.File

class ThemeDslExpanderTest :
    BehaviorSpec({
        fun expand(
            yaml: String,
            resourceId: String = "theme",
            resources: Map<String, String> = emptyMap(),
        ): Node = ThemeDslExpander.expand(resourceId, Yaml.parseToYamlNode(yaml)) { id ->
            resources[id]?.let { Yaml.parseToYamlNode(it) }
        }

        Given("a theme with an __include to a local path") {
            val expanded = expand(
                """
                preset_keyboards:
                  default:
                    name: base
                    keys: [q, w]
                    nested: {a: 1, b: 2}
                letter:
                  __include: /preset_keyboards/default
                  ascii_mode: 1
                  nested: {b: 3}
                """.trimIndent(),
            )

            Then("the included keys are inherited and sibling keys override them") {
                val letter = expanded["letter"]!!.mapping!!
                letter["name"]!!.string shouldBe "base"
                letter["ascii_mode"]!!.string shouldBe "1"
            }

            Then("mappings merge recursively") {
                val nested = expanded["letter"]!!.mapping!!["nested"]!!.mapping!!
                nested["a"]!!.string shouldBe "1"
                nested["b"]!!.string shouldBe "3"
            }
        }

        Given("a theme including another resource") {
            val resources = mapOf(
                "base" to """
                    preset_keyboards:
                      default:
                        name: from_base
                        keys: [a]
                """.trimIndent(),
            )

            Then("the reference resolves through the resource loader") {
                val expanded = expand(
                    "keyboard:\n  __include: base.yaml:/preset_keyboards/default\n",
                    resources = resources,
                )
                expanded["keyboard"]!!.mapping!!["name"]!!.string shouldBe "from_base"
            }

            Then("a missing resource fails") {
                shouldThrow<ThemeDslExpander.UnresolvedReference> {
                    expand("keyboard:\n  __include: missing:/foo\n")
                }
            }

            Then("a missing resource is ignored when marked optional") {
                val expanded = expand("keyboard:\n  __include: missing:/foo?\n  own: 1\n")
                expanded["keyboard"]!!.mapping!!["own"]!!.string shouldBe "1"
            }
        }

        Given("a theme with circular references") {
            Then("expansion fails") {
                shouldThrow<ThemeDslExpander.UnresolvedReference> {
                    expand("a: {__include: /b}\nb: {__include: /a}\n")
                }
            }
        }

        Given("a theme using unsupported DSL constructs") {
            Then("an include list is rejected") {
                shouldThrow<ThemeDslExpander.UnsupportedDsl> {
                    expand("a: {__include: [/x, /y]}\n")
                }
            }

            Then("unknown directives are rejected") {
                shouldThrow<ThemeDslExpander.UnsupportedDsl> {
                    expand("a: {__merge: /x}\n")
                }
            }

            Then("path operators are rejected") {
                shouldThrow<ThemeDslExpander.UnsupportedDsl> {
                    expand("a: {list/+: [1]}\n")
                }
                shouldThrow<ThemeDslExpander.UnsupportedDsl> {
                    expand("a: {key/=: 1}\n")
                }
            }

            Then("patching a non-mapping node is rejected") {
                shouldThrow<ThemeDslExpander.UnsupportedDsl> {
                    expand("s: scalar\na: {__include: /s, __patch: {k: 1}}\n")
                }
            }

            Then("patching a node without an include overwrites it") {
                expand("a: {__patch: {k: 1}}\n")["a"]!!.mapping!!["k"]!!.string shouldBe "1"
            }
        }

        Given("a theme using __patch") {
            val template = """
                preset_keyboards:
                  default: {name: base, keys: [q]}
                patches:
                  p: {name: patched}
            """.trimIndent()

            Then("a literal patch overwrites keys") {
                val expanded = expand(
                    template + "\nletter:\n  __include: /preset_keyboards/default\n  __patch: {name: literal}\n",
                )
                expanded["letter"]!!.mapping!!["name"]!!.string shouldBe "literal"
            }

            Then("a referenced patch overwrites keys") {
                val expanded = expand(
                    template + "\nletter:\n  __include: /preset_keyboards/default\n  __patch: /patches/p\n",
                )
                expanded["letter"]!!.mapping!!["name"]!!.string shouldBe "patched"
            }
        }

        Given("a theme with nested directives") {
            Then("directives inside included nodes and sequence items are expanded") {
                val expanded = expand(
                    """
                    inner: {v: 1}
                    middle:
                      __include: /inner
                      extra: 2
                    outer:
                      list:
                        - {__include: /middle}
                    """.trimIndent(),
                )
                val item = expanded["outer"]!!.mapping!!["list"]!!.mapping
                item shouldBe null
                val sequence = expanded["outer"]!!.mapping!!["list"] as Node.Sequence
                val merged = sequence[0].mapping!!
                merged["v"]!!.string shouldBe "1"
                merged["extra"]!!.string shouldBe "2"
            }
        }

        Given("the built-in trime.yaml") {
            val file = File("src/main/assets/shared/trime.yaml")
            val expanded = ThemeDslExpander.expand("trime", Yaml.parseToYamlNode(file.readText())) { null }

            Then("the 'letter' keyboard inherits the default keyboard") {
                val keyboards = expanded["preset_keyboards"]!!.mapping!!
                val default = keyboards["default"]!!.mapping!!
                val letter = keyboards["letter"]!!.mapping!!
                letter["ascii_mode"]!!.string shouldBe "1"
                letter["keys"] shouldBe default["keys"]
                letter["name"] shouldBe default["name"]
                letter["height"] shouldBe default["height"]
            }

            Then("the pure include 'scj6' equals its target keyboard") {
                val keyboards = expanded["preset_keyboards"]!!.mapping!!
                keyboards["scj6"] shouldBe keyboards["cangjie5"]
            }

            Then("the expansion is accepted by the theme decoder") {
                val theme = Theme.decode(expanded.mapping!!)
                theme.presetKeyboards.getValue("letter").keys.size shouldBe
                    theme.presetKeyboards.getValue("default").keys.size
                theme.presetKeyboards.getValue("scj6").keys.size shouldBe
                    theme.presetKeyboards.getValue("cangjie5").keys.size
            }
        }
    })
