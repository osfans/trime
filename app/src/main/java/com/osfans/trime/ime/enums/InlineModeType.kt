/*
 * Copyright (C) 2015-present, osfans
 * waxaca@163.com https://github.com/osfans
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.osfans.trime.ime.enums

/** 嵌入模式枚举  */
enum class InlineModeType {
    INLINE_NONE, INLINE_PREVIEW, INLINE_COMPOSITION, INLINE_INPUT;

    companion object {
        fun fromString(string: String): InlineModeType {
            return when (string) {
                "preview", "preedit", "true" -> INLINE_PREVIEW
                "composition" -> INLINE_COMPOSITION
                "input" -> INLINE_INPUT
                else -> INLINE_NONE
            }
        }
    }
}
