// SPDX-FileCopyrightText: 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.core

data class RimeResponse(
    val commit: RimeProto.Commit?,
    val context: RimeProto.Context?,
    val status: RimeProto.Status?,
)
