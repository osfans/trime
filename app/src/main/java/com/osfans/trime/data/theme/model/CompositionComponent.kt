// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.theme.model

data class CompositionComponent(
    val start: String,
    val move: String,
    val end: String,
    val composition: String,
    val letterSpacing: Int,
    val label: String,
    val candidate: String,
    val comment: String,
    val sep: String,
    val align: String,
    val whenStr: String,
    val click: String,
)
