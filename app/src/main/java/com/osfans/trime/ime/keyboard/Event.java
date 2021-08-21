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

package com.osfans.trime.ime.keyboard;

import android.text.TextUtils;
import android.view.KeyEvent;
import androidx.annotation.NonNull;
import com.osfans.trime.Rime;
import com.osfans.trime.setup.Config;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** {@link Key 按鍵}的各種事件（單擊、長按、滑動等） */
public class Event {
  // private String TAG = "Event";
  private final Keyboard mKeyboard;
  private int code;
  private int mask = 0;
  private String text;
  private String label;
  private String preview;
  private List<?> states;
  private String command;
  private String option;
  private String select;
  private String toggle;
  private String commit;

  private String shiftLock;
  private boolean functional;
  private boolean repeatable;
  private boolean sticky;

  // {send|key}
  private static final Pattern sendPattern = Pattern.compile("\\{[^\\{\\}]+\\}");
  private static final Pattern labelPattern = Pattern.compile("\\{[^\\{\\}]+?\\}");

  public Event(Keyboard keyboard, @NonNull String s) {
    mKeyboard = keyboard;
    if (sendPattern.matcher(s).matches()) {
      label = s.substring(1, s.length() - 1);
      int[] sends = parseSend(label); // send
      code = sends[0];
      mask = sends[1];
      if (code >= 0) return;
      s = label; // key
      label = null;
    }
    if (Key.presetKeys.containsKey(s)) {
      Map<?, ?> m = Key.presetKeys.get(s);
      command = Config.getString(m, "command");
      option = Config.getString(m, "option");
      select = Config.getString(m, "select");
      toggle = Config.getString(m, "toggle");
      label = Config.getString(m, "label");
      preview = Config.getString(m, "preview");
      shiftLock = Config.getString(m, "shift_lock");
      commit = Config.getString(m, "commit");
      String send = Config.getString(m, "send");
      if (TextUtils.isEmpty(send) && !TextUtils.isEmpty(command))
        send = "function"; // command默認發function
      int[] sends = parseSend(send);
      code = sends[0];
      mask = sends[1];
      parseLabel();
      text = Config.getString(m, "text");
      if (code < 0 && TextUtils.isEmpty(text)) text = s;
      if (m.containsKey("states")) states = (List<?>) m.get("states");
      sticky = Config.getBoolean(m, "sticky", false);
      repeatable = Config.getBoolean(m, "repeatable", false);
      functional = Config.getBoolean(m, "functional", true);
    } else if ((code = getClickCode(s)) >= 0) {
      parseLabel();
    } else {
      code = 0;
      text = s;
      label = labelPattern.matcher(s).replaceAll("");
    }
  }

  public Event(String s) {
    this(null, s);
  }

  public int getCode() {
    return code;
  }

  public int getMask() {
    return mask;
  }

  public String getCommand() {
    return command;
  }

  public String getOption() {
    return option;
  }

  public String getSelect() {
    return select;
  }

  public boolean isFunctional() {
    return functional;
  }

  public boolean isRepeatable() {
    return repeatable;
  }

  public boolean isSticky() {
    return sticky;
  }

  public String getShiftLock() {
    return shiftLock;
  }

  @NonNull
  public static int[] parseSend(String s) {
    int[] sends = new int[2];
    if (TextUtils.isEmpty(s)) return sends;
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

  @NonNull
  private String adjustCase(String s) {
    if (TextUtils.isEmpty(s)) return "";
    if (s.length() == 1 && mKeyboard != null && mKeyboard.isShifted())
      s = s.toUpperCase(Locale.getDefault());
    else if (s.length() == 1
        && mKeyboard != null
        && !Rime.isAsciiMode()
        && mKeyboard.isLabelUppercase()) s = s.toUpperCase(Locale.getDefault());
    return s;
  }

  public String getLabel() {
    if (!TextUtils.isEmpty(toggle)) return (String) states.get(Rime.getOption(toggle) ? 1 : 0);
    return adjustCase(label);
  }

  public String getText() {
    String s = "";
    if (!TextUtils.isEmpty(text)) s = text;
    else if (mKeyboard != null
        && mKeyboard.isShifted()
        && mask == 0
        && code >= KeyEvent.KEYCODE_A
        && code <= KeyEvent.KEYCODE_Z) s = label;
    return adjustCase(s);
  }

  public String getPreviewText() {
    if (!TextUtils.isEmpty(preview)) return preview;
    return getLabel();
  }

  public String getToggle() {
    if (!TextUtils.isEmpty(toggle)) return toggle;
    return "ascii_mode";
  }

  public String getCommit() {
    return commit;
  }

  private void parseLabel() {
    if (!TextUtils.isEmpty(label)) return;
    int c = code;
    if (c == KeyEvent.KEYCODE_SPACE) {
      label = Rime.getSchemaName();
    } else {
      if (c > 0) label = getDisplayLabel(c);
    }
  }

  public static String getDisplayLabel(int keyCode) {
    String s = "";
    if (keyCode < Key.getSymbolStart()) { // 字母數字
      if (Key.getKcm().isPrintingKey(keyCode)) {
        char c = Key.getKcm().getDisplayLabel(keyCode);
        if (Character.isUpperCase(c)) c = Character.toLowerCase(c);
        s = String.valueOf(c);
      } else {
        s = Key.androidKeys.get(keyCode);
      }
    } else if (keyCode < Key.androidKeys.size()) { // 可見符號
      keyCode -= Key.getSymbolStart();
      s = Key.getSymbols().substring(keyCode, keyCode + 1);
    }
    return s;
  }

  public static int getClickCode(String s) {
    int keyCode = -1;
    if (TextUtils.isEmpty(s)) { // 空鍵
      keyCode = 0;
    } else if (Key.androidKeys.contains(s)) { // 字母數字
      keyCode = Key.androidKeys.indexOf(s);
    } else if (Key.getSymbols().contains(s)) { // 可見符號
      keyCode = Key.getSymbolStart() + Key.getSymbols().indexOf(s);
    } else if (symbolAliases.containsKey(s)) {
      keyCode = symbolAliases.get(s);
    }
    return keyCode;
  }

  private static int getRimeCode(int code) {
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

  private static final Map<String, Integer> masks =
      new HashMap<String, Integer>() {
        {
          put("Shift", KeyEvent.META_SHIFT_ON);
          put("Control", KeyEvent.META_CTRL_ON);
          put("Alt", KeyEvent.META_ALT_ON);
        }
      };

  private static final Map<String, Integer> symbolAliases =
      new HashMap<String, Integer>() {
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
