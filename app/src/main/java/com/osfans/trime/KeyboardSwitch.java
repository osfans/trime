/*
 * Copyright 2010 Google Inc.
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
