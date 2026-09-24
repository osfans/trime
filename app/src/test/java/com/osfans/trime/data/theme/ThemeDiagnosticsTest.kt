// SPDX-FileCopyrightText: 2015 - 2026 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme

import android.util.Log
import com.osfans.trime.data.theme.ThemeDiagnostics.Code
import com.osfans.trime.data.theme.ThemeDiagnostics.Severity
import com.osfans.trime.data.theme.model.GeneralStyle
import com.osfans.trime.ime.keyboard.KeyCode
import com.osfans.trime.util.Yaml
import com.osfans.trime.util.mapping
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import timber.log.Timber
import java.io.File

class ThemeDiagnosticsTest :
    BehaviorSpec({
        // The Android key-name lookup is a platform call that throws when not
        // mocked; the bare letters used by the fixtures resolve without it.
        beforeTest { KeyCode.androidKeyNameToCode = { 0 } }

        /** Hex-only color parser; enough to tell colors from typos. */
        fun parseHex(value: String): Int? {
            val digits = when {
                value.startsWith("#") -> value.substring(1)
                value.startsWith("0x", ignoreCase = true) -> value.substring(2)
                else -> null
            }
            return digits?.let { runCatching { java.lang.Long.parseLong(it, 16).toInt() }.getOrNull() }
        }

        fun lint(yaml: String): List<ThemeDiagnostics.Finding> {
            val node = Yaml.parseToYamlNode(yaml).mapping!!
            return ThemeDiagnostics.lint(Theme.decode(node), node, ::parseHex)
        }

        fun lintCodes(yaml: String): List<Code> = lint(yaml).map(ThemeDiagnostics.Finding::code)

        Given("a minimal valid theme") {
            val yaml =
                """
                config_version: "3.0"
                name: minimal
                style: {candidate_text_size: 20}
                preset_color_schemes:
                  default: {back_color: "#000000"}
                preset_keys:
                  A: {label: a, send: a}
                preset_keyboards:
                  letter: {name: letter, keys: [{click: a}]}
                """.trimIndent()

            Then("nothing is reported") {
                lint(yaml) shouldBe emptyList()
            }
        }

        Given("unknown keys") {
            Then("top-level keys the runtime ignores are reported once each") {
                val findings =
                    lint(
                        """
                        config_version: "3.0"
                        name: t
                        style: {}
                        height: 5
                        round_corner: 3
                        preset_color_schemes: {default: {back_color: "#000000"}}
                        """.trimIndent(),
                    )
                findings.filter { it.code == Code.UNKNOWN_TOP_LEVEL_KEY } shouldContainExactlyInAnyOrder
                    listOf(
                        ThemeDiagnostics.Finding(
                            Severity.INFO,
                            Code.UNKNOWN_TOP_LEVEL_KEY,
                            "unknown key 'height' in ''; the runtime ignores it",
                            "/height",
                        ),
                        ThemeDiagnostics.Finding(
                            Severity.INFO,
                            Code.UNKNOWN_TOP_LEVEL_KEY,
                            "unknown key 'round_corner' in ''; the runtime ignores it",
                            "/round_corner",
                        ),
                    )
            }

            Then("style typos are warnings pointing at the key") {
                val findings = lint(
                    """
                    config_version: "3.0"
                    name: t
                    style: {candidate_texts_size: 22, candidate_text_size: 20}
                    preset_color_schemes: {default: {back_color: "#000000"}}
                    """.trimIndent(),
                )
                findings.map { it.code to it.path } shouldBe
                    listOf(Code.UNKNOWN_STYLE_KEY to "style/candidate_texts_size")
            }
        }

        Given("config_version") {
            val base =
                """
                name: t
                style: {}
                preset_color_schemes: {default: {back_color: "#000000"}}
                """.trimIndent()

            Then("a missing version is informational") {
                lintCodes(base) shouldBe listOf(Code.CONFIG_VERSION_MISSING)
            }

            Then("the supported version is silent") {
                lintCodes("config_version: \"${ThemeDiagnostics.SUPPORTED_CONFIG_VERSION}\"\n$base") shouldBe emptyList()
            }

            Then("an older version is silent") {
                lintCodes("config_version: '0.40'\n$base") shouldBe emptyList()
            }

            Then("a newer version warns with a migration hint") {
                lint("config_version: \"99.1\"\n$base").single().let {
                    it.code shouldBe Code.CONFIG_VERSION_UNSUPPORTED
                    it.severity shouldBe Severity.WARNING
                    it.message shouldBe
                        "'config_version' 99.1 is newer than the supported 3.0; " +
                        "settings added by the newer format are ignored"
                }
            }

            Then("a newer minor version warns") {
                lintCodes("config_version: \"3.1\"\n$base") shouldBe listOf(Code.CONFIG_VERSION_UNSUPPORTED)
            }

            Then("a value that is not a version warns") {
                lintCodes("config_version: abc\n$base") shouldBe listOf(Code.CONFIG_VERSION_INVALID)
            }
        }

        Given("color schemes") {
            Then("a theme without schemes is reported") {
                lintCodes("config_version: \"3.0\"\nname: t\nstyle: {}\npreset_color_schemes: {}\n") shouldBe
                    listOf(Code.NO_COLOR_SCHEME)
            }

            Then("a missing default scheme is reported") {
                lintCodes(
                    "config_version: \"3.0\"\nname: t\nstyle: {}\npreset_color_schemes: {ink: {back_color: \"#000000\"}}\n",
                ) shouldBe listOf(Code.MISSING_DEFAULT_SCHEME)
            }

            Then("a link to a missing scheme is reported") {
                val findings = lint(
                    """
                    config_version: "3.0"
                    name: t
                    style: {}
                    preset_color_schemes:
                      default: {back_color: "#000000", dark_scheme: night}
                    """.trimIndent(),
                )
                findings.single().let {
                    it.code shouldBe Code.MISSING_SCHEME_LINK
                    it.path shouldBe "preset_color_schemes/default/dark_scheme"
                    it.message shouldBe "scheme 'default' links to missing scheme 'night' via 'dark_scheme'"
                }
            }

            Then("a value the color parser rejects is a warning naming the value") {
                val findings = lint(
                    """
                    config_version: "3.0"
                    name: t
                    style: {}
                    preset_color_schemes:
                      default: {back_color: "not-a-color"}
                    """.trimIndent(),
                )
                findings.filter { it.code == Code.INVALID_COLOR_VALUE }.map { it.path } shouldBe
                    listOf("preset_color_schemes/default/back_color")
            }

            Then("a value a custom scheme key carries is reported at that key") {
                val findings = lint(
                    """
                    config_version: "3.0"
                    name: t
                    style: {}
                    preset_color_schemes: {default: {back_color: "#000000", my_base: "nope"}}
                    fallback_colors: {key_back_color: my_base}
                    """.trimIndent(),
                )
                findings.filter { it.code == Code.INVALID_COLOR_VALUE }.let { invalid ->
                    invalid.map { it.path } shouldBe listOf("preset_color_schemes/default/my_base")
                    invalid.map { it.message } shouldBe
                        listOf("scheme 'default': 'my_base' cannot be parsed as a color (value 'nope')")
                }
            }

            Then("a fallback_colors entry pointing at nothing is reported") {
                val findings = lint(
                    """
                    config_version: "3.0"
                    name: t
                    style: {}
                    preset_color_schemes: {default: {back_color: "#000000"}}
                    fallback_colors:
                      key_back_color: no_such_key
                    """.trimIndent(),
                )
                findings.single().let {
                    it.code shouldBe Code.BROKEN_FALLBACK_TARGET
                    it.severity shouldBe Severity.WARNING
                    it.path shouldBe "fallback_colors/key_back_color"
                    it.message shouldBe "fallback_colors: 'key_back_color' points at 'no_such_key', which no scheme defines"
                }
            }

            Then("a fallback_colors entry reaching a built-in chain is fine") {
                lintCodes(
                    """
                    config_version: "3.0"
                    name: t
                    style: {}
                    preset_color_schemes: {default: {back_color: "#000000", text_color: "#ffffff"}}
                    fallback_colors:
                      candidate_text_color: text_color
                      my_panel: candidate_text_color
                    """.trimIndent(),
                ) shouldBe emptyList()
            }

            Then("a fallback_colors chain that cycles is reported") {
                lintCodes(
                    """
                    config_version: "3.0"
                    name: t
                    style: {}
                    preset_color_schemes: {default: {back_color: "#000000"}}
                    fallback_colors:
                      my_panel: my_panel_color
                      my_panel_color: my_panel
                    """.trimIndent(),
                ) shouldBe listOf(Code.BROKEN_FALLBACK_TARGET, Code.BROKEN_FALLBACK_TARGET)
            }
        }

        Given("preset keys and keyboards") {
            Then("an unresolvable preset send is a warning") {
                val findings = lint(
                    """
                    config_version: "3.0"
                    name: t
                    style: {}
                    preset_color_schemes: {default: {back_color: "#000000"}}
                    preset_keys:
                      BROKEN: {label: x, send: NotAKey}
                    """.trimIndent(),
                )
                findings.single().let {
                    it.code shouldBe Code.UNRESOLVABLE_PRESET_SEND
                    it.path shouldBe "preset_keys/BROKEN"
                    it.message shouldBe "preset 'BROKEN' has an unrecognized send 'NotAKey'"
                }
            }

            Then("keyboards referencing missing keyboards are reported") {
                val findings = lint(
                    """
                    config_version: "3.0"
                    name: t
                    style: {}
                    preset_color_schemes: {default: {back_color: "#000000"}}
                    preset_keyboards:
                      letter:
                        name: letter
                        import_preset: nope
                        ascii_keyboard: also-nope
                        landscape_keyboard: letter
                        keys: [{click: a}]
                    """.trimIndent(),
                )
                findings.map { it.code to it.path } shouldBe
                    listOf(
                        Code.MISSING_KEYBOARD_REFERENCE to "preset_keyboards/letter/import_preset",
                        Code.MISSING_KEYBOARD_REFERENCE to "preset_keyboards/letter/ascii_keyboard",
                    )
            }
            Then("keyboards importing each other in a cycle are reported once") {
                val findings = lint(
                    """
                    config_version: "3.0"
                    name: t
                    style: {}
                    preset_color_schemes: {default: {back_color: "#000000"}}
                    preset_keyboards:
                      letter: {name: letter, import_preset: symbols, keys: [{click: a}]}
                      symbols: {name: symbols, import_preset: letter, keys: [{click: a}]}
                    """.trimIndent(),
                )
                findings.single().let {
                    it.code shouldBe Code.CYCLIC_KEYBOARD_REFERENCE
                    it.severity shouldBe Severity.WARNING
                    it.path shouldBe "preset_keyboards/symbols/import_preset"
                    it.message shouldBe
                        "preset keyboards import each other in a cycle ('letter' -> 'symbols') " +
                        "via 'import_preset'; the reference never resolves"
                }
            }
        }

        Given("reporting") {
            /** Collects the lines [ThemeDiagnostics.report] logs, one per finding. */
            class CollectingTree(private val lines: MutableList<String>) : Timber.Tree() {
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

            /** Runs [block] and returns what it logged. */
            fun captured(block: () -> Unit): List<String> {
                val lines = mutableListOf<String>()
                val tree = CollectingTree(lines)
                Timber.plant(tree)
                try {
                    block()
                } finally {
                    Timber.uproot(tree)
                }
                return lines
            }

            Then("each finding is logged once, at its severity") {
                val node =
                    Yaml.parseToYamlNode(
                        """
                        config_version: "3.0"
                        name: t
                        style: {candidate_texts_size: 12}
                        preset_color_schemes: {default: {back_color: "#000000", hilited_back_color: "nope"}}
                        height: 5
                        """.trimIndent(),
                    ).mapping!!
                val findings = ThemeDiagnostics.lint(Theme.decode(node), node, ::parseHex)
                val lines = captured { ThemeDiagnostics.log("fixture", findings) }
                lines shouldBe
                    listOf(
                        "I Theme 'fixture': unknown key 'height' in ''; the runtime ignores it",
                        "W Theme 'fixture': unknown key 'candidate_texts_size' in 'style'; the runtime ignores it",
                        "W Theme 'fixture': scheme 'default': 'hilited_back_color' cannot be parsed as a color (value 'nope')",
                    )
            }

            Then("a theme the runtime can read is silent") {
                val node =
                    Yaml.parseToYamlNode(
                        """
                        config_version: "3.0"
                        name: t
                        style: {candidate_text_size: 12}
                        preset_color_schemes: {default: {back_color: "#000000"}}
                        """.trimIndent(),
                    ).mapping!!
                val findings = ThemeDiagnostics.lint(Theme.decode(node), node, ::parseHex)
                captured { ThemeDiagnostics.log("clean", findings) } shouldBe emptyList()
                findings shouldBe emptyList()
            }
        }

        Given("the report of a theme") {
            val findings =
                lint(
                    """
                    config_version: "3.0"
                    name: t
                    height: 5
                    style: {candidate_texts_size: 12}
                    preset_color_schemes: {default: {back_color: "#000000"}}
                    """.trimIndent(),
                )

            Then("the findings of a checked theme are rendered with their code") {
                val text = ThemeDiagnostics.format("mytheme", "My Theme", findings)
                text shouldContain "Theme: My Theme (mytheme)\n"
                text shouldContain "Findings: 2 (1 warnings, 1 info)\n"
                text shouldContain "[INFO] /height: unknown key 'height'"
                text shouldContain "(UNKNOWN_TOP_LEVEL_KEY)"
                text shouldContain "(UNKNOWN_STYLE_KEY)"
            }

            Then("a theme without findings says so") {
                ThemeDiagnostics.format("mytheme", "My Theme", emptyList()) shouldBe
                    "Theme: My Theme (mytheme)\nNo findings.\n"
            }

            Then("a theme whose checks could not run says so") {
                ThemeDiagnostics.format("mytheme", "My Theme", null) shouldBe
                    "Theme: My Theme (mytheme)\n" +
                    "Static checks could not run for this theme.\n"
            }
        }

        Given("the shipped themes") {
            // The settings the shipped themes carry but the runtime never reads.
            val trimeYamlFindings =
                listOf(
                    "INFO UNKNOWN_TOP_LEVEL_KEY /android_keys",
                    "WARNING UNKNOWN_STYLE_KEY style/preview_font",
                    "WARNING UNKNOWN_STYLE_KEY style/preview_height",
                    "WARNING UNKNOWN_STYLE_KEY style/preview_offset",
                    "WARNING UNKNOWN_STYLE_KEY style/preview_text_size",
                )
            val tongwenfengFindings =
                listOf(
                    "INFO UNKNOWN_TOP_LEVEL_KEY /height",
                    "INFO UNKNOWN_TOP_LEVEL_KEY /round_corner",
                    "INFO UNKNOWN_TOP_LEVEL_KEY /colors",
                )

            fun lintBuiltin(file: String): List<ThemeDiagnostics.Finding> {
                val (theme, node) = ThemeTestSupport.themeAndNode("src/main/assets/shared/$file")
                return ThemeDiagnostics.lint(theme, node, ::parseHex)
            }

            Then("only the documented findings are reported") {
                listOf(
                    "trime.yaml" to trimeYamlFindings,
                    "tongwenfeng.trime.yaml" to tongwenfengFindings,
                ).forEach { (file, expected) ->
                    val findings = lintBuiltin(file)
                    // Send values are platform key names: a device resolves them,
                    // the JVM cannot, so they are summarised instead of listed.
                    val sends = findings.filter { it.code == Code.UNRESOLVABLE_PRESET_SEND }
                    sends.forEach { finding ->
                        val send = finding.message.substringBeforeLast('\'').substringAfterLast('\'')
                        // The message names the send, so an empty extraction means
                        // the format changed and this summary checks nothing.
                        send.isEmpty() shouldBe false
                        send shouldBe send.uppercase()
                    }
                    val rest =
                        findings
                            .filterNot { it.code == Code.UNRESOLVABLE_PRESET_SEND }
                            .map { "${it.severity} ${it.code} ${it.path}" }
                    rest shouldBe expected
                }
            }
        }

        Given("the key vocabularies") {
            Then("GeneralStyle.KNOWN_KEYS matches the keys decode reads") {
                val literals =
                    Regex("""node(?:\.pairs)?\["([a-z_0-9]+)"]""")
                        .findAll(File("src/main/java/com/osfans/trime/data/theme/model/GeneralStyle.kt").readText())
                        .map { it.groupValues[1] }
                        .toSet()
                GeneralStyle.KNOWN_KEYS shouldBe literals
            }

            Then("Theme.TOP_LEVEL_KEYS matches the keys decode reads") {
                val literals =
                    Regex("""node(?:\.pairs)?\["([a-z_0-9]+)"]""")
                        .findAll(File("src/main/java/com/osfans/trime/data/theme/Theme.kt").readText())
                        .map { it.groupValues[1] }
                        .toSet()
                Theme.TOP_LEVEL_KEYS shouldBe literals + setOf("config_version", "author", "description", "version")
            }
        }
    })
