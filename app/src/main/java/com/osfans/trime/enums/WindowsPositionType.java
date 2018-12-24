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

package com.osfans.trime.enums;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public enum WindowsPositionType {
  LEFT,
  LEFT_UP,
  RIGHT,
  RIGHT_UP,
  DRAG,
  FIXED,
  BOTTOM_LEFT,
  BOTTOM_RIGHT,
  TOP_LEFT,
  TOP_RIGHT;

  private static final Map<String, WindowsPositionType> convertMap =
      new HashMap<String, WindowsPositionType>(WindowsPositionType.values().length);

  static {
    for (WindowsPositionType type : WindowsPositionType.values()) {
      convertMap.put(type.toString(), type);
    }
  }

  public static WindowsPositionType fromString(String code) {
    WindowsPositionType type = convertMap.get(code.toUpperCase(Locale.getDefault()));
    if (null == type) {
      return FIXED;
    } else {
      return type;
    }
  }
}
