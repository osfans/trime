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

import org.xmlpull.v1.XmlPullParserException;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.util.Xml;
import android.util.DisplayMetrics;
import android.view.KeyEvent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.StringTokenizer;

/** 從YAML中加載鍵盤配置，包含多個{@link Key 按鍵}。 */
public class Keyboard {

    static final String TAG = "Keyboard";

    public static final int EDGE_LEFT = 0x01;
    public static final int EDGE_RIGHT = 0x02;
    public static final int EDGE_TOP = 0x04;
    public static final int EDGE_BOTTOM = 0x08;

    /** 按鍵默認水平間距 */
    private int mDefaultHorizontalGap;
    
    /** 默認鍵寬 */
    private int mDefaultWidth;

    /** 默認鍵高 */
    private int mDefaultHeight;

    /** 默認行距 */
    private int mDefaultVerticalGap;

    /** 鍵盤的Shift鍵是否按住 */
    private boolean mShifted;
    
    /** 鍵盤的Shift鍵 */
    public Key mShiftKey;
    
    /** Total height of the keyboard, including the padding and keys */
    private int mTotalHeight;
    
    /** 
     * Total width of the keyboard, including left side gaps and keys, but not any gaps on the
     * right side.
     */
    private int mTotalWidth;
    
    /** List of keys in this keyboard */
    public List<Key> mKeys, mComposingKeys;

    private int mMetaState;
    
    /** Width of the screen available to fit the keyboard */
    private int mDisplayWidth;

    /** Height of the screen */
    private int mDisplayHeight;

    /** Keyboard mode, or zero, if none.  */
    private int mAsciiMode;

    // Variables for pre-computing nearest keys.
    
    private static final int GRID_WIDTH = 10;
    private static final int GRID_HEIGHT = 5;
    private static final int GRID_SIZE = GRID_WIDTH * GRID_HEIGHT;
    private int mCellWidth;
    private int mCellHeight;
    private int[][] mGridNeighbors;
    private int mProximityThreshold;
    /** Number of key widths from current touch point to search for nearest keys. */
    public static float SEARCH_DISTANCE = 1.4f;

    /**
     * Creates a keyboard from the given xml key layout file.
     * @param context the application or service context
     */
    public Keyboard(Context context) {
      DisplayMetrics dm = context.getResources().getDisplayMetrics();
      mDisplayWidth = dm.widthPixels;
      mDisplayHeight = dm.heightPixels;
      //Log.v(TAG, "keyboard's display metrics:" + dm);

      Config config = Config.get();
      mDefaultHorizontalGap = config.getPixel("horizontal_gap");
      mDefaultVerticalGap = config.getPixel("vertical_gap");
      mDefaultWidth = (int)(mDisplayWidth * config.getDouble("key_width") / 100);
      mDefaultHeight = config.getPixel("key_height");
      mProximityThreshold = (int) (mDefaultWidth * SEARCH_DISTANCE);
      mProximityThreshold = mProximityThreshold * mProximityThreshold; // Square it for comparison

      mKeys = new ArrayList<Key>();
      mComposingKeys = new ArrayList<Key>();
    }

    /**
     * <p>Creates a blank keyboard from the given resource file and populates it with the specified
     * characters in left-to-right, top-to-bottom fashion, using the specified number of columns.
     * </p>
     * <p>If the specified number of columns is -1, then the keyboard will fit as many keys as
     * possible in each row.</p>
     * @param context the application or service context
     * @param characters the list of characters to display on the keyboard. One key will be created
     * for each character.
     * @param columns the number of columns of keys to display. If this number is greater than the 
     * number of keys that can fit in a row, it will be ignored. If this number is -1, the 
     * keyboard will fit as many keys as possible in each row.
     * @param horizontalPadding 按鍵水平間距
     */
    public Keyboard(Context context, CharSequence characters, int columns, int horizontalPadding) {
        this(context);
        int x = 0;
        int y = 0;
        int column = 0;
        mTotalWidth = 0;

        final int maxColumns = columns == -1 ? Integer.MAX_VALUE : columns;
        for (int i = 0; i < characters.length(); i++) {
            char c = characters.charAt(i);
            if (column >= maxColumns 
                    || x + mDefaultWidth + horizontalPadding > mDisplayWidth) {
                x = 0;
                y += mDefaultVerticalGap + mDefaultHeight;
                column = 0;
            }
            final Key key = new Key(this);
            key.x = x;
            key.y = y;
            key.width = mDefaultWidth;
            key.height = mDefaultHeight;
            key.gap = mDefaultHorizontalGap;
            key.events[0] = new Event(this, String.valueOf(c));
            column++;
            x += key.width + key.gap;
            mKeys.add(key);
            if (x > mTotalWidth) {
                mTotalWidth = x;
            }
        }
        mTotalHeight = y + mDefaultHeight; 
    }

