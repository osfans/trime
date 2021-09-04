package com.osfans.trime.util;

import static android.view.KeyEvent.*;

import android.view.KeyEvent;

public class StringUitls {
  private static final String sectionDivider = ",.?!~:，。：～？！…\t\r\n\\/";

  public static int findNextSection(CharSequence str, int start) {
    if (str != null) {
      int i = Math.max(0, start);
      if (i < str.length()) {
        char c = str.charAt(i);
        boolean judge = sectionDivider.indexOf(c) < 0;
        for (; i < str.length(); i++) {
          c = str.charAt(i);
          if (sectionDivider.indexOf(c) < 0) judge = true;
          else if (judge) {
            return i;
          }
        }
      }
    }
    return 0;
  }

  public static int findPrevSection(CharSequence str, int start) {
    if (str != null) {
      int i = Math.min(start, str.length()) - 1;
      if (i >= 0) {
        char c = str.charAt(i);
        boolean judge = sectionDivider.indexOf(c) < 0;
        for (; i >= 0; i--) {
          c = str.charAt(i);
          if (sectionDivider.indexOf(c) < 0) judge = true;
          else if (judge) {
            return i;
          }
        }
      }
    }
    return 0;
  }

  public static String stringReplacer(String str, String[] rules) {
    if (str == null) return "";

    String s = str;
    for (String rule : rules) {
      s = s.replaceAll(rule, "");
      if (s.length() < 1) return "";
    }
    return s;
  }

  public static boolean stringNotMatch(String str, String[] rules) {
    if (str == null) return false;

    if (str.length() < 1) return false;

    for (String rule : rules) {
      if (str.matches(rule)) return false;
    }
    return true;
  }

  // 考虑到可能存在魔改机型的keycode有差异，而KeyEvent.keyCodeToString(keyCode)无法从keyCode获得按键字符，故重写这个从keyCode获取Char的方法。
  public static String toCharString(int keyCode) {

    switch (keyCode) {
      case KEYCODE_TAB:
        return "\t";
      case KEYCODE_SPACE:
        return " ";
      case KEYCODE_PLUS:
        return "+";
      case KEYCODE_MINUS:
        return "-";
      case KEYCODE_STAR:
        return "*";
      case KEYCODE_SLASH:
        return "/";
      case KEYCODE_EQUALS:
        return "=";
      case KEYCODE_AT:
        return "@";
      case KEYCODE_POUND:
        return "#";
      case KEYCODE_APOSTROPHE:
        return "'";
      case KEYCODE_BACKSLASH:
        return "\\";
      case KEYCODE_COMMA:
        return ",";
      case KEYCODE_PERIOD:
        return ".";
      case KEYCODE_LEFT_BRACKET:
        return "[";
      case KEYCODE_RIGHT_BRACKET:
        return "]";
      case KEYCODE_SEMICOLON:
        return ";";
      case KEYCODE_GRAVE:
        return "`";
      case KEYCODE_NUMPAD_ADD:
        return "+";
      case KEYCODE_NUMPAD_SUBTRACT:
        return "-";
      case KEYCODE_NUMPAD_MULTIPLY:
        return "*";
      case KEYCODE_NUMPAD_DIVIDE:
        return "/";
      case KEYCODE_NUMPAD_EQUALS:
        return "=";
      case KEYCODE_NUMPAD_COMMA:
        return ",";
      case KEYCODE_NUMPAD_DOT:
        return ".";
      case KEYCODE_NUMPAD_LEFT_PAREN:
        return "(";
      case KEYCODE_NUMPAD_RIGHT_PAREN:
        return ")";
    }

    int c = 0;
    if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
      c = '0' + keyCode - KeyEvent.KEYCODE_0;
    } else if (keyCode >= KeyEvent.KEYCODE_NUMPAD_0 && keyCode <= KeyEvent.KEYCODE_NUMPAD_9) {
      c = '0' + keyCode - KeyEvent.KEYCODE_NUMPAD_0;
    } else if (keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_Z) {
      c = 'a' + keyCode - KeyEvent.KEYCODE_A;
    }

    if (c > 0) return Character.toString((char) c);
    return "";
  }
}
