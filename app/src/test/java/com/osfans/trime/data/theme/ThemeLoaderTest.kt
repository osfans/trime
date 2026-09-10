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
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import java.io.File

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
            Then("the patch of '<id>.custom.yaml' is injected as an optional __patch reference") {
                val patched = ThemeLoader.applyCustomPatch("theme", source)
                patched.mapping!!["__patch"]!!.string shouldBe "theme.custom:/patch?"
            }

            Then("the injected patch wins over the resource") {
                val patched = ThemeLoader.applyCustomPatch("theme", source)
                val patchedTheme = ThemeLoader.decodeSource(
                    "theme",
                    patched,
                    resources("theme.custom" to "patch:\n  name: patched\n"),
                )
                patchedTheme.name shouldBe "patched"
                patchedTheme.presetKeyboards shouldContainKey "default"
            }

            Then("the patch's directives resolve in the file the patch is written in") {
                val patched = ThemeLoader.applyCustomPatch("theme", node("base: {name: from_theme}\n"))
                val expanded =
                    ThemeDslExpander.expand(
                        "theme",
                        patched,
                        resources("theme.custom" to "base: {name: from_custom}\npatch: {__include: /base}\n"),
                    )
                // The patch is `{name: from_custom}`, taken from the custom file's
                // own `base`; resolving it in the theme file would say from_theme.
                expanded.mapping!!["name"]!!.string shouldBe "from_custom"
            }

            Then("a .schema resource is patched through its matching .custom file") {
                val patched = ThemeLoader.applyCustomPatch("sometheme.schema", source)
                ThemeLoader.decodeSource(
                    "sometheme.schema",
                    patched,
                    resources("sometheme.custom" to "patch:\n  name: patched\n"),
                ).name shouldBe "patched"
            }

            Then("a patch that is not a mapping is refused") {
                shouldThrow<ThemeDslExpander.UnsupportedDsl> {
                    ThemeLoader.decodeSource(
                        "theme",
                        ThemeLoader.applyCustomPatch("theme", source),
                        resources("theme.custom" to "patch: [name, other]\n"),
                    )
                }
            }

            Then("an explicit __patch in the resource wins") {
                val explicit = node("__patch:\n  name: explicit\nname: base\n")
                ThemeLoader.applyCustomPatch("theme", explicit) shouldBe explicit
            }

            Then("a missing custom file leaves the resource untouched") {
                val patched = ThemeLoader.applyCustomPatch("theme", source)
                ThemeLoader.decodeSource("theme", patched) { null }.name shouldBe "base"
            }

            Then("a custom file without a patch node leaves the resource untouched") {
                val patched = ThemeLoader.applyCustomPatch("theme", source)
                val patchedTheme = ThemeLoader.decodeSource(
                    "theme",
                    patched,
                    resources("theme.custom" to "name: other\n"),
                )
                patchedTheme.name shouldBe "base"
            }

            Then("custom files are never patched recursively") {
                val custom = node("patch:\n  name: patched\n")
                ThemeLoader.applyCustomPatch("theme.custom", custom) shouldBe custom
                ThemeLoader.applyCustomPatch("trime.custom", custom) shouldBe custom
            }
        }

        Given("the source path") {
            fun sourceFile(yaml: String): File = File.createTempFile("theme", ".yaml").apply {
                writeText(yaml)
                deleteOnExit()
            }

            val noResources = ThemeLoader.SourceLoader { null }

            Then("anything it cannot read faithfully falls back to the deployed artifact") {
                // DSL outside the supported subset.
                ThemeLoader.loadFromSource("dsl", sourceFile("a: {keys/+: [1]}\n"), noResources) shouldBe null
                // A YAML root that is not a mapping.
                ThemeLoader.loadFromSource("scalar", sourceFile("just a scalar\n"), noResources) shouldBe null
                // A file that is not valid YAML at all.
                ThemeLoader.loadFromSource("broken", sourceFile("a: [\n"), noResources) shouldBe null
            }

            Then("a readable source is decoded without librime") {
                val result =
                    ThemeLoader.loadFromSource(
                        "theme",
                        sourceFile("name: from_source\nstyle: {}\n"),
                        noResources,
                    )
                (result as? ThemeLoader.ThemeLoadResult.Success)?.theme?.name shouldBe "from_source"
            }
        }

        Given("the source lookup") {
            val root = File.createTempFile("themes", "").apply {
                delete()
                mkdirs()
                deleteOnExit()
            }

            fun sourceFile(name: String, yaml: String): File = File(root, name).apply { writeText(yaml) }

            Then("a resource is looked up in the given roots, in order") {
                sourceFile("theme.yaml", "name: from_source\n")
                ThemeLoader.findSourceFile("theme", listOf(root))?.readText() shouldBe "name: from_source\n"
                ThemeLoader.findSourceFile("missing", listOf(root)) shouldBe null
            }

            Then("a resource that escapes its root is refused") {
                val outside = File.createTempFile("outside", ".yaml").apply {
                    writeText("name: outside\n")
                    deleteOnExit()
                }
                // Reachable through the root as `../<name>`, but still out of bounds.
                root.resolve("../${outside.name}").isFile shouldBe true
                ThemeLoader.findSourceFile("../${outside.nameWithoutExtension}", listOf(root)) shouldBe null
            }

            Then("a name that an __include provides is resolved") {
                val file = sourceFile(
                    "included.yaml",
                    "name_source: from_include\nname:\n  __include: /name_source\n",
                )
                val sources = ThemeLoader.SourceLoader { null }
                ThemeLoader.loadSourceNode("included", file, sources)?.mapping?.get("name")?.string shouldBe "from_include"
            }

            Then("a source outside the supported DSL is not expanded") {
                val file = sourceFile("dsl.yaml", "name: dsl\na: {keys/+: [1]}\n")
                ThemeLoader.loadSourceNode("dsl", file, ThemeLoader.SourceLoader { null }) shouldBe null
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
