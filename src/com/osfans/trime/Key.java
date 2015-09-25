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

public class Key {
  private String TAG = "Key";
  private Keyboard mKeyboard;
  public Event click, long_click, composing, has_menu, paging;
  public int width, height, gap, edgeFlags;
  public String hint;

  public int x, y;
  public boolean pressed, on;

  public String popupCharacters;
  public int popupResId;

  public static List<String> androidKeys;
  public static Map<String, Map> presetKeys;

  /** Create an empty key with no attributes. */
  public Key(Keyboard parent) {
      mKeyboard = parent;
  }

  public Key(Keyboard parent, Map<String,Object> mk) {
    this(parent);
    String s;
    s = getString(mk, "click");
    if (!s.isEmpty()) click = new Event(mKeyboard, s);
    s = getString(mk, "long_click");
    if (!s.isEmpty()) long_click = new Event(mKeyboard, s);
    hint = getString(mk, "hint");
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
        if (click.sticky) on = !on;
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
            if (click.sticky || click.functional) {
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
    int c = click.code;
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

  public String getLabel() {
    return click.getLabel();
  }

  public String getPreviewText() {
    return click.getPreviewText();
  }

  public String getSymbolLabel() {
    return long_click.getLabel();
  }
}
