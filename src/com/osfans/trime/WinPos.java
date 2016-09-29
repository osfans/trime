/*
 * Copyright 2016 osfans
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.osfans.trime;

public enum WinPos {
  LEFT,
  LEFT_UP,
  RIGHT,
  RIGHT_UP,
  FIXED,
  BOTTOM_LEFT,
  BOTTOM_RIGHT,
  TOP_LEFT,
  TOP_RIGHT;

  public static WinPos fromString(String text) {
    if (text != null) {
      for (WinPos b : WinPos.values()) {
        if (text.equalsIgnoreCase(b.toString())) {
          return b;
        }
      }
    }
    return FIXED;
  }
}

