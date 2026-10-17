// SPDX-FileCopyrightText: 2015 - 2025 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.core.data.fixture

import android.net.Uri

class FixtureSafQueryException(
    val uri: Uri,
    val relativePath: String,
) : RuntimeException("SAF query returned null for '$relativePath' ($uri)")
