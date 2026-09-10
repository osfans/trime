// SPDX-FileCopyrightText: 2015 - 2026 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.util.yaml

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class ParserTest :
    BehaviorSpec({
        fun isNull(yaml: String): Boolean = Yaml.parseToYamlNode(yaml)["key"]!!.isNull

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
                isNull("key: 'NULL'\n") shouldBe false
                // An explicit tag is not the implicit one yaml-cpp checks for.
                isNull("key: !!null x\n") shouldBe false
            }

            Then("an ordinary plain scalar is a string") {
                isNull("key: value\n") shouldBe false
                isNull("key: 0\n") shouldBe false
            }
        }
    })
