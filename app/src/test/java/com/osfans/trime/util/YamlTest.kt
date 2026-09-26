// SPDX-FileCopyrightText: 2015 - 2026 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.util

import com.charleskorn.kaml.AmbiguousQuoteStyle
import com.charleskorn.kaml.AnchorsAndAliases
import com.charleskorn.kaml.MultiLineStringStyle
import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.SequenceStyle
import com.charleskorn.kaml.SingleLineStringStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.Serializable

class YamlTest :
    BehaviorSpec({
        val kaml = Yaml(
            configuration = YamlConfiguration(
                /* encodeDefaults = */
                true,
                /* strictMode = */
                false,
                /* extensionDefinitionPrefix = */
                null,
                /* polymorphismStyle = */
                PolymorphismStyle.Tag,
                /* polymorphismPropertyName = */
                "type",
                /* encodingIndentationSize = */
                2,
                /* breakScalarsAt = */
                80,
                /* sequenceStyle = */
                SequenceStyle.Block,
                /* singleLineStringStyle = */
                SingleLineStringStyle.DoubleQuoted,
                /* multiLineStringStyle = */
                MultiLineStringStyle.DoubleQuoted,
                /* ambiguousQuoteStyle = */
                AmbiguousQuoteStyle.DoubleQuoted,
                /* sequenceBlockIndent = */
                0,
                /* anchorsAndAliases = */
                AnchorsAndAliases.Permitted(maxAliasCount = 1000u),
                /* yamlNamingStrategy = */
                null,
                /* codePointLimit = */
                10 * 1024 * 1024,
                /* decodeEnumCaseInsensitive = */
                false,
            ),
        )

        fun node(yaml: String) = kaml.parseToYamlNode(yaml)["key"]!!
        fun isNull(yaml: String): Boolean = node(yaml).isNull

        Given("a plain scalar without a value") {
            Then("it is null, whatever spelling the document uses") {
                // yaml-cpp's IsNullString(), which is how librime sees these scalars.
                isNull("key:\n") shouldBe true
                isNull("key: ~\n") shouldBe true
                isNull("key: null\n") shouldBe true
                isNull("key: Null\n") shouldBe true
                isNull("key: NULL\n") shouldBe true
            }
        }

        Given("a quoted or explicit scalar") {
            Then("it is a string, because librime reads it as one") {
                isNull("key: ''\n") shouldBe false
                isNull("key: \"~\"\n") shouldBe false
                isNull("key: \"null\"\n") shouldBe false
                // An explicit tag is not the implicit one yaml-cpp checks for.
                isNull("key: !!null x\n") shouldBe false
                // kaml's node model does not keep the quoting style, so a quoted
                // capitalised spelling is indistinguishable from a plain one and is
                // still treated as null; yaml-cpp would read it as a string.
                isNull("key: 'NULL'\n") shouldBe true
            }

            Then("an ordinary plain scalar is a string") {
                isNull("key: value\n") shouldBe false
                isNull("key: 0\n") shouldBe false
            }
        }

        Given("a number or a boolean") {
            Then("kaml's own conversion is used, so its spellings keep working") {
                node("key: 42\n").int shouldBe 42
                node("key: -7\n").int shouldBe -7
                node("key: 0x2a\n").int shouldBe 42
                node("key: 0o52\n").int shouldBe 42
                node("key: 42.5\n").int shouldBe null
                node("key: value\n").int shouldBe null
                node("key: [1, 2]\n").int shouldBe null

                node("key: 1.5\n").float shouldBe 1.5f
                node("key: .inf\n").float shouldBe Float.POSITIVE_INFINITY
                node("key: -.inf\n").float shouldBe Float.NEGATIVE_INFINITY
                node("key: .nan\n").float!!.isNaN() shouldBe true
                node("key: value\n").float shouldBe null

                node("key: true\n").boolean shouldBe true
                node("key: True\n").boolean shouldBe true
                node("key: FALSE\n").boolean shouldBe false
                node("key: yes\n").boolean shouldBe null
            }
        }

        Given("a node accessed by key or index") {
            Then("it looks the child up when it can, and gives null when it cannot") {
                val document = kaml.parseToYamlNode("a:\n  b: 1\nc: [x, y]\n")
                document["a"]!!.mapping!!.pairs.mapValues { it.value.string } shouldBe mapOf("b" to "1")
                document["c"]!!.sequence!!.items.map { it.string } shouldBe listOf("x", "y")
                document["c"]!!["1"]!!.string shouldBe "y"
                document["c"]!!["7"] shouldBe null
                document["missing"] shouldBe null
                document["a"]!!["missing"] shouldBe null
                yamlScalarOf("x")["a"] shouldBe null
            }

            Then("the builders produce nodes that read back the same way") {
                yamlMapOf("a" to yamlScalarOf("1")).pairs["a"]!!.string shouldBe "1"
                yamlListOf(yamlScalarOf("x"), yamlScalarOf("y")).items.map { it.string } shouldBe listOf("x", "y")
            }
        }

        Given("a document with merge keys") {
            Then("the merged mapping is expanded, like yaml-cpp does") {
                val node = kaml.parseToYamlNode(
                    "defaults: &defaults\n  a: 1\n  b: 2\nnode:\n  <<: *defaults\n  b: 3\n",
                )
                val merged = node["node"]!!.mapping!!.pairs
                merged.keys shouldBe setOf("a", "b")
                merged["a"]!!.string shouldBe "1"
                merged["b"]!!.string shouldBe "3"
            }
        }

        Given("a document that expands more aliases than the budget allows") {
            Then("parsing is aborted instead of expanding all of them") {
                // Aliases into alias-free nodes count as one each, so this exceeds
                // the budget of 1000 by plain repetition.
                val references = (0 until 1100).joinToString("\n") { "key$it: *base" }
                val document = "base: &base [1, 2, 3]\n$references\n"
                val thrown = shouldThrow<Throwable> { kaml.parseToYamlNode(document) }
                (thrown.message ?: "") shouldContain "Maximum number of aliases"
            }
        }

        Given("a serializable class") {
            Then("it is decoded through kaml, tolerating keys it does not know") {
                // Sound effect descriptors are authored by users, so a key the model
                // does not know must not fail the whole file.
                val fixture = kaml.decodeFromString(
                    YamlDecodingFixture.serializer(),
                    "name: click\nfolder: click\nunknown:\n  nested: 1\n",
                )
                fixture shouldBe YamlDecodingFixture(name = "click", folder = "click")
            }
        }
    })

@Serializable
private data class YamlDecodingFixture(
    val name: String = "",
    val folder: String,
)
