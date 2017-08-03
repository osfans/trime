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
      int[] sends = parseSend(Function.getString(m, "send"));
      code = sends[0];
      mask = sends[1];
      parseLabel();
      text = Function.getString(m, "text");
      if (code == 0 && Function.isEmpty(text)) text = s;
      if (m.containsKey("states")) states = (List<String>)m.get("states");
      sticky = (Boolean)Key.getValue(m, "sticky", false);
      repeatable = (Boolean)Key.getValue(m, "repeatable", false);
      functional = (Boolean)Key.getValue(m, "functional", true);
    } else if ((code = getClickCode(s)) > 0) {
      parseLabel();
    } else {
      code = 0;
      text = s;
      label = s;
      if (containsSend(s)) {
        if (s.contains("{}")) label = s.replace("{}", "[]");
        label = label.replaceAll("\\{.+?\\}", "");
        if (s.contains("{}")) label = label.replace("[]", "{}");
      }
    }
  }

  public static boolean containsSend(String s) {
    return (!Function.isEmpty(s)) && s.length() > 1 && s.matches(".*\\{.+\\}.*");
  }

  public static int [] parseSend(String s) {
    int[] sends = new int[2];
    if (Function.isEmpty(s)) return sends;
    String codes;
    if (!s.contains("+")) codes = s;
    else {
      String[] ss = s.split("\\+");
      int n = ss.length;
      for (int i = 0; i < n - 1; i++) if (masks.containsKey(ss[i])) sends[1] |= masks.get(ss[i]);
      codes = ss[n - 1];
    }
    sends[0] = Key.androidKeys.indexOf(codes);
    return sends;
  }

  public String adjustCase(String s) {
    if (Function.isEmpty(s)) return "";
    if (s.length() == 1 && mKeyboard != null && mKeyboard.isShifted()) s = s.toUpperCase(Locale.getDefault());
    else if (s.length() == 1 && mKeyboard != null && !Rime.isAsciiMode() && mKeyboard.isLabelUppercase()) s = s.toUpperCase(Locale.getDefault());
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
      if (c > 0) label = getDisplayLabel(c);
    }
  }

  public static String getDisplayLabel(int keyCode) {
    String s = "";
    if (keyCode < Key.symbolStart) { //字母數字
      if (Key.kcm.isPrintingKey(keyCode)) {
        char c = Key.kcm.getDisplayLabel(keyCode);
        if (Character.isUpperCase(c)) c = Character.toLowerCase(c);
        s = String.valueOf(c);
      } else {
        s = Key.androidKeys.get(keyCode);
      }
    } else if (keyCode < Key.androidKeys.size()) { //可見符號
      keyCode -= Key.symbolStart;
      s = Key.symbols.substring(keyCode, keyCode + 1);
    }
    return s;
  }

  public static int getClickCode(String s) {
    int keyCode = 0;
    if (Key.androidKeys.contains(s)) { //字母數字
      keyCode = Key.androidKeys.indexOf(s);
    } else if (Key.symbols.contains(s)) { //可見符號
      keyCode = Key.symbolStart + Key.symbols.indexOf(s);
    } else if (symbolAliases.containsKey(s)) {
      keyCode = symbolAliases.get(s);
    }
    return keyCode;
  }

  public static int getRimeCode(int code) {
    int i = 0;
    if (code >= 0 && code < Key.androidKeys.size()) {
      String s = Key.androidKeys.get(code);
      i = Rime.get_keycode_by_name(s);
    }
    return i;
  }

  public static boolean hasModifier(int mask, int modifier) {
    return (mask & modifier) > 0;
  }

  public static int[] getRimeEvent(int code, int mask) {
    int i = getRimeCode(code);
    int m = 0;
    if (hasModifier(mask, KeyEvent.META_SHIFT_ON)) m |= Rime.META_SHIFT_ON;
    if (hasModifier(mask, KeyEvent.META_CTRL_ON)) m |= Rime.META_CTRL_ON;
    if (hasModifier(mask, KeyEvent.META_ALT_ON)) m |= Rime.META_ALT_ON;
    if (mask == Rime.META_RELEASE_ON) m |= Rime.META_RELEASE_ON;
    return new int[] {i, m};
  }

  public static Map<String,Integer> masks = new HashMap<String,Integer>() {
    {
      put("Shift", KeyEvent.META_SHIFT_ON);
      put("Control", KeyEvent.META_CTRL_ON);
      put("Alt", KeyEvent.META_ALT_ON);
    }
  };

  public static Map<String,Integer> symbolAliases = new HashMap<String,Integer>() {
    {
      put("#", KeyEvent.KEYCODE_POUND);
      put("'", KeyEvent.KEYCODE_APOSTROPHE);
      put("(", KeyEvent.KEYCODE_NUMPAD_LEFT_PAREN);
      put(")", KeyEvent.KEYCODE_NUMPAD_RIGHT_PAREN);
      put("*", KeyEvent.KEYCODE_STAR);
      put("+", KeyEvent.KEYCODE_PLUS);
      put(",", KeyEvent.KEYCODE_COMMA);
      put("-", KeyEvent.KEYCODE_MINUS);
      put(".", KeyEvent.KEYCODE_PERIOD);
      put("/", KeyEvent.KEYCODE_SLASH);
      put(";", KeyEvent.KEYCODE_SEMICOLON);
      put("=", KeyEvent.KEYCODE_EQUALS);
      put("@", KeyEvent.KEYCODE_AT);
      put("\\", KeyEvent.KEYCODE_BACKSLASH);
      put("[", KeyEvent.KEYCODE_LEFT_BRACKET);
      put("`", KeyEvent.KEYCODE_GRAVE);
      put("]", KeyEvent.KEYCODE_RIGHT_BRACKET);
    }
  };
}
