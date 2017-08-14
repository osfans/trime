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

import android.graphics.drawable.Drawable;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import java.util.List;
import java.util.Map;

/** {@link Keyboard 鍵盤}中的各個按鍵，包含單擊、長按、滑動等多種{@link Event 事件} */
public class Key {
  private String TAG = "Key";
  private Keyboard mKeyboard;
  private Event ascii;
  private Event composing;
  private Event has_menu;
  private Event paging;
  private boolean send_bindings = true;
  private static final int CLICK = 0;
  public static final int LONG_CLICK = 1;
  public static final int SWIPE_LEFT = 2;
  public static final int SWIPE_RIGHT = 3;
  public static final int SWIPE_UP = 4;
  public static final int SWIPE_DOWN = 5;
  public static final int COMBO = 6;
  private static final int EVENT_NUM = 7;
  public Event[] events = new Event[EVENT_NUM];

  public int width, height, gap, edgeFlags;
  public int row, column;
  private String label;
  public String hint;
  private Drawable key_back_color;
  private Drawable hilited_key_back_color;
  private Integer key_text_color;
  private Integer key_symbol_color;
  private Integer hilited_key_text_color;
  private Integer hilited_key_symbol_color;
  public Integer key_text_size, symbol_text_size;
  public Float round_corner;
  public int key_text_offset_x,
      key_text_offset_y,
      key_symbol_offset_x,
      key_symbol_offset_y,
      key_hint_offset_x,
      key_hint_offset_y;
  public int x, y;
  public boolean pressed, on;

  public String popupCharacters;
  public int popupResId;

