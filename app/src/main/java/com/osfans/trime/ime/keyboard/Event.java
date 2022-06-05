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
import com.osfans.trime.core.Rime;
import com.osfans.trime.data.Config;
import com.osfans.trime.ime.enums.Keycode;
import com.osfans.trime.util.ConfigGetter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import timber.log.Timber;

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
      int[] sends = Keycode.parseSend(label); // send
      code = sends[0];
      mask = sends[1];
      if (code > 0 || mask > 0) return;
      if (parseAction(label)) return;
      s = label; // key
      label = null;
    }
    if (Key.presetKeys.containsKey(s)) {
      // todo 把presetKeys缓存为presetKeyEvents，减少重新载入
      Map<String, ?> presetKey = Key.presetKeys.get(s);
      command = ConfigGetter.getString(presetKey, "command", "");
      option = ConfigGetter.getString(presetKey, "option", "");
      select = ConfigGetter.getString(presetKey, "select", "");
      toggle = ConfigGetter.getString(presetKey, "toggle", "");
      label = ConfigGetter.getString(presetKey, "label", "");
      preview = ConfigGetter.getString(presetKey, "preview", "");
      shiftLock = ConfigGetter.getString(presetKey, "shift_lock", "");
      commit = ConfigGetter.getString(presetKey, "commit", "");
      String send = ConfigGetter.getString(presetKey, "send", "");
      if (TextUtils.isEmpty(send) && !TextUtils.isEmpty(command))
        send = "function"; // command默認發function
      int[] sends = Keycode.parseSend(send);
      code = sends[0];
      mask = sends[1];
      parseLabel();
      text = Config.getString(presetKey, "text");
      if (code < 0 && TextUtils.isEmpty(text)) text = s;
      if (presetKey.containsKey("states")) states = (List<?>) presetKey.get("states");
      sticky = ConfigGetter.getBoolean(presetKey, "sticky", false);
      repeatable = ConfigGetter.getBoolean(presetKey, "repeatable", false);
      functional = ConfigGetter.getBoolean(presetKey, "functional", true);
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

  // 快速把字符串解析为event, 暂时只处理了comment类型 不能完全正确处理=，
  private boolean parseAction(String s) {
    boolean result = false;
    String[] strs = s.split(",");
    for (String str : strs) {
      String[] set = str.split("=", 2);
      if (set.length != 2) continue;
      if (set[0].equals("commit")) {
        commit = set[1];
        result = true;
      } else if (set[0].equals("label")) {
        label = set[1];
        result = true;
      } else if (set[0].equals("text")) {
        text = set[1];
        result = true;
      }
    }
    Timber.d("<Event> text=" + text + ", commit=" + commit + ", label=" + label + ", s=" + s);
    return result;
  }

  // TODO 进一步解耦，在Event中去除mKeyboard
  @NonNull
  private String adjustCase(String s) {
    if (TextUtils.isEmpty(s)) return "";
    if (s.length() == 1 && mKeyboard != null && mKeyboard.needUpCase())
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
        && mKeyboard.needUpCase()
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
    if (Keycode.Companion.isStdKey(keyCode)) { // 字母數字
      if (Key.getKcm().isPrintingKey(keyCode)) {
        char c = Key.getKcm().getDisplayLabel(keyCode);
        if (Character.isUpperCase(c)) c = Character.toLowerCase(c);
        s = String.valueOf(c);
      } else {
        s = Keycode.keyNameOf(keyCode);
      }
    } else if (Keycode.Companion.hasSymbolLabel(keyCode)) { // 可見符號
      s = Keycode.Companion.getSymbolLabell(Keycode.valueOf(keyCode));
    }
    return s;
  }

  public static int getClickCode(String s) {
    int keyCode = -1;
    if (TextUtils.isEmpty(s)) { // 空鍵
      keyCode = 0;
    } else if (Keycode.fromString(s) != Keycode.VoidSymbol) { // 字母數字
      keyCode = Keycode.keyCodeOf(s);
    } else if (symbolAliases.containsKey(s)) {
      keyCode = symbolAliases.get(s);
    }
    return keyCode;
  }

  public static boolean hasModifier(int mask, int modifier) {
    return (mask & modifier) > 0;
  }

  // KeyboardEvent 从软键盘的按键keycode（可能含有mask）和mask，分离出rimekeycode和mask构成的数组
  public static int[] getRimeEvent(int code, int mask) {
    int i = RimeKeycode.get().getRimeCode(code);
    int m = 0;
    if (hasModifier(mask, KeyEvent.META_SHIFT_ON)) m |= Rime.META_SHIFT_ON;
    if (hasModifier(mask, KeyEvent.META_CTRL_ON)) m |= Rime.META_CTRL_ON;
    if (hasModifier(mask, KeyEvent.META_ALT_ON)) m |= Rime.META_ALT_ON;
    if (hasModifier(mask, KeyEvent.META_SYM_ON)) m |= Rime.META_SYM_ON;
    if (hasModifier(mask, KeyEvent.META_META_ON)) m |= Rime.META_META_ON;
    if (mask == Rime.META_RELEASE_ON) m |= Rime.META_RELEASE_ON;
    Timber.d(
        "<Event> getRimeEvent()\tcode=%d, mask=%d, name=%s\toutput key=%d, meta=%d",
        code, mask, Keycode.keyNameOf(code), i, m);
    return new int[] {i, m};
  }

  public boolean isMeta() {
    int c = getCode();
    return (c == KeyEvent.KEYCODE_META_LEFT || c == KeyEvent.KEYCODE_META_RIGHT);
  }

  public boolean isAlt() {
    int c = getCode();
    return (c == KeyEvent.KEYCODE_ALT_LEFT || c == KeyEvent.KEYCODE_ALT_RIGHT);
  }

  private static final Map<String, Integer> masks =
      new HashMap<String, Integer>() {
        {
          put("Shift", KeyEvent.META_SHIFT_ON);
          put("Control", KeyEvent.META_CTRL_ON);
          put("Alt", KeyEvent.META_ALT_ON);
          put("Meta", KeyEvent.META_META_ON);
          put("SYM", KeyEvent.META_SYM_ON);
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
