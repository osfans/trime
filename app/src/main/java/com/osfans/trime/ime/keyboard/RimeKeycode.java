package com.osfans.trime.ime.keyboard;

import com.osfans.trime.core.Rime;
import com.osfans.trime.ime.enums.Keycode;
import java.util.HashMap;
import java.util.Map;
import timber.log.Timber;

public class RimeKeycode {
  private Map<Integer, Integer> rimeKeycode;
  private static RimeKeycode self;

  private RimeKeycode() {
    rimeKeycode = new HashMap<>();
  }

  public static RimeKeycode get() {
    if (self == null) self = new RimeKeycode();
    return self;
  }

  public int getRimeCode(int code) {
    if (!rimeKeycode.containsKey(code)) rimeKeycode.put(code, rimeCode(code));
    return rimeKeycode.get(code);
  }

  // TODO 把软键盘预设android_keys的keycode(index)->keyname(string)—>rimeKeycode的过程改为直接返回int
  // https://github.com/rime/librime/blob/99e269c8eb251deddbad9b0d2c4d965b228f8006/src/rime/key_table.cc
  public static int rimeCode(int code) {
    int i = 0;
    if (code >= 0 && code < Keycode.values().length) {
      String s = Keycode.keyNameOf(code);
      i = Rime.get_keycode_by_name(s);
    } else {
      Timber.w("code=%s", code);
    }
    return i;
  }
}
