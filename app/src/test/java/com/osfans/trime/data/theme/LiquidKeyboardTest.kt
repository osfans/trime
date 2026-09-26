/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme

import com.osfans.trime.data.theme.model.LiquidKeyboard
import com.osfans.trime.util.int
import com.osfans.trime.util.mapping
import com.osfans.trime.util.pairs
import com.osfans.trime.util.sequence
import com.osfans.trime.util.string
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class LiquidKeyboardTest :
    BehaviorSpec({
        fun decode(yaml: String): Theme = ThemeTestSupport.yaml.decodeFromYamlNode<Theme>(
            ThemeTestSupport.yaml.parseToYamlNode(yaml.trimIndent()).mapping!!,
        )

        Given("a liquid keyboard with metrics, a fixed key bar and two keyboards") {
            val keyboard = decode(
                """
            name: test
            liquid_keyboard:
              single_width: 60
              key_height: 40
              margin_x: 5
              fixed_key_bar:
                position: top
                keys: [Shift, Delete]
              keyboards: [emoji, symbols]
              emoji:
                type: SINGLE
                name: Emoji
                keys: "🙂😂"
              symbols:
                type: SYMBOL
                keys:
                  - "，"
                  - {click: "-"}
                  - {click: "", label: 换行}
                  - {k: v}
            """,
            ).liquidKeyboard

            Then("the fixed keys are decoded") {
                keyboard.singleWidth shouldBe 60
                keyboard.keyHeight shouldBe 40
                keyboard.marginX shouldBe 5f
                keyboard.fixedKeyBar.position shouldBe LiquidKeyboard.KeyBar.Position.TOP
                keyboard.fixedKeyBar.keys shouldBe listOf("Shift", "Delete")
            }

            Then("the listed keyboards are decoded in order, named after their id") {
                keyboard.keyboards.map { it.id } shouldBe listOf("emoji", "symbols")
                keyboard.keyboards.map { it.name } shouldBe listOf("Emoji", "symbols")
                keyboard.getTagList() shouldBe listOf(
                    LiquidKeyboard.Tag("Emoji", LiquidKeyboard.DataType.SINGLE),
                    LiquidKeyboard.Tag("symbols", LiquidKeyboard.DataType.SYMBOL),
                )
            }

            Then("a single keyboard holds one key per code point") {
                keyboard.keyboards[0].keys shouldBe listOf(
                    LiquidKeyboard.KeyItem("🙂"),
                    LiquidKeyboard.KeyItem("😂"),
                )
            }

            Then("written out keys keep their order and expand every mapping") {
                keyboard.keyboards[1].keys shouldBe listOf(
                    LiquidKeyboard.KeyItem("，"),
                    LiquidKeyboard.KeyItem("-", ""),
                    LiquidKeyboard.KeyItem("", "换行"),
                    LiquidKeyboard.KeyItem("k", "v"),
                )
            }
        }

        Given("a keyboard whose keys are one multi line scalar") {
            Then("one key is read per line and empty lines are dropped") {
                decode(
                    """
                name: test
                liquid_keyboard:
                  keyboards: [sym]
                  sym:
                    type: SYMBOL
                    keys: |
                      a
                      b

                      c
                """,
                ).liquidKeyboard.keyboards.single().keys shouldBe listOf(
                    LiquidKeyboard.KeyItem("a"),
                    LiquidKeyboard.KeyItem("b"),
                    LiquidKeyboard.KeyItem("c"),
                )
            }
        }

        Given("a keyboard without keys") {
            Then("a missing keys entry means no keys") {
                decode(
                    """
                name: test
                liquid_keyboard:
                  keyboards: [sym]
                  sym:
                    type: SYMBOL
                """,
                ).liquidKeyboard.keyboards.single().keys shouldBe emptyList()
            }

            Then("an empty keys entry also means no keys") {
                decode(
                    """
                name: test
                liquid_keyboard:
                  keyboards: [sym]
                  sym:
                    type: SYMBOL
                    keys:
                """,
                ).liquidKeyboard.keyboards.single().keys shouldBe emptyList()
            }
        }

        Given("a keyboards list naming a section that is not defined") {
            Then("the missing keyboard is skipped") {
                decode(
                    """
                name: test
                liquid_keyboard:
                  keyboards: [emoji, missing]
                  emoji:
                    type: SINGLE
                    keys: "a"
                """,
                ).liquidKeyboard.keyboards.map { it.id } shouldBe listOf("emoji")
            }
        }

        Given("a keyboard without a type") {
            Then("only that keyboard is skipped, the rest is decoded") {
                decode(
                    """
                name: test
                liquid_keyboard:
                  keyboards: [broken, ok]
                  broken:
                    keys: "x"
                  ok:
                    type: SYMBOL
                    keys: "y"
                """,
                ).liquidKeyboard.keyboards.map { it.id } shouldBe listOf("ok")
            }
        }

        Given("a liquid keyboard section that is missing, empty or unrelated") {
            Then("the defaults are used instead of failing the theme") {
                decode("name: test").liquidKeyboard shouldBe LiquidKeyboard()
                decode("name: test\nliquid_keyboard:").liquidKeyboard shouldBe LiquidKeyboard()
                decode("name: test\nliquid_keyboard: 3").liquidKeyboard shouldBe LiquidKeyboard()
            }
        }

        Given("the built in trime theme") {
            Then("its keyboards are exactly the ids its source lists, in order") {
                val (theme, node) = ThemeTestSupport.themeAndNode("src/main/assets/shared/trime.yaml")
                val liquid = node.pairs["liquid_keyboard"]
                theme.liquidKeyboard.keyboards.map { it.id } shouldBe
                    liquid?.pairs?.get("keyboards")?.sequence?.items?.mapNotNull { it.string }
                theme.liquidKeyboard.singleWidth shouldBe (liquid?.pairs?.get("single_width")?.int ?: 0)
                theme.liquidKeyboard.keyHeight shouldBe (liquid?.pairs?.get("key_height")?.int ?: 0)
            }

            Then("the tongwenfeng theme decodes its keyboards as well") {
                ThemeTestSupport.decodeBuiltinTheme("tongwenfeng.trime.yaml")
                    .liquidKeyboard.keyboards.isNotEmpty() shouldBe true
            }
        }
    })
