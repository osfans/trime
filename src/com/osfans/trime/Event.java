/*
 * Copyright (C) 2008-2009 Google Inc.
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

public class Event {
  private String TAG = "Event";
  private Keyboard mKeyboard;
  int code, mask;
  String text, label, preview;
  List<String> states;
  String command, option, select, toggle;
  public boolean functional, repeatable, sticky;

  public Event(Keyboard keyboard, String s) {
    mKeyboard = keyboard;
    if (Key.presetKeys.containsKey(s)) {
      Map m = Key.presetKeys.get(s);
      text = Key.getString(m, "text");
      command = Key.getString(m, "command");
      option = Key.getString(m, "option");
      select = Key.getString(m, "select");
      toggle = Key.getString(m, "toggle");
      label = Key.getString(m, "label");
      preview = Key.getString(m, "preview");
      parseSend(Key.getString(m, "send"));
      parseLabel();
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
    if (s.isEmpty()) return;
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
    if (s == null) return "";
    if (s.length() == 1 && mKeyboard.isShifted()) s = s.toUpperCase();
    return s;
  }

  public String getLabel() {
    if (toggle != null && !toggle.isEmpty()) return states.get(Rime.getOption(toggle) ? 1 : 0);
    return adjustCase(label);
  }

  public String getText() {
    String s = "";
    if (text != null && !text.isEmpty()) s = text;
    else if (mKeyboard.isShifted() && mask == 0 && code >= KeyEvent.KEYCODE_A && code <= KeyEvent.KEYCODE_Z) s = label;
    return adjustCase(s);
  }

  public String getPreviewText() {
    if (preview != null && !preview.isEmpty()) return preview;
    return getLabel();
  }

  public String getToggle() {
    if (toggle != null && !toggle.isEmpty()) return toggle;
    return "ascii_mode";
  }

  private void parseLabel() {
    if (label != null && !label.isEmpty()) return;
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
