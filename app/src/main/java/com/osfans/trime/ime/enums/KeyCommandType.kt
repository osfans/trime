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

/*按键的特殊命令枚举（仅用于新增的liquidKeyboard）*/
enum class KeyCommandType {
    NULL, LEFT, RIGHT, EXIT, DEL_LEFT, DEL_RIGHT, UNDO, REDO, ;

    companion object {
        private val convertMap: HashMap<String, KeyCommandType> = hashMapOf()

        init {
            for (type in values()) {
                convertMap[type.toString()] = type
            }
        }

        fun fromString(code: String): KeyCommandType {
            val type = convertMap[code.uppercase(Locale.getDefault())]
            return type ?: NULL
        }
    }
}
