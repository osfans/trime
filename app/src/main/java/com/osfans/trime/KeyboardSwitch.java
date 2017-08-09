/**
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

package com.osfans.trime;

import android.content.Context;

import java.util.List;

/** 管理多個{@link Keyboard 鍵盤} */
public class KeyboardSwitch {

  private final Context context;

  private Keyboard[] mKeyboards;
  private List<String> mKeyboardNames;
  private int currentId, lastId;
  private int currentDisplayWidth;

  public KeyboardSwitch(Context context) {
    this.context = context;
    currentId = -1;
    lastId = 0;
    reset();
  }

  public void reset() {
    mKeyboardNames = Config.get().getKeyboardNames();
    int n = mKeyboardNames.size();
    mKeyboards = new Keyboard[n];
    for (int i = 0; i < n; i++ ) {
      mKeyboards[i] = new Keyboard(context, mKeyboardNames.get(i));
    }
    setKeyboard(0);
  }

  public void setKeyboard(String name){
    int i;
    if (name == null || name.contentEquals(".default")) {
      i = 0;
    } else if (name.contentEquals(".prior")) { //前一個
      i = currentId - 1;
      if (i < 0) i = mKeyboards.length - 1;
    } else {
      i = mKeyboardNames.indexOf(name); //指定鍵盤
    }
    if (i < 0) i = currentId + 1; //默認下一個
    setKeyboard(i);
  }

  public void setKeyboard(int i){
    if (i < 0 || i >= mKeyboards.length) i = 0;
    lastId = currentId;
    currentId = i;
  }

  public void init(int displayWidth) {
    if ((currentId >= 0) && (displayWidth == currentDisplayWidth)) {
      return;
    }

    currentDisplayWidth = displayWidth;
    reset();
  }

  public Keyboard getCurrentKeyboard() {
    return mKeyboards[currentId];
  }
  
  public boolean getAsciiMode() {
    return getCurrentKeyboard().getAsciiMode();
  }
}
