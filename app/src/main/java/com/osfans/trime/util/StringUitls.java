package com.osfans.trime.util;

public class StringUitls {
  private static String sectionDivider = ",.?!~:，。：～？！…\t\r\n\\/";

  public static int findNextSection(CharSequence str, int start) {
    if (str != null) {
      int i = Math.max(0, start);
      if (i < str.length()) {
        char c = str.charAt(i);
        boolean judge = sectionDivider.indexOf(c) < 0 ? true : false;
        for (; i < str.length(); i++) {
          c = str.charAt(i);
          if (sectionDivider.indexOf(c) < 0) judge = true;
          else if (judge) {
            return i;
          }
        }
      }
    }
    return str.length();
  }

  public static int findPrevSection(CharSequence str, int start) {
    if (str != null) {
      int i = Math.min(start, str.length()) - 1;
      if (i >= 0) {
        char c = str.charAt(i);
        boolean judge = sectionDivider.indexOf(c) < 0 ? true : false;
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
}
