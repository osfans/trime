/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.keyboard

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class KeyContentLayoutTest :
    StringSpec({
        val measure = { text: String, size: Float -> KeyContentLayout.Metrics(text.codePointCount(0, text.length) * size, -size * 0.8f, size * 0.2f) }

        "ordinary short key captions retain their requested size" {
            val area = KeyContentLayout.Box(4f, 4f, 100f, 80f)
            val text = KeyContentLayout.text("ABC", 24f, 12f, area, 2, measure)
            text.lines shouldBe listOf("ABC")
            text.size shouldBe 24f
        }

        "narrow symbol keys reserve disjoint top main and bottom bands" {
            val bands = KeyContentLayout.bands(KeyContentLayout.Box(4f, 4f, 68f, 96f), true, true, 28f, 2f)
            val top = checkNotNull(bands.top)
            val bottom = checkNotNull(bands.bottom)
            (top.bottom < bands.main.top) shouldBe true
            (bands.main.bottom < bottom.top) shouldBe true
            (bands.main.height >= 46f) shouldBe true
            listOf(top, bands.main, bottom).forEach { area ->
                val text = KeyContentLayout.text("返回键盘长标签", 56f, 16f, area, 2, measure)
                (text.width <= area.width && text.height <= area.height) shouldBe true
            }
        }

        "long labels use at most two lines then ellipsis instead of shrinking without a floor" {
            val text = KeyContentLayout.text("abcdefghijk", 32f, 12f, KeyContentLayout.Box(0f, 0f, 36f, 30f), 2, measure)
            text.size shouldBe 12f
            text.lines shouldBe listOf("abc", "de…")
            (text.width <= 36f && text.height <= 30f) shouldBe true
        }

        "large third party offsets cannot move text or icons out of their band" {
            val area = KeyContentLayout.Box(4f, 5f, 68f, 31f)
            KeyContentLayout.place(area, 30f, 20f, 999f, -999f) shouldBe KeyContentLayout.Box(38f, 5f, 68f, 25f)
            KeyContentLayout.place(area, 100f, 100f, -999f, 999f) shouldBe area
        }

        "line and ellipsis boundaries retain combining nonBMP emoji join and flag sequences" {
            listOf("a\u0301", "\uD840\uDC00", "\uD83D\uDC69\u200D\uD83D\uDCBB", "\uD83D\uDC4D\uD83C\uDFFD", "\uD83C\uDDE8\uD83C\uDDF3", "1\uFE0F\u20E3").forEach { cluster ->
                KeyContentLayout.boundaries(cluster + "X") shouldBe listOf(0, cluster.length, cluster.length + 1)
                val metric = { value: String, size: Float -> KeyContentLayout.Metrics((KeyContentLayout.boundaries(value).size - 1) * size, -size, 0f) }
                val text = KeyContentLayout.text(cluster + "XYZ", 12f, 12f, KeyContentLayout.Box(0f, 0f, 24f, 12f), 1, metric)
                text.lines shouldBe listOf(cluster + "…")
            }
        }

        "font ink overhang and explicit newlines fit within the finite area" {
            val ink = { text: String, size: Float -> KeyContentLayout.Metrics(text.length * size + 4f, -size, size * 0.5f) }
            val text = KeyContentLayout.text("AAAA\nBBBB\nCCCC", 20f, 12f, KeyContentLayout.Box(0f, 0f, 52f, 40f), 2, ink)
            text.lines shouldBe listOf("AAAA", "BBB…")
            (text.width <= 52f && text.height <= 40f) shouldBe true
        }
    })