    public List<Key> getKeys() {
        return mKeys;
    }

    public List<Key> getComposingKeys() {
        return mComposingKeys;
    }

    protected int getHorizontalGap() {
        return mDefaultHorizontalGap;
    }
    
    protected void setHorizontalGap(int gap) {
        mDefaultHorizontalGap = gap;
    }

    protected int getVerticalGap() {
        return mDefaultVerticalGap;
    }

    protected void setVerticalGap(int gap) {
        mDefaultVerticalGap = gap;
    }

    protected int getKeyHeight() {
        return mDefaultHeight;
    }

    protected void setKeyHeight(int height) {
        mDefaultHeight = height;
    }

    protected int getKeyWidth() {
        return mDefaultWidth;
    }
    
    protected void setKeyWidth(int width) {
        mDefaultWidth = width;
    }

    /**
     * Returns the total height of the keyboard
     * @return the total height of the keyboard
     */
    public int getHeight() {
        return mTotalHeight;
    }
    
    public int getMinWidth() {
        return mTotalWidth;
    }

  public boolean hasModifier(int modifiers) {
    return (mMetaState & modifiers) != 0;
  }

  public boolean hasModifier() {
    return mMetaState != 0;
  }

  public boolean toggleModifier(int mask) {
    boolean value = !hasModifier(mask);
    if (value) mMetaState |= mask;
    else mMetaState &= ~mask;
    return value;
  }

  public int getModifer() {
    return mMetaState;
  }

  public boolean setModifier(int mask, boolean value) {
    boolean b = hasModifier(mask);
    if (b == value) return false;
    if (value) mMetaState |= mask;
    else mMetaState &= ~mask;
    return true;
  }

  public boolean isAlted() {
    return hasModifier(KeyEvent.META_ALT_ON);
  }

  public boolean isShifted() {
    return hasModifier(KeyEvent.META_SHIFT_ON);
  }

  public boolean isCtrled() {
    return hasModifier(KeyEvent.META_CTRL_ON);
  }

  /**
   * 設定鍵盤的Shift鍵狀態
   * @param on 是否保持Shift按下狀態
   * @param shifted 是否按下Shift
   * @return Shift鍵狀態是否改變
   */
  public boolean setShifted(boolean on, boolean shifted) {
    if (mShiftKey != null) mShiftKey.on = on;
    return setModifier(KeyEvent.META_SHIFT_ON, on || shifted);
  }

  public boolean resetShifted() {
    if (mShiftKey != null && !mShiftKey.on) return setModifier(KeyEvent.META_SHIFT_ON, false);
    return false;
  }

    private void computeNearestNeighbors() {
        // Round-up so we don't have any pixels outside the grid
        mCellWidth = (getMinWidth() + GRID_WIDTH - 1) / GRID_WIDTH;
        mCellHeight = (getHeight() + GRID_HEIGHT - 1) / GRID_HEIGHT;
        mGridNeighbors = new int[GRID_SIZE][];
        int[] indices = new int[mKeys.size()];
        final int gridWidth = GRID_WIDTH * mCellWidth;
        final int gridHeight = GRID_HEIGHT * mCellHeight;
        for (int x = 0; x < gridWidth; x += mCellWidth) {
            for (int y = 0; y < gridHeight; y += mCellHeight) {
                int count = 0;
                for (int i = 0; i < mKeys.size(); i++) {
                    final Key key = mKeys.get(i);
                    if (key.squaredDistanceFrom(x, y) < mProximityThreshold ||
                            key.squaredDistanceFrom(x + mCellWidth - 1, y) < mProximityThreshold ||
                            key.squaredDistanceFrom(x + mCellWidth - 1, y + mCellHeight - 1) 
                                < mProximityThreshold ||
                            key.squaredDistanceFrom(x, y + mCellHeight - 1) < mProximityThreshold) {
                        indices[count++] = i;
                    }
                }
                int [] cell = new int[count];
                System.arraycopy(indices, 0, cell, 0, count);
                mGridNeighbors[(y / mCellHeight) * GRID_WIDTH + (x / mCellWidth)] = cell;
            }
        }
    }
    
