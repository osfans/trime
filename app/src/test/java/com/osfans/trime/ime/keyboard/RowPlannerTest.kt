/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.ime.keyboard

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * 行布局与跨行的纯计算：键按权重换行、跨行键在下方各行预留 x 区间、跨行高度累加。
 * 这里不需要 Android，[planKeyRows] 与 [spanHeight] 都是纯函数。
 */
class RowPlannerTest :
    BehaviorSpec({
        // 100 权重 = 300px，每个权重 33 的键宽 99px，一行正好放 3 个
        val allowedWidth = 300
        val unit = 3f
        val rowHeight = 50

        fun key(
            weight: Float = 33f,
            height: Float = 0f,
            clickable: Boolean = true,
            rowSpan: Int = 1,
        ) = KeyMetrics(weight, height, clickable, rowSpan)

        fun plan(
            keys: List<KeyMetrics>,
            maxColumns: Int = Int.MAX_VALUE,
        ) = planKeyRows(keys, allowedWidth, unit, unit, maxColumns, rowHeight)

        /** 把 RowPlan 落成矩形（x 区间 + y 区间），用于检查是否重叠。 */
        fun rects(
            plan: RowPlan,
            heights: List<Int>,
        ): List<Pair<IntRange, IntRange>> {
            val yTop = mutableListOf(0)
            heights.forEach { yTop.add(yTop.last() + it) }
            return plan.placements.map { p ->
                val top = yTop[p.row]
                val bottom = top + spanHeight(p.row, p.rowSpan, heights)
                (p.x until p.x + p.widthPx) to (top until bottom)
            }
        }

        Given("a plain keyboard without row spans") {
            val plan = plan(List(12) { key() })

            When("keys are laid out") {
                Then("they wrap into four rows of three") {
                    plan.rowCount shouldBe 4
                    plan.placements.map { it.row } shouldBe
                        listOf(0, 0, 0, 1, 1, 1, 2, 2, 2, 3, 3, 3)
                }

                Then("every row height is the default") {
                    plan.rowRawHeight shouldBe listOf(50, 50, 50, 50)
                }
            }
        }

        Given("a nine-grid keyboard whose confirm key spans two rows") {
            // 3 列 × 4 行：前 8 个键 + 第 3 行末位的确认键（跨 2 行） + 末行 2 个键
            val plan = plan(List(8) { key() } + key(rowSpan = 2) + List(2) { key() })

            When("the confirm key is placed") {
                Then("it starts on row 2 at the last column and keeps the span") {
                    val confirm = plan.placements[8]
                    confirm.row shouldBe 2
                    confirm.column shouldBe 2
                    confirm.rowSpan shouldBe 2
                }
            }

            When("the following row is filled") {
                Then("its keys stop before the reserved span instead of covering it") {
                    plan.rowCount shouldBe 4
                    plan.placements[9].row shouldBe 3
                    plan.placements[9].x shouldBe 0
                    plan.placements[10].row shouldBe 3
                    plan.placements[10].x shouldBe 99
                }
            }

            When("the confirm key is measured") {
                Then("its height covers both rows") {
                    val heights = List(plan.rowCount) { 50 }
                    spanHeight(plan.placements[8].row, plan.placements[8].rowSpan, heights) shouldBe 100
                }
            }

            When("all keys are turned into rectangles") {
                Then("no two keys overlap") {
                    val heights = List(plan.rowCount) { 50 }
                    val boxes = rects(plan, heights)
                    for (i in boxes.indices) {
                        for (j in i + 1 until boxes.size) {
                            val (a, b) = boxes[i] to boxes[j]
                            val overlapX = a.first.first <= b.first.last && b.first.first <= a.first.last
                            val overlapY = a.second.first <= b.second.last && b.second.first <= a.second.last
                            (overlapX && overlapY) shouldBe false
                        }
                    }
                }
            }
        }

        Given("a spanning key in the first row") {
            val plan = plan(listOf(key(rowSpan = 3)) + List(6) { key() })

            When("the following rows are filled") {
                Then("they start after the reserved x interval") {
                    // 跨行键占住首行的第一列，下面两行都从它右侧开始
                    plan.placements[3].row shouldBe 1
                    plan.placements[3].x shouldBe 99
                    plan.placements[5].row shouldBe 2
                    plan.placements[5].x shouldBe 99
                }
            }
        }

        Given("a row span that runs past the last row") {
            val plan = plan(List(3) { key() } + key(rowSpan = 4))

            When("the height is computed") {
                Then("it is clamped to the last row") {
                    val heights = List(plan.rowCount) { 50 }
                    spanHeight(1, 3, heights) shouldBe 50
                    spanHeight(0, 2, heights) shouldBe 100
                }
            }
        }

        Given("non-clickable keys") {
            val plan = plan(listOf(key(clickable = false, rowSpan = 2)) + List(6) { key() })

            When("they are laid out") {
                Then("they take space but neither count as a column nor span rows") {
                    plan.placements[0].clickable shouldBe false
                    plan.placements[0].rowSpan shouldBe 1
                    plan.placements[1].column shouldBe 0
                }
            }
        }

        Given("a per-row column limit") {
            val plan = plan(List(4) { key() }, maxColumns = 2)

            When("the limit is reached") {
                Then("the row wraps even though the width would still fit") {
                    plan.placements.map { it.row } shouldBe listOf(0, 0, 1, 1)
                }
            }
        }
    })
