// SPDX-FileCopyrightText: 2015 - 2026 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme

import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.yamlMap
import com.osfans.trime.util.get
import com.osfans.trime.util.mapping
import com.osfans.trime.util.pairs
import com.osfans.trime.util.string
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
        ): YamlNode = ThemeDslExpander.expand(
            resourceId,
            ThemeTestSupport.yaml.parseToYamlNode(yaml),
        ) { id ->
            resources[id]?.let { ThemeTestSupport.yaml.parseToYamlNode(it) }
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
                val letter = expanded.pairs!!["letter"]!!.mapping!!
                letter.pairs["name"]!!.string shouldBe "base"
                letter.pairs["ascii_mode"]!!.string shouldBe "1"
            }

            Then("mappings merge recursively") {
                val nested = expanded.pairs!!["letter"]!!.mapping!!.pairs["nested"]!!.mapping!!
                nested.pairs["a"]!!.string shouldBe "1"
                nested.pairs["b"]!!.string shouldBe "3"
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
                expanded.pairs!!["keyboard"]!!.mapping!!.pairs["name"]!!.string shouldBe "from_base"
            }

            Then("a missing resource fails") {
                shouldThrow<ThemeDslExpander.UnresolvedReference> {
                    expand("keyboard:\n  __include: missing:/foo\n")
                }
            }

            Then("a missing resource is ignored when marked optional") {
                val expanded = expand("keyboard:\n  __include: missing:/foo?\n  own: 1\n")
                expanded.pairs!!["keyboard"]!!.mapping!!.pairs["own"]!!.string shouldBe "1"
            }

            Then("a missing patch is ignored when marked optional") {
                val expanded = expand("keyboard:\n  own: 1\n  __patch: \"missing:/patch?\"\n")
                expanded.pairs!!["keyboard"]!!.mapping!!.pairs["own"]!!.string shouldBe "1"
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
            Then("every construct outside the subset is refused") {
                listOf(
                    // __append / __merge directives
                    "a: {__append: [1]}",
                    "a: {__merge: {b: 1}}",
                    // path and list operators
                    "a: {keys/@next: 1}",
                    "a: {nested/key: 1}",
                    "a: {list/+: [1]}",
                    "a: {key/=: 1}",
                    // a __patch list, and a patch of the wrong type
                    "a: {__patch: [{k: 1}]}",
                    // Literal patch nodes with their own compiler dependencies.
                    "base: {k: 1}\na: {__patch: {__include: /base}}",
                    "a: {__patch: {__patch: {k: 1}}}",
                    "a: {__patch: {'': {name: replacement, style: {}}}}",
                    "s: scalar\na: {__patch: /s}",
                    // a non-scalar __include
                    "a: {__include: [/x, /y]}",
                    "a: {__include: {k: 1}}",
                ).forEach { yaml ->
                    shouldThrow<ThemeDslExpander.UnsupportedDsl> { expand(yaml) }
                }
            }

            Then("patching a non-mapping node is rejected") {
                shouldThrow<ThemeDslExpander.UnsupportedDsl> {
                    expand("s: scalar\na: {__include: /s, __patch: {k: 1}}\n")
                }
            }

            Then("merging a mapping into a non-mapping sibling is rejected") {
                shouldThrow<ThemeDslExpander.UnsupportedDsl> {
                    expand("base: {keys: []}\na: {__include: /base, keys: {b: 1}}\n")
                }
            }

            Then("list references fall back instead of accepting another index syntax or skipping an optional item") {
                listOf("0", "@0", "@last").forEach { index ->
                    listOf("", "?").forEach { optional ->
                        shouldThrow<ThemeDslExpander.UnsupportedDsl> {
                            expand("items: [{name: first}]\ncopy: {__include: '/items/$index$optional'}\n")
                        }
                    }
                }
            }

            Then("an optional reference cannot skip a child inherited by a pending ancestor") {
                shouldThrow<ThemeDslExpander.UnsupportedDsl> {
                    expand(
                        "base: {k: {v: 1}}\nderived: {__include: /base}\n" +
                            "copy: {__include: '/derived/k?'}\n",
                    )
                }
            }

            Then("a cross-resource subtree cannot bypass its root custom patch") {
                val shared = ThemeLoader.applyCustomPatch(
                    "shared",
                    ThemeTestSupport.yaml.parseToYamlNode("style: {value: before}\n"),
                )
                val custom = ThemeTestSupport.yaml.parseToYamlNode("patch: {style: {value: after}}\n")
                shouldThrow<ThemeDslExpander.UnsupportedDsl> {
                    ThemeDslExpander.expand(
                        "theme",
                        ThemeTestSupport.yaml.parseToYamlNode("style: {__include: 'shared:/style?'}\n"),
                    ) { id ->
                        when (id) {
                            "shared" -> shared
                            "shared.custom" -> custom
                            else -> null
                        }
                    }
                }
            }

            Then("patching a node without an include overwrites it") {
                expand("a: {__patch: {k: 1}}\n")["a"]!!.mapping!!.pairs["k"]!!.string shouldBe "1"
            }
        }

        Given("a sibling key of an __include") {
            val template = "base: {a: 1, b: 2}\n"

            Then("a key without a value leaves the included value untouched") {
                val a = expand(template + "x: {__include: /base, a: }\n")["x"]!!.mapping!!
                a.pairs["a"]!!.string shouldBe "1"
            }

            Then("an empty string replaces the included value") {
                val a = expand(template + "x: {__include: /base, a: \"\"}\n")["x"]!!.mapping!!
                a.pairs["a"]!!.string shouldBe ""
            }

            Then("librime's unambiguous null spellings leave the included value untouched too") {
                listOf("~", "null").forEach { spelling ->
                    val a = expand(template + "x: {__include: /base, a: $spelling}\n")["x"]!!.mapping!!
                    a.pairs["a"]!!.string shouldBe "1"
                }
            }

            Then("capitalized null spellings fall back because Kaml loses their quoting style") {
                listOf("Null", "NULL", "'Null'", "'NULL'").forEach { spelling ->
                    shouldThrow<ThemeDslExpander.UnsupportedDsl> {
                        expand(template + "x: {__include: /base, a: $spelling}\n")
                    }
                }
            }

            Then("a quoted null spelling is still a string") {
                val a = expand(template + "x: {__include: /base, a: 'null'}\n")["x"]!!.mapping!!
                a.pairs["a"]!!.string shouldBe "null"
            }

            Then("explicit tags fall back instead of losing their scalar type") {
                listOf("!!str", "!!null", "!local").forEach { tag ->
                    shouldThrow<ThemeDslExpander.UnsupportedDsl> {
                        expand(template + "x: {__include: /base, a: $tag null}\n")
                    }
                }
            }
        }

        Given("a theme reusing a node through a YAML anchor") {
            val expanded = expand(
                """
                inner: {v: 1}
                base: &base
                  __include: /inner
                  extra: 2
                copy: *base
                """.trimIndent(),
            )

            Then("the aliased node is expanded as well, once") {
                // kaml resolves an alias to an equal but distinct node, so the two
                // copies are expanded separately and are equal by content, not identity.
                expanded.pairs!!["copy"]!!.mapping!!.pairs["v"]!!.string shouldBe "1"
                expanded.pairs!!["copy"]!!.mapping!!.pairs["extra"]!!.string shouldBe "2"
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
                expanded.pairs!!["letter"]!!.mapping!!.pairs["name"]!!.string shouldBe "literal"
            }

            Then("a referenced patch overwrites keys") {
                val expanded = expand(
                    template + "\nletter:\n  __include: /preset_keyboards/default\n  __patch: /patches/p\n",
                )
                expanded.pairs!!["letter"]!!.mapping!!.pairs["name"]!!.string shouldBe "patched"
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
                val item = expanded.pairs!!["outer"]!!.mapping!!.pairs["list"]!!.mapping
                item shouldBe null
                val sequence = expanded.pairs!!["outer"]!!.mapping!!.pairs["list"] as YamlList
                val merged = sequence[0].mapping!!
                merged.pairs["v"]!!.string shouldBe "1"
                merged.pairs["extra"]!!.string shouldBe "2"
            }
        }

        Given("the built-in trime.yaml") {
            val file = File("src/main/assets/shared/trime.yaml")
            val expanded = ThemeDslExpander.expand("trime", ThemeTestSupport.yaml.parseToYamlNode(file.readText())) { null }

            Then("the 'letter' keyboard shares the default keyboard content") {
                val keyboards = expanded.pairs!!["preset_keyboards"]!!.mapping!!
                val default = keyboards.pairs["default"]!!.mapping!!
                val letter = keyboards.pairs["letter"]!!.mapping!!
                letter.pairs["ascii_mode"]!!.string shouldBe "1"
                letter.pairs.getValue("keys").equivalentContentTo(default.pairs.getValue("keys")) shouldBe true
                letter.pairs.getValue("name").equivalentContentTo(default.pairs.getValue("name")) shouldBe true
                letter.pairs.getValue("height").equivalentContentTo(default.pairs.getValue("height")) shouldBe true
            }

            Then("the aliased 'scj6' equals its target keyboard by content") {
                val keyboards = expanded.pairs!!["preset_keyboards"]!!.mapping!!
                keyboards.pairs.getValue("scj6").equivalentContentTo(keyboards.pairs.getValue("cangjie5")) shouldBe true
            }

            Then("the expansion is accepted by the theme decoder") {
                val theme = ThemeTestSupport.yaml.decodeFromYamlNode<Theme>(expanded.yamlMap)
                theme.presetKeyboards.getValue("letter").keys.size shouldBe
                    theme.presetKeyboards.getValue("default").keys.size
                theme.presetKeyboards.getValue("scj6").keys.size shouldBe
                    theme.presetKeyboards.getValue("cangjie5").keys.size
            }
        }
    })
