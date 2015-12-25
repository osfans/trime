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

import android.graphics.drawable.Drawable;
import android.view.KeyEvent;
import android.util.Log;
import android.os.Build;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/** {@link Keyboard 鍵盤}中的各個按鍵，包含單擊、長按、滑動等多種{@link Event 事件} */
public class Key {
  private String TAG = "Key";
  private Keyboard mKeyboard;
  public Event composing, ascii, has_menu, paging;
  public String[] eventTypes = new String[]{"click", "long_click", "swipe_left", "swipe_right", "swipe_up", "swipe_down"};
  public static final int CLICK = 0;
  public static final int LONG_CLICK = 1;
  public static final int SWIPE_LEFT = 2;
  public static final int SWIPE_RIGHT = 3;
  public static final int SWIPE_UP = 4;
  public static final int SWIPE_DOWN = 5;
  public static final int EVENT_NUM = 6;
  public Event[] events = new Event[EVENT_NUM];

  public int width, height, gap, edgeFlags;
  public String label, hint;

  public int x, y;
  public boolean pressed, on;

  public String popupCharacters;
  public int popupResId;

  public static List<String> androidKeys;
  public static Map<String, Map> presetKeys;

  /**
   * Create an empty key with no attributes.
   * @param parent 按鍵所在的{@link Keyboard 鍵盤}
   */
  public Key(Keyboard parent) {
      mKeyboard = parent;
  }

  /**
   * Create an empty key with no attributes.
   * @param parent 按鍵所在的{@link Keyboard 鍵盤}
   * @param mk 從YAML中解析得到的Map
   */
  public Key(Keyboard parent, Map<String,Object> mk) {
    this(parent);
    String s;
    for (int i = 0; i < EVENT_NUM; i++) {
      String eventType = eventTypes[i];
      s = getString(mk, eventType);
      if (s.length() > 0) events[i] = new Event(mKeyboard, s);
    }
    s = getString(mk, "composing");
    if (s.length() > 0) {
      composing = new Event(mKeyboard, s);
    }
    s = getString(mk, "has_menu");
    if (s.length() > 0) {
      has_menu = new Event(mKeyboard, s);
    }
    s = getString(mk, "paging");
    if (s.length() > 0) {
      paging = new Event(mKeyboard, s);
    }
    if (composing != null || has_menu != null || paging != null) mKeyboard.mComposingKeys.add(this);
    s = getString(mk, "ascii");
    if (!Function.isEmpty(s)) ascii = new Event(mKeyboard, s);
    label = getString(mk, "label");
    hint = getString(mk, "hint");
    if (isShift()) mKeyboard.mShiftKey = this;
  }
  
  public final static int[] KEY_STATE_NORMAL_ON = { 
      android.R.attr.state_checkable, 
      android.R.attr.state_checked
  };
  
  public final static int[] KEY_STATE_PRESSED_ON = { 
      android.R.attr.state_pressed, 
      android.R.attr.state_checkable, 
      android.R.attr.state_checked 
  };
  
  public final static int[] KEY_STATE_NORMAL_OFF = { 
      android.R.attr.state_checkable 
  };
  
  public final static int[] KEY_STATE_PRESSED_OFF = { 
      android.R.attr.state_pressed, 
      android.R.attr.state_checkable 
  };
  
  public final static int[] KEY_STATE_NORMAL = {
  };
  
  public final static int[] KEY_STATE_PRESSED = {
      android.R.attr.state_pressed
  };

  public final static int[][] KEY_STATES = new int[][]{
      KEY_STATE_PRESSED_ON,
      KEY_STATE_PRESSED_OFF,
      KEY_STATE_NORMAL_ON,
      KEY_STATE_NORMAL_OFF,
      KEY_STATE_PRESSED,
      KEY_STATE_NORMAL
  };

    /**
     * Informs the key that it has been pressed, in case it needs to change its appearance or
     * state.
     * @see #onReleased(boolean)
     */
    public void onPressed() {
        pressed = !pressed;
    }
    
    /**
     * Changes the pressed state of the key. If it is a sticky key, it will also change the
     * toggled state of the key if the finger was release inside.
     * @param inside whether the finger was released inside the key
     * @see #onPressed()
     */
    public void onReleased(boolean inside) {
        pressed = !pressed;
        if (getClick().sticky) on = !on;
    }

    /**
     * Detects if a point falls inside this key.
     * @param x the x-coordinate of the point 
     * @param y the y-coordinate of the point
     * @return whether or not the point falls inside the key. If the key is attached to an edge,
     * it will assume that all points between the key and the edge are considered to be inside
     * the key.
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
     * @return the drawable state of the key.
     * @see android.graphics.drawable.StateListDrawable#setState(int[])
     */
    public int[] getCurrentDrawableState() {
        int[] states = KEY_STATE_NORMAL;

        if (on) {
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

  public static String getString(Map m, String k) {
    if (m.containsKey(k)) return m.get(k).toString();
    return "";
  }

  public static Object getValue(Map m, String k, Object o) {
    return m.containsKey(k) ? m.get(k) : o;
  }

  public static double getDouble(Map m, String k, Object i) {
    Object o = getValue(m, k, i);
    if (o instanceof Integer) return ((Integer)o).doubleValue();
    else if (o instanceof Float) return ((Float)o).doubleValue();
    else if (o instanceof Double) return ((Double)o).doubleValue();
    return 0f;
  }

  public Event getEvent() {
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
    if (i >= 0 && i <= EVENT_NUM) e = events[i];
    if (e != null) return e;
    return getClick();
  }

  public int getCode() {
    return getEvent(CLICK).code;
  }

  public int getCode(int type) {
    return getEvent(type).code;
  }

  public String getLabel() {
    Event event = getEvent();
    if (!Function.isEmpty(label) && event == getClick()
    && (ascii == null && !Rime.isAsciiMode()))
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
