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

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import com.osfans.trime.Rime;
import com.osfans.trime.ime.enums.KeyEventType;
import com.osfans.trime.setup.Config;
import java.util.List;
import java.util.Map;

/** {@link Keyboard 鍵盤}中的各個按鍵，包含單擊、長按、滑動等多種{@link Event 事件} */
public class Key {
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
  public static List<String> androidKeys;
  public static Map<String, Map<?, ?>> presetKeys;
  private static final int EVENT_NUM = KeyEventType.values().length;
  public Event[] events = new Event[EVENT_NUM];
  public int edgeFlags;
  private static int symbolStart;
  private static String symbols;
  private static final KeyCharacterMap kcm = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD);
  private final Keyboard mKeyboard;
  private Event ascii;
  private Event composing;
  private Event has_menu;
  private Event paging;
  private boolean send_bindings = true;
  private int width;
  private int height;
  private int gap;
  private int row;
  private int column;
  private String label;
  private String hint;
  private Drawable key_back_color;
  private Drawable hilited_key_back_color;
  private Integer key_text_color;
  private Integer key_symbol_color;
  private Integer hilited_key_text_color;
  private Integer hilited_key_symbol_color;
  private Integer key_text_size;
  private Integer symbol_text_size;
  private Float round_corner;
  private int key_text_offset_x;
  private int key_text_offset_y;
  private int key_symbol_offset_x;
  private int key_symbol_offset_y;
  private int key_hint_offset_x;
  private int key_hint_offset_y;
  private int key_press_offset_x;
  private int key_press_offset_y;
  private int x;
  private int y;
  private boolean pressed;
  private boolean on;
  private String popupCharacters;
  private int popupResId;

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
  public Key(Context context, Keyboard parent, Map<String, Object> mk) {
    this(parent);
    String s;
    Config config = Config.get(context);
    for (int i = 0; i < EVENT_NUM; i++) {
      String[] eventTypes =
          new String[] {
            "click", "long_click", "swipe_left", "swipe_right", "swipe_up", "swipe_down", "combo"
          };
      String eventType = eventTypes[i];
      s = Config.getString(mk, eventType);
      if (!TextUtils.isEmpty(s)) events[i] = new Event(mKeyboard, s);
      else if (i == KeyEventType.CLICK.ordinal()) events[i] = new Event(mKeyboard, "");
    }
    s = Config.getString(mk, "composing");
    if (!TextUtils.isEmpty(s)) composing = new Event(mKeyboard, s);
    s = Config.getString(mk, "has_menu");
    if (!TextUtils.isEmpty(s)) has_menu = new Event(mKeyboard, s);
    s = Config.getString(mk, "paging");
    if (!TextUtils.isEmpty(s)) paging = new Event(mKeyboard, s);
    if (composing != null || has_menu != null || paging != null)
      mKeyboard.getmComposingKeys().add(this);
    s = Config.getString(mk, "ascii");
    if (!TextUtils.isEmpty(s)) ascii = new Event(mKeyboard, s);
    label = Config.getString(mk, "label");
    hint = Config.getString(mk, "hint");
    if (mk.containsKey("send_bindings")) send_bindings = Config.getBoolean(mk, "send_bindings");
    else if (composing == null && has_menu == null && paging == null) send_bindings = false;
    if (isShift()) mKeyboard.setmShiftKey(this);
    key_text_size = Config.getPixel(mk, "key_text_size");
    symbol_text_size = Config.getPixel(mk, "symbol_text_size");
    key_text_color = Config.getColor(context, mk, "key_text_color");
    hilited_key_text_color = Config.getColor(context, mk, "hilited_key_text_color");
    key_back_color = config.getDrawable(mk, "key_back_color");
    hilited_key_back_color = config.getDrawable(mk, "hilited_key_back_color");
    key_symbol_color = Config.getColor(context, mk, "key_symbol_color");
    hilited_key_symbol_color = Config.getColor(context, mk, "hilited_key_symbol_color");
    round_corner = Config.getFloat(mk, "round_corner");
  }

  public static List<String> getAndroidKeys() {
    return androidKeys;
  }

  public static Map<String, Map<?, ?>> getPresetKeys() {
    return presetKeys;
  }

  public static int getSymbolStart() {
    return symbolStart;
  }

  public static void setSymbolStart(int symbolStart) {
    Key.symbolStart = symbolStart;
  }

  public static String getSymbols() {
    return symbols;
  }

  public static void setSymbols(String symbols) {
    Key.symbols = symbols;
  }

  public static KeyCharacterMap getKcm() {
    return kcm;
  }

  public int getWidth() {
    return width;
  }

  public void setWidth(int width) {
    this.width = width;
  }

  public int getHeight() {
    return height;
  }

  public void setHeight(int height) {
    this.height = height;
  }

  public int getGap() {
    return gap;
  }

  public void setGap(int gap) {
    this.gap = gap;
  }

  public int getEdgeFlags() {
    return edgeFlags;
  }

  public void setEdgeFlags(int edgeFlags) {
    this.edgeFlags = edgeFlags;
  }

  public int getRow() {
    return row;
  }

  public void setRow(int row) {
    this.row = row;
  }

  public int getColumn() {
    return column;
  }

  public void setColumn(int column) {
    this.column = column;
  }

  public String getHint() {
    return hint;
  }

  public Integer getKey_text_size() {
    return key_text_size;
  }

  public Integer getSymbol_text_size() {
    return symbol_text_size;
  }

  public Float getRound_corner() {
    return round_corner;
  }

  public int getX() {
    return x;
  }

  public void setX(int x) {
    this.x = x;
  }

  public int getY() {
    return y;
  }

  public void setY(int y) {
    this.y = y;
  }

  public boolean isPressed() {
    return pressed;
  }

  public boolean isOn() {
    return on;
  }

  public void setOn(boolean on) {
    this.on = on;
  }

  public String getPopupCharacters() {
    return popupCharacters;
  }

  public int getPopupResId() {
    return popupResId;
  }

  public int getKey_text_offset_x() {
    return key_text_offset_x + getKey_offset_x();
  }

  public void setKey_text_offset_x(int key_text_offset_x) {
    this.key_text_offset_x = key_text_offset_x;
  }

  public int getKey_text_offset_y() {
    return key_text_offset_y + getKey_offset_y();
  }

  public void setKey_text_offset_y(int key_text_offset_y) {
    this.key_text_offset_y = key_text_offset_y;
  }

  public int getKey_symbol_offset_x() {
    return key_symbol_offset_x + getKey_offset_x();
  }

  public void setKey_symbol_offset_x(int key_symbol_offset_x) {
    this.key_symbol_offset_x = key_symbol_offset_x;
  }

  public int getKey_symbol_offset_y() {
    return key_symbol_offset_y + getKey_offset_y();
  }

  public void setKey_symbol_offset_y(int key_symbol_offset_y) {
    this.key_symbol_offset_y = key_symbol_offset_y;
  }

  public int getKey_hint_offset_x() {
    return key_hint_offset_x + getKey_offset_x();
  }

  public void setKey_hint_offset_x(int key_hint_offset_x) {
    this.key_hint_offset_x = key_hint_offset_x;
  }

  public int getKey_hint_offset_y() {
    return key_hint_offset_y + getKey_offset_y();
  }

  public void setKey_hint_offset_y(int key_hint_offset_y) {
    this.key_hint_offset_y = key_hint_offset_y;
  }

  public void setKey_press_offset_x(int key_press_offset_x) {
    this.key_press_offset_x = key_press_offset_x;
  }

  public void setKey_press_offset_y(int key_press_offset_y) {
    this.key_press_offset_y = key_press_offset_y;
  }

  public int getKey_offset_x() {
    return pressed ? key_press_offset_x : 0;
  }

  public int getKey_offset_y() {
    return pressed ? key_press_offset_y : 0;
  }

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
    if (getClick().isSticky()) on = !on;
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
    final boolean leftEdge = (edgeFlags & Keyboard.EDGE_LEFT) > 0;
    final boolean rightEdge = (edgeFlags & Keyboard.EDGE_RIGHT) > 0;
    final boolean topEdge = (edgeFlags & Keyboard.EDGE_TOP) > 0;
    final boolean bottomEdge = (edgeFlags & Keyboard.EDGE_BOTTOM) > 0;
    return (x >= this.x || (leftEdge && x <= this.x + this.width))
        && (x < this.x + this.width || (rightEdge && x >= this.x))
        && (y >= this.y || (topEdge && y <= this.y + this.height))
        && (y < this.y + this.height || (bottomEdge && y >= this.y));
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
    boolean isShifted = isShift() && mKeyboard.isShifted(); // 臨時大寫
    if (isShifted || on) {
      if (pressed) {
        states = KEY_STATE_PRESSED_ON;
      } else {
        states = KEY_STATE_NORMAL_ON;
      }
    } else {
      if (getClick().isSticky() || getClick().isFunctional()) {
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
    switch (getClick().getShiftLock()) {
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
    return events[KeyEventType.CLICK.ordinal()];
  }

  public Event getLongClick() {
    return events[KeyEventType.LONG_CLICK.ordinal()];
  }

  public boolean hasEvent(int i) {
    return events[i] != null;
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
    return getClick().getCode();
  }

  public int getCode(int type) {
    return getEvent(type).getCode();
  }

  public String getLabel() {
    Event event = getEvent();
    if (!TextUtils.isEmpty(label) && event == getClick() && (ascii == null && !Rime.isAsciiMode()))
      return label; // 中文狀態顯示標籤
    return event.getLabel();
  }

  public String getPreviewText(int type) {
    if (type == KeyEventType.CLICK.ordinal()) return getEvent().getPreviewText();
    return getEvent(type).getPreviewText();
  }

  public String getSymbolLabel() {
    return getLongClick().getLabel();
  }
}