    /**
     * Returns the indices of the keys that are closest to the given point.
     * @param x the x-coordinate of the point
     * @param y the y-coordinate of the point
     * @return the array of integer indices for the nearest keys to the given point. If the given
     * point is out of range, then an array of size zero is returned.
     */
    public int[] getNearestKeys(int x, int y) {
        if (mGridNeighbors == null) computeNearestNeighbors();
        if (x >= 0 && x < getMinWidth() && y >= 0 && y < getHeight()) {
            int index = (y / mCellHeight) * GRID_WIDTH + (x / mCellWidth);
            if (index < GRID_SIZE) {
                return mGridNeighbors[index];
            }
        }
        return new int[0];
    }

  public Keyboard(Context context, String name) {
    this(context);
    Map<String,Object> m = Config.get().getKeyboard(name);
    mAsciiMode = (Integer)Key.getValue(m, "ascii_mode", 1);
    int columns = (Integer)Key.getValue(m, "columns", 20);
    int defaultWidth = (int)(Key.getDouble(m, "width", 0) * mDisplayWidth / 100);
    if (defaultWidth == 0) defaultWidth = mDefaultWidth;
    double height = Key.getDouble(m, "height", 0);
    int defaultHeight = mDefaultHeight;
    if (height > 0) defaultHeight = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, (float)height, Resources.getSystem().getDisplayMetrics());
    int rowHeight = defaultHeight;
    List<Map<String,Object>> lm = (List<Map<String,Object>>)m.get("keys");

    int x = mDefaultHorizontalGap/2;
    int y = mDefaultVerticalGap;
    int row = 0;
    int column = 0;
    mTotalWidth = 0;

    final int maxColumns = columns == -1 ? Integer.MAX_VALUE : columns;
    for (Map<String,Object> mk: lm) {
      int gap = mDefaultHorizontalGap;
      int w = (int)(Key.getDouble(mk, "width", 0) * mDisplayWidth / 100);
      if (w == 0 && mk.containsKey("click")) w = defaultWidth;
      w -= gap;
      if (column >= maxColumns || x + w > mDisplayWidth) {
        x = gap/2;
        y += mDefaultVerticalGap + rowHeight;
        column = 0;
        row++;
        if (mKeys.size() > 0) mKeys.get(mKeys.size() - 1).edgeFlags |= Keyboard.EDGE_RIGHT;
      }
      if (column == 0) {
        double heightK = Key.getDouble(mk, "height", 0);
        if (heightK > 0) {
          rowHeight = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, (float)heightK, Resources.getSystem().getDisplayMetrics());
        } else {
          rowHeight = defaultHeight;
        }
      }
      if (!mk.containsKey("click")){ //無按鍵事件
        x += w + gap;
        continue; //縮進
      }

      final Key key = new Key(this, mk);
      key.x = x;
      key.y = y;
      int right_gap = Math.abs(mDisplayWidth - x - w - gap/2);
      key.width = (right_gap <= mDisplayWidth / 100) ? mDisplayWidth - x - gap/2: w; //右側不留白
      key.height = rowHeight;
      key.gap = gap;
      key.row = row;
      key.column = column;
      column++;
      x += key.width + key.gap;
      mKeys.add(key);
      if (x > mTotalWidth) {
          mTotalWidth = x;
      }
    }
    if (mKeys.size() > 0) mKeys.get(mKeys.size() - 1).edgeFlags |= Keyboard.EDGE_RIGHT;
    mTotalHeight = y + rowHeight + mDefaultVerticalGap;
    for (Key key: mKeys) {
      if (key.column == 0) key.edgeFlags |= Keyboard.EDGE_LEFT;
      if (key.row == 0) key.edgeFlags |= Keyboard.EDGE_TOP;
      if (key.row == row) key.edgeFlags |= Keyboard.EDGE_BOTTOM;
    }
  }

  public boolean getAsciiMode() {
    return mAsciiMode != 0;
  }
}
