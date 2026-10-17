// SPDX-FileCopyrightText: 2015 - 2026 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme

import android.util.Log
import com.charleskorn.kaml.YamlNode
import com.osfans.trime.data.theme.model.KeyActionToken
import com.osfans.trime.ime.keyboard.KeyAction
import com.osfans.trime.ime.keyboard.KeyCode
import com.osfans.trime.ime.keyboard.KeyCodeTestSupport
import com.osfans.trime.util.mapping
import com.osfans.trime.util.pairs
import com.osfans.trime.util.string
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import java.io.File
import java.nio.file.Files

class ThemeLoaderTest :
    BehaviorSpec({
        val platformLookup = KeyCode.androidKeyNameToCode
        beforeSpec { KeyCode.androidKeyNameToCode = KeyCodeTestSupport::androidKeyNameToCode }
        afterSpec { KeyCode.androidKeyNameToCode = platformLookup }

        fun node(yaml: String): YamlNode = ThemeTestSupport.yaml.parseToYamlNode(yaml)

        fun resources(vararg pairs: Pair<String, String>): (String) -> YamlNode? {
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
                patched.mapping!!.pairs["__patch"]!!.string shouldBe "theme.custom:/patch?"
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
                expanded.mapping!!.pairs["name"]!!.string shouldBe "from_custom"
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

        Given("the real built-in source files with explicit language navigation") {
            listOf("trime.yaml", "tongwenfeng.trime.yaml").forEach { filename ->
                Then("$filename keeps the email and URL corpus on its real ASCII letter keyboard") {
                    val file = File("src/main/assets/shared/$filename")
                    val loaded = ThemeLoader.loadFromSource(
                        file.nameWithoutExtension,
                        file,
                        ThemeLoader.SourceLoader { null },
                    ).shouldBeInstanceOf<ThemeLoader.ThemeLoadResult.Success>().theme
                    val letter = loaded.presetKeyboards.getValue("letter")
                    letter.asciiMode shouldBe 1
                    letter.keys.size shouldBe if (filename == "trime.yaml") 47 else 37
                    // These corpus tasks start unshifted. Every character must use an
                    // actual click/long binding on this page, without a layout detour.
                    val bindings = letter.keys.flatMap { key ->
                        listOfNotNull(key.ascii ?: key.click, key.longClick.takeIf { key.popup.isEmpty() })
                    }.filterIsInstance<KeyActionToken.Plain>()
                    listOf("trime.test+16@example.org", "https://example.org/a-b?x=16&lang=zh").forEach { corpus ->
                        corpus.forEach { value ->
                            val routes = bindings.filter { it.token == value.toString() }
                            check(routes.isNotEmpty()) { "$filename: no visible click/long route for <$value>" }
                            // Literal ASCII key codes resolve without a platform character
                            // map or JNI. Preset collisions must not hijack that output.
                            val expectedCode = KeyCode.nameToKeyCode(value.toString())
                            check(expectedCode != 0)
                            routes.forEach { token ->
                                val action = KeyAction(token, loaded.presetKeys)
                                action.code shouldBe expectedCode
                                action.modifier shouldBe 0
                                action.commit shouldBe ""
                                action.text shouldBe ""
                                action.command shouldBe ""
                                action.select shouldBe ""
                                action.toggle shouldBe ""
                                action.isFunctional shouldBe false
                            }
                        }
                    }
                    if (filename == "tongwenfeng.trime.yaml") {
                        listOf(
                            Triple("h", "+", "!"),
                            Triple("k", "&", "*"),
                            Triple("l", "=", "(){Left}"),
                        ).forEach { (click, output, preserved) ->
                            val key = letter.keys.single { it.click == KeyActionToken.Plain(click) }
                            key.hint shouldBe output
                            key.swipeUp shouldBe KeyActionToken.Plain(preserved)
                            key.swipeDown shouldBe KeyActionToken.Plain(if (click == "k") "_" else output)
                            key.popup shouldBe emptyList()
                            key.ascii shouldBe null
                            key.composing shouldBe null
                        }
                    }
                }

                Then("$filename retains reachable language actions and navigation bindings through the source loader") {
                    val file = File("src/main/assets/shared/$filename")
                    val loaded = ThemeLoader.loadFromSource(
                        file.nameWithoutExtension,
                        file,
                        ThemeLoader.SourceLoader { null },
                    ).shouldBeInstanceOf<ThemeLoader.ThemeLoadResult.Success>().theme
                    mapOf("Keyboard_ascii" to ".ascii", "Keyboard_native" to ".native", "Keyboard_return" to ".return")
                        .forEach { (action, target) -> loaded.presetKeys.getValue(action).select shouldBe target }
                    val t9 = loaded.presetKeyboards.getValue("luna_pinyin_t9")
                    t9.keys.size shouldBe 17
                    t9.asciiKeyboard shouldBe "letter"
                    val english = t9.keys.single { it.click == KeyActionToken.Plain("Keyboard_number") }
                    english.longClick shouldBe KeyActionToken.Plain("Keyboard_ascii")
                    english.hint shouldBe "EN"
                    val letter = loaded.presetKeyboards.getValue("letter")
                    if (filename == "trime.yaml") {
                        letter.keys.single { it.click == KeyActionToken.Plain("Mode_switch") }.ascii shouldBe
                            KeyActionToken.Plain("Keyboard_native")
                    } else {
                        letter.keys.single { it.click == KeyActionToken.Plain("Keyboard_native") }.longClick shouldBe
                            KeyActionToken.Plain("Theme_settings")
                    }
                    listOf("number", "symbols").forEach { layout ->
                        val back = loaded.presetKeyboards.getValue(layout).keys.single {
                            it.click == KeyActionToken.Plain("Keyboard_return")
                        }
                        back.ascii shouldBe null
                        back.composing shouldBe null
                        back.hasMenu shouldBe null
                        back.paging shouldBe null
                    }
                    loaded.presetKeyboards.getValue("symbols").keys.single {
                        it.click == KeyActionToken.Plain("Keyboard_number")
                    }.composing shouldBe null
                }
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
                        sourceFile("name: from_source\nstyle: {}\npreset_color_schemes: {default: {}}\n"),
                        noResources,
                    )
                (result as? ThemeLoader.ThemeLoadResult.Success)?.theme?.name shouldBe "from_source"
            }

            Then("normal native parsing is informational while source errors retain their warning and cause") {
                val entries = mutableListOf<Triple<Int, String, Throwable?>>()
                val tree = object : Timber.Tree() {
                    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                        entries.add(Triple(priority, message, t))
                    }
                }
                Timber.plant(tree)
                try {
                    ThemeLoader.loadFromSource(
                        "native-theme",
                        sourceFile("name: base\nstyle: {}\n__patch: {preset_keyboards/luna_pinyin_t9: {}}\n"),
                        ThemeLoader.SourceLoader { null },
                    ) shouldBe null
                    val decision = entries.single()
                    decision.first shouldBe Log.INFO
                    decision.second.contains("native-theme") shouldBe true
                    decision.second.contains("preset_keyboards/luna_pinyin_t9") shouldBe true
                    decision.second.contains('\n') shouldBe false
                    decision.third shouldBe null
                    val unreadable = sourceFile("").apply {
                        check(delete())
                        check(mkdir())
                        check(isDirectory)
                    }
                    for (file in listOf(sourceFile("name: [\n"), sourceFile("__include: missing:/style\n"), unreadable)) {
                        entries.clear()
                        ThemeLoader.loadFromSource("broken-theme", file, ThemeLoader.SourceLoader { null }) shouldBe null
                        val failure = entries.single()
                        failure.first shouldBe Log.WARN
                        failure.second.contains("broken-theme") shouldBe true
                        (failure.third != null) shouldBe true
                    }
                } finally {
                    Timber.uproot(tree)
                }
            }

            Then("an existing malformed optional custom file rejects the source theme and its name") {
                val file = sourceFile("name: base\nstyle: {}\n")
                val custom = sourceFile("patch: [\n")
                fun loader() = ThemeLoader.SourceLoader { id -> custom.takeIf { id == "theme.custom" } }
                ThemeLoader.loadFromSource("theme", file, loader()) shouldBe null
                ThemeLoader.loadSourceNode("theme", file, loader()) shouldBe null
            }

            Then("an existing malformed optional include is not treated as a missing resource") {
                val file = sourceFile("name: base\nstyle:\n  __include: shared:/style?\n")
                val shared = sourceFile("style: [\n")
                fun loader() = ThemeLoader.SourceLoader { id -> shared.takeIf { id == "shared" } }
                ThemeLoader.loadFromSource("theme", file, loader()) shouldBe null
                ThemeLoader.loadSourceNode("theme", file, loader()) shouldBe null
            }

            Then("actually absent optional custom and include files still allow source loading") {
                val file = sourceFile(
                    "name: base\nstyle:\n  __include: missing:/style?\n" +
                        "preset_color_schemes: {default: {}}\n",
                )
                val result = ThemeLoader.loadFromSource("theme", file, ThemeLoader.SourceLoader { null })
                (result as? ThemeLoader.ThemeLoadResult.Success)?.theme?.name shouldBe "base"
                ThemeLoader.loadSourceNode("theme", file, ThemeLoader.SourceLoader { null })?.mapping?.pairs?.get("name")?.string shouldBe "base"
            }

            Then("a fresh load retries a repaired custom file and reads later edits") {
                val file = sourceFile("name: base\nstyle: {}\npreset_color_schemes: {default: {}}\n")
                val custom = sourceFile("patch: [\n")
                fun loader() = ThemeLoader.SourceLoader { id -> custom.takeIf { id == "theme.custom" } }
                ThemeLoader.loadFromSource("theme", file, loader()) shouldBe null
                custom.writeText("patch:\n  name: repaired\n")
                val repaired = ThemeLoader.loadFromSource("theme", file, loader())
                (repaired as? ThemeLoader.ThemeLoadResult.Success)?.theme?.name shouldBe "repaired"
                custom.writeText("patch:\n  name: edited\n")
                val edited = ThemeLoader.loadFromSource("theme", file, loader())
                (edited as? ThemeLoader.ThemeLoadResult.Success)?.theme?.name shouldBe "edited"
            }

            Then("cancellation during the root or optional custom lookup propagates") {
                shouldThrow<CancellationException> {
                    ThemeLoader.loadFromSource("theme", sources = ThemeLoader.SourceLoader { throw CancellationException("root lookup cancelled") })
                }
                val file = sourceFile("name: base\nstyle: {}\n")
                shouldThrow<CancellationException> {
                    ThemeLoader.loadFromSource("theme", file, ThemeLoader.SourceLoader { throw CancellationException("custom lookup cancelled") })
                }
            }

            Then("a theme that declares no color scheme is refused") {
                // Nothing could be rendered with it, so it is reported as a load
                // failure instead of crashing when a scheme is first needed.
                val result =
                    ThemeLoader.loadFromSource(
                        "theme",
                        sourceFile("name: no_scheme\nstyle: {}\n"),
                        noResources,
                    )
                (result as? ThemeLoader.ThemeLoadResult.Failure)
                    ?.error
                    .shouldBeInstanceOf<ThemeLoader.ThemeLoadError.NoColorScheme>()
            }

            Then("what the checks found travels with the loaded theme") {
                val result =
                    ThemeLoader.loadFromSource(
                        "theme",
                        sourceFile("name: t\nheight: 5\nstyle: {}\npreset_color_schemes: {default: {}}\n"),
                        noResources,
                    )
                val findings = (result as? ThemeLoader.ThemeLoadResult.Success)?.findings
                findings.orEmpty().map { it.code } shouldContain ThemeDiagnostics.Code.UNKNOWN_TOP_LEVEL_KEY
            }

            Then("an explicit file wins over the loader cache") {
                val loader = ThemeLoader.SourceLoader { null }
                fun nameOf(file: File): String? = loader.load("theme", file)?.mapping?.pairs?.get("name")?.string
                nameOf(sourceFile("name: first\n")) shouldBe "first"
                nameOf(sourceFile("name: second\n")) shouldBe "second"
            }
        }

        Given("the source lookup") {
            val root = File.createTempFile("themes", "").apply {
                delete()
                mkdirs()
                deleteOnExit()
            }

            fun sourceFile(name: String, yaml: String): File = File(root, name).apply { writeText(yaml) }
            fun loader() = ThemeLoader.SourceLoader { ThemeLoader.findSourceFile(it, listOf(root)) }

            Then("a resource is looked up in the given roots, in order") {
                sourceFile("theme.yaml", "name: from_source\n")
                ThemeLoader.findSourceFile("theme", listOf(root))?.readText() shouldBe "name: from_source\n"
                ThemeLoader.findSourceFile("missing", listOf(root)) shouldBe null
            }

            Then("a resource that escapes its root is refused even when the include is optional") {
                val outside = File.createTempFile("outside", ".yaml").apply {
                    writeText("name: outside\n")
                    deleteOnExit()
                }
                // Reachable through the root as `../<name>`, but still out of bounds.
                root.resolve("../${outside.name}").isFile shouldBe true
                shouldThrow<ThemeDslExpander.UnsupportedDsl> {
                    ThemeLoader.findSourceFile("../${outside.nameWithoutExtension}", listOf(root))
                }
                val file = sourceFile("escape-source.yaml", "name: base\n__include: ../${outside.nameWithoutExtension}:/?\n")
                ThemeLoader.loadFromSource("escape-source", file, loader()) shouldBe null
                ThemeLoader.loadSourceNode("escape-source", file, loader()) shouldBe null
            }

            Then("a sibling whose name starts with the root name is still outside the root") {
                val sibling = File(root.parentFile, root.name + "-outside").apply { mkdirs() }
                try {
                    File(sibling, "theme.yaml").writeText("name: outside\n")
                    root.resolve("../${sibling.name}/theme.yaml").isFile shouldBe true
                    shouldThrow<ThemeDslExpander.UnsupportedDsl> {
                        ThemeLoader.findSourceFile("../${sibling.name}/theme", listOf(root))
                    }
                } finally {
                    sibling.deleteRecursively()
                }
            }

            Then("a real symbolic link to an outside file rejects even an optional patch") {
                val outside = File.createTempFile("outside-theme", ".yaml").apply { writeText("patch:\n  name: outside\n") }
                val link = File(root, "linked.yaml")
                try {
                    Files.createSymbolicLink(link.toPath(), outside.toPath())
                    Files.isSymbolicLink(link.toPath()) shouldBe true
                    link.isFile shouldBe true
                    shouldThrow<ThemeDslExpander.UnsupportedDsl> {
                        ThemeLoader.findSourceFile("linked", listOf(root))
                    }
                    val file = sourceFile("linked-source.yaml", "name: base\n__patch: linked:/patch?\n")
                    ThemeLoader.loadFromSource("linked-source", file, loader()) shouldBe null
                    ThemeLoader.loadSourceNode("linked-source", file, loader()) shouldBe null
                } finally {
                    link.delete()
                    outside.delete()
                }
            }

            Then("a name that an __include provides is resolved") {
                val file = sourceFile(
                    "included.yaml",
                    "name_source: from_include\nname:\n  __include: /name_source\n",
                )
                val sources = ThemeLoader.SourceLoader { null }
                ThemeLoader.loadSourceNode("included", file, sources)?.mapping?.pairs?.get("name")?.string shouldBe "from_include"
            }

            Then("an existing custom directory blocks source loading instead of becoming an absent patch") {
                val custom = File(root, "directory-theme.custom.yaml").apply { mkdirs() }
                try {
                    val file = sourceFile("directory-theme.yaml", "name: base\nstyle: {}\n")
                    shouldThrow<ThemeDslExpander.UnsupportedDsl> {
                        ThemeLoader.findSourceFile("directory-theme.custom", listOf(root))
                    }
                    ThemeLoader.loadFromSource("directory-theme", file, loader()) shouldBe null
                    ThemeLoader.loadSourceNode("directory-theme", file, loader()) shouldBe null
                } finally {
                    custom.delete()
                }
            }

            Then("a source outside the supported DSL is not expanded") {
                val file = sourceFile("dsl.yaml", "name: dsl\na: {keys/+: [1]}\n")
                ThemeLoader.loadSourceNode("dsl", file, ThemeLoader.SourceLoader { null }) shouldBe null
            }
        }

        Given("the deployed artifact's null serialization") {
            Then("remaining null map values and list elements require native deployment") {
                listOf(
                    "name: null\nstyle: {}\n",
                    "name: base\nstyle: {}\n__patch: {name: ~}\n",
                    "name: base\nstyle: {candidate_font: [null]}\n",
                ).forEach { yaml ->
                    shouldThrow<ThemeDslExpander.UnsupportedDsl> { theme(yaml) }
                }
            }

            Then("an include's null no-op is accepted after expansion consumes it") {
                theme("name: inherited\nbase: {popup_width: 52}\nstyle: {__include: /base, popup_width: null}\n").style.popupWidth shouldBe 52
            }

            Then("quoted null text remains a literal theme name") {
                theme("name: 'null'\nstyle: {}\n").name shouldBe "null"
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
                letter.asciiMode shouldBe 1
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
                letter.asciiMode shouldBe 1
                letter.keys.size shouldBe 1
            }
        }

        Given("theme diagnostics") {
            /** Collects the lines the loader logs, one per finding. */
            class CollectingTree(val lines: MutableList<String>) : Timber.Tree() {
                override fun log(
                    priority: Int,
                    tag: String?,
                    message: String,
                    t: Throwable?,
                ) {
                    val level =
                        when (priority) {
                            Log.INFO -> "I"
                            Log.WARN -> "W"
                            else -> "?"
                        }
                    lines += "$level $message"
                }
            }

            val lines = mutableListOf<String>()
            val tree = CollectingTree(lines)
            beforeTest { Timber.plant(tree) }
            afterTest { Timber.uproot(tree) }

            Then("decodeAndReport decodes and reports in one step") {
                // The JVM cannot parse colors (android.graphics is not mocked),
                // so only the structural findings are asserted here; the color
                // findings are covered by ThemeDiagnosticsTest.
                val result =
                    ThemeLoader.decodeAndReport(
                        "fixture",
                        node(
                            """
                            config_version: "3.0"
                            name: fixture
                            style: {candidate_texts_size: 12}
                            preset_color_schemes: {default: {back_color: "#000000"}}
                            height: 5
                            """.trimIndent(),
                        ).mapping!!,
                    )
                result.shouldBeInstanceOf<ThemeLoader.ThemeLoadResult.Success>().theme.name shouldBe "fixture"
                lines.filter { "unknown key" in it } shouldBe
                    listOf(
                        "I Theme 'fixture': unknown key 'height' in ''; the runtime ignores it",
                        "W Theme 'fixture': unknown key 'candidate_texts_size' in 'style'; the runtime ignores it",
                    )
            }
        }
    })
