/*
 * Copyright 2015 osfans
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.osfans.trime;

import android.view.KeyEvent;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Locale;

/** {@link Key 按鍵}的各種事件（單擊、長按、滑動等） */
public class Event {
  private String TAG = "Event";
  private Keyboard mKeyboard;
  int code = 0, mask = 0;
  String text, label, preview;
  List<String> states;
  String command, option, select, toggle;
  public boolean functional, repeatable, sticky;

  public Event(Keyboard keyboard, String s) {
    mKeyboard = keyboard;
    if (Key.presetKeys.containsKey(s)) {
      Map m = Key.presetKeys.get(s);
      command = Function.getString(m, "command");
      option = Function.getString(m, "option");
      select = Function.getString(m, "select");
      toggle = Function.getString(m, "toggle");
      label = Function.getString(m, "label");
      preview = Function.getString(m, "preview");
      parseSend(Function.getString(m, "send"));
      parseLabel();
      text = Function.getString(m, "text");
      if (code == 0 && Function.isEmpty(text)) text = s;
      if (m.containsKey("states")) states = (List<String>)m.get("states");
      sticky = (Boolean)Key.getValue(m, "sticky", false);
      repeatable = (Boolean)Key.getValue(m, "repeatable", false);
      functional = (Boolean)Key.getValue(m, "functional", true);
    } else if (Key.androidKeys.contains(s)) {
      code = Key.androidKeys.indexOf(s);
      parseLabel();
    } else {
      text = s;
      label = s;
    }
  }

  private void parseSend(String s) {
    if (Function.isEmpty(s)) return;
    String codes;
    if (!s.contains("+")) codes = s;
    else {
      String[] ss = s.split("\\+");
      int n = ss.length;
      for (int i = 0; i < n - 1; i++) if (masks.containsKey(ss[i])) mask |= masks.get(ss[i]);
      codes = ss[n - 1];
    }
    code = Key.androidKeys.indexOf(codes);
  }

  public String adjustCase(String s) {
    if (Function.isEmpty(s)) return "";
    if (s.length() == 1 && mKeyboard != null && mKeyboard.isShifted()) s = s.toUpperCase(Locale.getDefault());
    return s;
  }

  public String getLabel() {
    if (!Function.isEmpty(toggle)) return states.get(Rime.getOption(toggle) ? 1 : 0);
    return adjustCase(label);
  }

  public String getText() {
    String s = "";
    if (!Function.isEmpty(text)) s = text;
    else if (mKeyboard != null && mKeyboard.isShifted() && mask == 0 && code >= KeyEvent.KEYCODE_A && code <= KeyEvent.KEYCODE_Z) s = label;
    return adjustCase(s);
  }

  public String getPreviewText() {
    if (!Function.isEmpty(preview)) return preview;
    return getLabel();
  }

  public String getToggle() {
    if (!Function.isEmpty(toggle)) return toggle;
    return "ascii_mode";
  }

  private void parseLabel() {
    if (!Function.isEmpty(label)) return;
    int c = code;
    if (c == KeyEvent.KEYCODE_SPACE){
      label = Rime.getSchemaName();
    } else {
      if (c > 0) label = Key.androidKeys.get(c);
    }
  }

  public int[] getRimeEvent() {
    return getRimeEvent(code, mask);
  }

  public static int getRimeCode(int code) {
    String s = Key.androidKeys.get(code);
    return Rime.get_keycode_by_name(s);
  }

  public static int[] getRimeEvent(int code, int mask) {
    int i = getRimeCode(code);
    int m = 0;
    if ((mask & KeyEvent.META_SHIFT_ON) > 0) {
      m |= Rime.get_modifier_by_name("Shift");
    }
    if ((mask & KeyEvent.META_CTRL_ON) > 0) {
      m |= Rime.get_modifier_by_name("Control");
    }
    if ((mask & KeyEvent.META_ALT_ON) > 0) {
      m |= Rime.get_modifier_by_name("Alt");
    }
    if (mask == (1<<30)) m |= Rime.get_modifier_by_name("Release");
    return new int[] {i, m};
  }

  public static Map<String,Integer> masks = new HashMap<String,Integer>() {
    {
      put("Shift", KeyEvent.META_SHIFT_ON);
      put("Control", KeyEvent.META_CTRL_ON);
      put("Alt", KeyEvent.META_ALT_ON);
    }
  };
}
