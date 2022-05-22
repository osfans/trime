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

import java.util.HashMap
import java.util.Locale

/**
 * 悬浮窗。候选栏文字，按键文字，按键气泡文字的定位方式
 */
enum class PositionType {
    // 跟随光标
    LEFT, LEFT_UP, RIGHT, RIGHT_UP,
    // 固定位置
    DRAG, FIXED,
    // 相对位置
    BOTTOM_LEFT, BOTTOM_RIGHT, TOP_LEFT, TOP_RIGHT,
    // 暂未实现的相对位置
    TOP_CENTER, BOTTOM_CENTER, CENTER;

    companion object {
        private val convertMap: HashMap<String, PositionType> = hashMapOf()

        init {
            for (type in values()) {
                convertMap[type.toString()] = type
            }
        }

        @JvmStatic
        fun fromString(code: String): PositionType {
            val type = convertMap[code.uppercase(Locale.getDefault())]
            return type ?: FIXED
        }

        @JvmStatic
        fun fromString(code: String, default: PositionType): PositionType {
            val type = convertMap[code.uppercase(Locale.getDefault())]
            return type ?: default
        }

        /**
         * 解析候选栏文字，按键文字，按键气泡文字的定位方式
         */
        @JvmStatic
        fun parseKeyPosition(code: String): PositionType {
            val type = convertMap[code.uppercase(Locale.getDefault())]
            if (type != null)
                if (type > FIXED)
                    return type
            return type ?: CENTER
        }
    }
}
