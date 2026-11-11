/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class KeyTextFitterTest :
    BehaviorSpec({
        Given("文字在按键内框内放得下") {
            When("宽高都不超出") {
                Then("保持原字号和原比例") {
                    val fit = KeyTextFitter.fit(
                        desiredSize = 15f,
                        textWidth = 40f,
                        textHeight = 18f,
                        maxWidth = 50f,
                        maxHeight = 30f,
                        minSize = 8f,
                    )
                    fit.textSize shouldBe 15f
                    fit.textScaleX shouldBe 1f
                }
            }
        }

        Given("文字宽度略微超出") {
            When("压缩到 80% 以内就能放下") {
                Then("只做横向压缩，不缩小字号") {
                    val fit = KeyTextFitter.fit(
                        desiredSize = 15f,
                        textWidth = 100f,
                        textHeight = 18f,
                        maxWidth = 80f,
                        maxHeight = 30f,
                        minSize = 8f,
                    )
                    fit.textSize shouldBe 15f
                    fit.textScaleX shouldBe 0.8f
                }
            }
        }

        Given("文字宽度超出较多") {
            When("压缩到 80% 仍放不下") {
                Then("继续缩小字号，并保持 80% 压缩") {
                    val fit = KeyTextFitter.fit(
                        desiredSize = 15f,
                        textWidth = 100f,
                        textHeight = 18f,
                        maxWidth = 50f,
                        maxHeight = 30f,
                        minSize = 8f,
                    )
                    fit.textSize shouldBe 9.375f
                    fit.textScaleX shouldBe 0.8f
                    // 最终宽度回到 50，正好填满可用宽度
                    (100f * fit.textSize / 15f * fit.textScaleX) shouldBe 50f
                }
            }
        }

        Given("文字高度超出") {
            When("按高度等比缩小字号") {
                Then("缩小后的文字高度回到可用区域内") {
                    val fit = KeyTextFitter.fit(
                        desiredSize = 15f,
                        textWidth = 40f,
                        textHeight = 30f,
                        maxWidth = 100f,
                        maxHeight = 20f,
                        minSize = 8f,
                    )
                    fit.textSize shouldBe 10f
                    fit.textScaleX shouldBe 1f
                }
            }
        }

        Given("缩到最小字号仍然放不下") {
            When("最小字号生效") {
                Then("字号停在下限，由绘制层负责裁剪") {
                    val fit = KeyTextFitter.fit(
                        desiredSize = 15f,
                        textWidth = 200f,
                        textHeight = 18f,
                        maxWidth = 50f,
                        maxHeight = 30f,
                        minSize = 12f,
                    )
                    fit.textSize shouldBe 12f
                    fit.textScaleX shouldBe 0.8f
                }
            }
        }

        Given("文字尺寸无效") {
            When("宽度为 0") {
                Then("不调整字号") {
                    val fit = KeyTextFitter.fit(
                        desiredSize = 15f,
                        textWidth = 0f,
                        textHeight = 18f,
                        maxWidth = 50f,
                        maxHeight = 30f,
                        minSize = 8f,
                    )
                    fit.textSize shouldBe 15f
                    fit.textScaleX shouldBe 1f
                }
            }
        }
    })
