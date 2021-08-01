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

package com.osfans.trime;

import android.content.Context;
import java.util.List;

/** 管理多個{@link Keyboard 鍵盤} */
public class KeyboardSwitch {

  private final Context context;

  private Keyboard[] mKeyboards;
  private List<String> mKeyboardNames;
  private int currentId, lastId, lastLockId;
  private int currentDisplayWidth;

  public KeyboardSwitch(Context context) {
    this.context = context;
    currentId = -1;
    lastId = 0;
    lastLockId = 0;
    reset(context);
  }

  public void reset(Context context) {
    mKeyboardNames = Config.get(context).getKeyboardNames();
    int n = mKeyboardNames.size();
    mKeyboards = new Keyboard[n];
    for (int i = 0; i < n; i++) {
      mKeyboards[i] = new Keyboard(context, mKeyboardNames.get(i));
    }
    setKeyboard(0);
  }

  public void setKeyboard(String name) {
    int i = 0;
    if (isValidId(currentId)) i = currentId;
    if (Function.isEmpty(name)) {
      if (!mKeyboards[i].isLock()) i = lastLockId; //不記憶鍵盤時使用默認鍵盤
    } else if (name.contentEquals(".default")) {
      i = 0;
    } else if (name.contentEquals(".prior")) { //前一個
      i = currentId - 1;
    } else if (name.contentEquals(".next")) { //下一個
      i = currentId + 1;
    } else if (name.contentEquals(".last")) { //最近一個
      i = lastId;
    } else if (name.contentEquals(".last_lock")) { //最近一個Lock鍵盤
      i = lastLockId;
    } else if (name.contentEquals(".ascii")) { //英文鍵盤
      String asciiKeyboard = mKeyboards[i].getAsciiKeyboard();
      if (!Function.isEmpty(asciiKeyboard)) i = mKeyboardNames.indexOf(asciiKeyboard);
    } else {
      i = mKeyboardNames.indexOf(name); //指定鍵盤
    }
    setKeyboard(i);
  }

  private boolean isValidId(int i) {
    return i >= 0 && i < mKeyboards.length;
  }

  private void setKeyboard(int i) {
    if (!isValidId(i)) i = 0;
    lastId = currentId;
    if (isValidId(lastId)) {
      if (mKeyboards[lastId].isLock()) lastLockId = lastId;
    }
    currentId = i;
  }

  public void init(int displayWidth) {
    if ((currentId >= 0) && (displayWidth == currentDisplayWidth)) {
      return;
    }

    currentDisplayWidth = displayWidth;
    reset(context);
  }

  private boolean land;
  public void init(int displayWidth,boolean isLand) {
    if ((currentId >= 0) && (displayWidth == currentDisplayWidth)) {
      return;
    }
    land = isLand;
    currentDisplayWidth = displayWidth;
    reset(context);
  }

  public Keyboard getCurrentKeyboard() {
    return mKeyboards[currentId];
  }

  public boolean getAsciiMode() {
    return getCurrentKeyboard().getAsciiMode();
  }
}