  public static List<String> androidKeys;
  public static Map<String, Map> presetKeys;
  public static int symbolStart;
  public static String symbols;
  public static KeyCharacterMap kcm = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD);

  /**
   * Create an empty key with no attributes.
   *
   * @param parent 按鍵所在的{@link Keyboard 鍵盤}
   */
  public Key(Keyboard parent) {
    mKeyboard = parent;
  }

  /**
   * Create an empty key with no attributes.
   *
   * @param parent 按鍵所在的{@link Keyboard 鍵盤}
   * @param mk 從YAML中解析得到的Map
   */
  public Key(Keyboard parent, Map<String, Object> mk) {
    this(parent);
    String s;
    for (int i = 0; i < EVENT_NUM; i++) {
      String[] eventTypes =
          new String[] {
            "click", "long_click", "swipe_left", "swipe_right", "swipe_up", "swipe_down", "combo"
          };
      String eventType = eventTypes[i];
      s = Config.getString(mk, eventType);
      if (s.length() > 0) events[i] = new Event(mKeyboard, s);
    }
    s = Config.getString(mk, "composing");
    if (s.length() > 0) {
      composing = new Event(mKeyboard, s);
    }
    s = Config.getString(mk, "has_menu");
    if (s.length() > 0) {
      has_menu = new Event(mKeyboard, s);
    }
    s = Config.getString(mk, "paging");
    if (s.length() > 0) {
      paging = new Event(mKeyboard, s);
    }
    if (composing != null || has_menu != null || paging != null) mKeyboard.mComposingKeys.add(this);
    s = Config.getString(mk, "ascii");
    if (!Function.isEmpty(s)) ascii = new Event(mKeyboard, s);
    label = Config.getString(mk, "label");
    hint = Config.getString(mk, "hint");
    if (mk.containsKey("send_bindings")) send_bindings = (Boolean) mk.get("send_bindings");
    else if (composing == null && has_menu == null && paging == null) send_bindings = false;
    if (isShift()) mKeyboard.mShiftKey = this;
    key_text_size = Config.getPixel(mk, "key_text_size");
    symbol_text_size = Config.getPixel(mk, "symbol_text_size");
    key_text_color = Config.getColor(mk, "key_text_color");
    hilited_key_text_color = Config.getColor(mk, "hilited_key_text_color");
    key_back_color = Config.getColorDrawable(mk, "key_back_color");
    hilited_key_back_color = Config.getColorDrawable(mk, "hilited_key_back_color");
    key_symbol_color = Config.getColor(mk, "key_symbol_color");
    hilited_key_symbol_color = Config.getColor(mk, "hilited_key_symbol_color");
    round_corner = Config.getFloat(mk, "round_corner");
  }

  public static final int[] KEY_STATE_NORMAL_ON = {
    android.R.attr.state_checkable, android.R.attr.state_checked
  };

  public static final int[] KEY_STATE_PRESSED_ON = {
    android.R.attr.state_pressed, android.R.attr.state_checkable, android.R.attr.state_checked
  };

  public static final int[] KEY_STATE_NORMAL_OFF = {android.R.attr.state_checkable};

  public static final int[] KEY_STATE_PRESSED_OFF = {
    android.R.attr.state_pressed, android.R.attr.state_checkable
  };

  public static final int[] KEY_STATE_NORMAL = {};

  public static final int[] KEY_STATE_PRESSED = {android.R.attr.state_pressed};

  public static final int[][] KEY_STATES =
      new int[][] {
        KEY_STATE_PRESSED_ON,
        KEY_STATE_PRESSED_OFF,
        KEY_STATE_NORMAL_ON,
        KEY_STATE_NORMAL_OFF,
        KEY_STATE_PRESSED,
        KEY_STATE_NORMAL
      };

  private boolean isNormal(int[] drawableState) {
    return (drawableState == KEY_STATE_NORMAL
        || drawableState == KEY_STATE_NORMAL_ON
        || drawableState == KEY_STATE_NORMAL_OFF);
  }

  public Drawable getBackColorForState(int[] drawableState) {
    if (isNormal(drawableState)) return key_back_color;
    else return hilited_key_back_color;
  }

  public Integer getTextColorForState(int[] drawableState) {
    if (isNormal(drawableState)) return key_text_color;
    else return hilited_key_text_color;
  }

  public Integer getSymbolColorForState(int[] drawableState) {
    if (isNormal(drawableState)) return key_symbol_color;
    else return hilited_key_symbol_color;
  }

  /**
   * Informs the key that it has been pressed, in case it needs to change its appearance or state.
   *
   * @see #onReleased(boolean)
   */
  public void onPressed() {
    pressed = !pressed;
  }

  /**
   * Changes the pressed state of the key. If it is a sticky key, it will also change the toggled
   * state of the key if the finger was release inside.
   *
   * @param inside whether the finger was released inside the key
   * @see #onPressed()
   */
  public void onReleased(boolean inside) {
    pressed = !pressed;
    if (getClick().sticky) on = !on;
  }

  /**
   * Detects if a point falls inside this key.
   *
   * @param x the x-coordinate of the point
   * @param y the y-coordinate of the point
   * @return whether or not the point falls inside the key. If the key is attached to an edge, it
   *     will assume that all points between the key and the edge are considered to be inside the
   *     key.
   */
  public boolean isInside(int x, int y) {
    boolean leftEdge = (edgeFlags & Keyboard.EDGE_LEFT) > 0;
    boolean rightEdge = (edgeFlags & Keyboard.EDGE_RIGHT) > 0;
    boolean topEdge = (edgeFlags & Keyboard.EDGE_TOP) > 0;
    boolean bottomEdge = (edgeFlags & Keyboard.EDGE_BOTTOM) > 0;
    if ((x >= this.x || (leftEdge && x <= this.x + this.width))
        && (x < this.x + this.width || (rightEdge && x >= this.x))
        && (y >= this.y || (topEdge && y <= this.y + this.height))
        && (y < this.y + this.height || (bottomEdge && y >= this.y))) {
      return true;
    } else {
      return false;
    }
  }

  /**
   * Returns the square of the distance between the center of the key and the given point.
   *
   * @param x the x-coordinate of the point
   * @param y the y-coordinate of the point
   * @return the square of the distance of the point from the center of the key
   */
  public int squaredDistanceFrom(int x, int y) {
    int xDist = this.x + width / 2 - x;
    int yDist = this.y + height / 2 - y;
    return xDist * xDist + yDist * yDist;
  }

  /**
   * Returns the drawable state for the key, based on the current state and type of the key.
   *
   * @return the drawable state of the key.
   * @see android.graphics.drawable.StateListDrawable#setState(int[])
   */
  public int[] getCurrentDrawableState() {
    int[] states = KEY_STATE_NORMAL;
    boolean isShifted = isShift() && mKeyboard.isShifted(); //臨時大寫
    if (isShifted || on) {
      if (pressed) {
        states = KEY_STATE_PRESSED_ON;
      } else {
        states = KEY_STATE_NORMAL_ON;
      }
    } else {
      if (getClick().sticky || getClick().functional) {
        if (pressed) {
          states = KEY_STATE_PRESSED_OFF;
        } else {
          states = KEY_STATE_NORMAL_OFF;
        }
      } else {
        if (pressed) {
          states = KEY_STATE_PRESSED;
        }
      }
    }
    return states;
  }

  public boolean isShift() {
    int c = getCode();
    return (c == KeyEvent.KEYCODE_SHIFT_LEFT || c == KeyEvent.KEYCODE_SHIFT_RIGHT);
  }

  public boolean isShiftLock() {
    switch (getClick().shift_lock) {
      case "long":
        return false;
      case "click":
        return true;
    }
    return !Rime.isAsciiMode();
  }

  public boolean sendBindings(int type) {
    Event e = null;
    if (type > 0 && type <= EVENT_NUM) e = events[type];
    if (e != null) return true;
    if (ascii != null && Rime.isAsciiMode()) return false;
    if (send_bindings) {
      if (paging != null && Rime.isPaging()) return true;
      if (has_menu != null && Rime.hasMenu()) return true;
      if (composing != null && Rime.isComposing()) return true;
    }
    return false;
  }

  private Event getEvent() {
    if (ascii != null && Rime.isAsciiMode()) return ascii;
    if (paging != null && Rime.isPaging()) return paging;
    if (has_menu != null && Rime.hasMenu()) return has_menu;
    if (composing != null && Rime.isComposing()) return composing;
    return getClick();
  }

  public Event getClick() {
    return events[CLICK];
  }

  public Event getLongClick() {
    return events[LONG_CLICK];
  }

  public Event getEvent(int i) {
    Event e = null;
    if (i > 0 && i <= EVENT_NUM) e = events[i];
    if (e != null) return e;
    if (ascii != null && Rime.isAsciiMode()) return ascii;
    if (send_bindings) {
      if (paging != null && Rime.isPaging()) return paging;
      if (has_menu != null && Rime.hasMenu()) return has_menu;
      if (composing != null && Rime.isComposing()) return composing;
    }
    return getClick();
  }

  public int getCode() {
    return getClick().code;
  }

  public int getCode(int type) {
    return getEvent(type).code;
  }

  public String getLabel() {
    Event event = getEvent();
    if (!Function.isEmpty(label) && event == getClick() && (ascii == null && !Rime.isAsciiMode()))
      return label; //中文狀態顯示標籤
    return event.getLabel();
  }

  public String getPreviewText(int type) {
    if (type == CLICK) return getEvent().getPreviewText();
    return getEvent(type).getPreviewText();
  }

  public String getSymbolLabel() {
    return getLongClick().getLabel();
  }
}
