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
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import androidx.annotation.NonNull;
import com.osfans.trime.setup.Config;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 從YAML中加載鍵盤配置，包含多個{@link Key 按鍵}。 */
public class Keyboard {
  public static final int EDGE_LEFT = 0x01;
  public static final int EDGE_RIGHT = 0x02;
  public static final int EDGE_TOP = 0x04;
  public static final int EDGE_BOTTOM = 0x08;
  private static final int GRID_WIDTH = 10;
  private static final int GRID_HEIGHT = 5;
  private static final int GRID_SIZE = GRID_WIDTH * GRID_HEIGHT;
  // private static final String TAG = Keyboard.class.getSimpleName();
  /** Number of key widths from current touch point to search for nearest keys. */
  public static float SEARCH_DISTANCE = 1.4f;
  /** 按鍵默認水平間距 */
  private int mDefaultHorizontalGap;
  /** 默認鍵寬 */
  private int mDefaultWidth;
  /** 默認鍵高 */
  private int mDefaultHeight;
  /** 默認行距 */
  private int mDefaultVerticalGap;
  /** 默認按鍵圓角半徑 */
  private float mRoundCorner;
  /** 鍵盤背景 */
  private Drawable mBackground;
  /** 鍵盤的Shift鍵是否按住 * */
  // private boolean mShifted;
  /** 鍵盤的Shift鍵 */
  private Key mShiftKey;
  /** Total height of the keyboard, including the padding and keys */
  private int mTotalHeight;
  /**
   * Total width of the keyboard, including left side gaps and keys, but not any gaps on the right
   * side.
   */
  private int mTotalWidth;
  /** List of keys in this keyboard */
  private final List<Key> mKeys;

  private final List<Key> mComposingKeys;
  private int mMetaState;
  /** Width of the screen available to fit the keyboard */
  private int mDisplayWidth;
  /** Keyboard mode, or zero, if none. */
  private int mAsciiMode;

  // Variables for pre-computing nearest keys.
  private String mLabelTransform;
  private int mCellWidth;
  private int mCellHeight;
  private int[][] mGridNeighbors;
  private int mProximityThreshold;

  private boolean mLock; // 切換程序時記憶鍵盤
  private String mAsciiKeyboard; // 英文鍵盤

  /**
   * Creates a keyboard from the given xml key layout file.
   *
   * @param context the application or service context
   */
  public Keyboard(@NonNull Context context) {

    // 橫屏模式下，键盘左右两侧到屏幕边缘的距离
    final boolean land =
        (context.getResources().getConfiguration().orientation
            == Configuration.ORIENTATION_LANDSCAPE);

    final Config config = Config.get(context);

    final DisplayMetrics dm = context.getResources().getDisplayMetrics();
    mDisplayWidth = dm.widthPixels;
    int[] keyboardPadding = config.getKeyboardPadding(land);
    mDisplayWidth = mDisplayWidth - keyboardPadding[0] - keyboardPadding[1];
    /* Height of the screen */
    // final int mDisplayHeight = dm.heightPixels;
    // Log.v(TAG, "keyboard's display metrics:" + dm);

    mDefaultHorizontalGap = config.getPixel("horizontal_gap");
    mDefaultVerticalGap = config.getPixel("vertical_gap");
    mDefaultWidth = (int) (mDisplayWidth * config.getDouble("key_width") / 100);

    mDefaultHeight = config.getPixel("key_height");
    if (land) mDefaultHeight = config.getPixel("key_height_land", mDefaultHeight);

    mProximityThreshold = (int) (mDefaultWidth * SEARCH_DISTANCE);
    mProximityThreshold = mProximityThreshold * mProximityThreshold; // Square it for comparison
    mRoundCorner = config.getFloat("round_corner");
    mBackground = config.getColorDrawable("keyboard_back_color");

    mKeys = new ArrayList<>();
    mComposingKeys = new ArrayList<>();
  }
  /**
   * Creates a blank keyboard from the given resource file and populates it with the specified
   * characters in left-to-right, top-to-bottom fashion, using the specified number of columns.
   *
   * <p>
   *
   * <p>If the specified number of columns is -1, then the keyboard will fit as many keys as
   * possible in each row.
   *
   * @param context the application or service context
   * @param characters the list of characters to display on the keyboard. One key will be created
   *     for each character.
   * @param columns the number of columns of keys to display. If this number is greater than the
   *     number of keys that can fit in a row, it will be ignored. If this number is -1, the
   *     keyboard will fit as many keys as possible in each row.
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
      if (column >= maxColumns || x + mDefaultWidth + horizontalPadding > mDisplayWidth) {
        x = 0;
        y += mDefaultVerticalGap + mDefaultHeight;
        column = 0;
      }
      final Key key = new Key(this);
      key.setX(x);
      key.setY(y);
      key.setWidth(mDefaultWidth);
      key.setHeight(mDefaultHeight);
      key.setGap(mDefaultHorizontalGap);
      key.events[0] = new Event(this, String.valueOf(c));
      column++;
      x += key.getWidth() + key.getGap();
      mKeys.add(key);
      if (x > mTotalWidth) {
        mTotalWidth = x;
      }
    }
    mTotalHeight = y + mDefaultHeight;
  }

  public Keyboard(Context context, String name) {
    this(context);
    Config config = Config.get(context);
    final Map<?, ?> m = config.getKeyboard(name);
    mLabelTransform = Config.getString(m, "label_transform", "none");
    mAsciiMode = Config.getInt(m, "ascii_mode", 1);
    if (mAsciiMode == 0) mAsciiKeyboard = Config.getString(m, "ascii_keyboard");
    mLock = Config.getBoolean(m, "lock", false);
    int columns = Config.getInt(m, "columns", 30);
    int defaultWidth = (int) (Config.getDouble(m, "width", 0) * mDisplayWidth / 100);
    if (defaultWidth == 0) defaultWidth = mDefaultWidth;
    int height = Config.getPixel(m, "height", 0);
    int defaultHeight = (height > 0) ? height : mDefaultHeight;
    int rowHeight = defaultHeight;
    List<Map<String, Object>> lm = (List<Map<String, Object>>) m.get("keys");

    if (m.containsKey("horizontal_gap"))
      mDefaultHorizontalGap = Config.getPixel(m, "horizontal_gap");
    if (m.containsKey("vertical_gap")) mDefaultVerticalGap = Config.getPixel(m, "vertical_gap");
    if (m.containsKey("round_corner")) mRoundCorner = Config.getFloat(m, "round_corner");

    Drawable background = config.getDrawable(m, "keyboard_back_color");
    if (background != null) mBackground = background;

    int x = mDefaultHorizontalGap / 2;
    int y = mDefaultVerticalGap;
    int row = 0;
    int column = 0;
    mTotalWidth = 0;
    final int key_text_offset_x = Config.getPixel(m, "key_text_offset_x", 0);
    final int key_text_offset_y = Config.getPixel(m, "key_text_offset_y", 0);
    final int key_symbol_offset_x = Config.getPixel(m, "key_symbol_offset_x", 0);
    final int key_symbol_offset_y = Config.getPixel(m, "key_symbol_offset_y", 0);
    final int key_hint_offset_x = Config.getPixel(m, "key_hint_offset_x", 0);
    final int key_hint_offset_y = Config.getPixel(m, "key_hint_offset_y", 0);
    final int key_press_offset_x = Config.getInt(m, "key_press_offset_x", 0);
    final int key_press_offset_y = Config.getInt(m, "key_press_offset_y", 0);

    final int maxColumns = columns == -1 ? Integer.MAX_VALUE : columns;
    for (Map<String, Object> mk : lm) {
      int gap = mDefaultHorizontalGap;
      int w = (int) (Config.getDouble(mk, "width", 0) * mDisplayWidth / 100);
      if (w == 0 && mk.containsKey("click")) w = defaultWidth;
      w -= gap;
      if (column >= maxColumns || x + w > mDisplayWidth) {
        x = gap / 2;
        y += mDefaultVerticalGap + rowHeight;
        column = 0;
        row++;
        if (mKeys.size() > 0) mKeys.get(mKeys.size() - 1).edgeFlags |= Keyboard.EDGE_RIGHT;
      }
      if (column == 0) {
        int heightK = Config.getPixel(mk, "height", 0);
        rowHeight = (heightK > 0) ? heightK : defaultHeight;
      }
      if (!mk.containsKey("click")) { // 無按鍵事件
        x += w + gap;
        continue; // 縮進
      }

      final Key key = new Key(context, this, mk);
      key.setKey_text_offset_x(Config.getPixel(mk, "key_text_offset_x", key_text_offset_x));
      key.setKey_text_offset_y(Config.getPixel(mk, "key_text_offset_y", key_text_offset_y));
      key.setKey_symbol_offset_x(Config.getPixel(mk, "key_symbol_offset_x", key_symbol_offset_x));
      key.setKey_symbol_offset_y(Config.getPixel(mk, "key_symbol_offset_y", key_symbol_offset_y));
      key.setKey_hint_offset_x(Config.getPixel(mk, "key_hint_offset_x", key_hint_offset_x));
      key.setKey_hint_offset_y(Config.getPixel(mk, "key_hint_offset_y", key_hint_offset_y));
      key.setKey_press_offset_x(Config.getInt(mk, "key_press_offset_x", key_press_offset_x));
      key.setKey_press_offset_y(Config.getInt(mk, "key_press_offset_y", key_press_offset_y));

      key.setX(x);
      key.setY(y);
      int right_gap = Math.abs(mDisplayWidth - x - w - gap / 2);
      // 右側不留白
      key.setWidth((right_gap <= mDisplayWidth / 100) ? mDisplayWidth - x - gap / 2 : w);
      key.setHeight(rowHeight);
      key.setGap(gap);
      key.setRow(row);
      key.setColumn(column);
      column++;
      x += key.getWidth() + key.getGap();
      mKeys.add(key);
      if (x > mTotalWidth) {
        mTotalWidth = x;
      }
    }
    if (mKeys.size() > 0) mKeys.get(mKeys.size() - 1).edgeFlags |= Keyboard.EDGE_RIGHT;
    mTotalHeight = y + rowHeight + mDefaultVerticalGap;
    for (Key key : mKeys) {
      if (key.getColumn() == 0) key.edgeFlags |= Keyboard.EDGE_LEFT;
      if (key.getRow() == 0) key.edgeFlags |= Keyboard.EDGE_TOP;
      if (key.getRow() == row) key.edgeFlags |= Keyboard.EDGE_BOTTOM;
    }
  }

  public Key getmShiftKey() {
    return mShiftKey;
  }

  public void setmShiftKey(Key mShiftKey) {
    this.mShiftKey = mShiftKey;
  }

  public List<Key> getmComposingKeys() {
    return mComposingKeys;
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
   *
   * @return the total height of the keyboard
   */
  public int getHeight() {
    return mTotalHeight;
  }

  public int getMinWidth() {
    return mTotalWidth;
  }

  private boolean hasModifier(int modifiers) {
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

  private boolean setModifier(int mask, boolean value) {
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
   *
   * @param on 是否保持Shift按下狀態
   * @param shifted 是否按下Shift
   * @return Shift鍵狀態是否改變
   */
  public boolean setShifted(boolean on, boolean shifted) {
    on = on & shifted;
    if (mShiftKey != null) mShiftKey.setOn(on);
    return setModifier(KeyEvent.META_SHIFT_ON, on || shifted);
  }

  public boolean resetShifted() {
    if (mShiftKey != null && !mShiftKey.isOn()) return setModifier(KeyEvent.META_SHIFT_ON, false);
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
          if (key.squaredDistanceFrom(x, y) < mProximityThreshold
              || key.squaredDistanceFrom(x + mCellWidth - 1, y) < mProximityThreshold
              || key.squaredDistanceFrom(x + mCellWidth - 1, y + mCellHeight - 1)
                  < mProximityThreshold
              || key.squaredDistanceFrom(x, y + mCellHeight - 1) < mProximityThreshold
              || key.isInside(x, y)
              || key.isInside(x + mCellWidth - 1, y)
              || key.isInside(x + mCellWidth - 1, y + mCellHeight - 1)
              || key.isInside(x, y + mCellHeight - 1)) {
            indices[count++] = i;
          }
        }
        int[] cell = new int[count];
        System.arraycopy(indices, 0, cell, 0, count);
        mGridNeighbors[(y / mCellHeight) * GRID_WIDTH + (x / mCellWidth)] = cell;
      }
    }
  }

  /**
   * Returns the indices of the keys that are closest to the given point.
   *
   * @param x the x-coordinate of the point
   * @param y the y-coordinate of the point
   * @return the array of integer indices for the nearest keys to the given point. If the given
   *     point is out of range, then an array of size zero is returned.
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

  public boolean getAsciiMode() {
    return mAsciiMode != 0;
  }

  public String getAsciiKeyboard() {
    return mAsciiKeyboard;
  }

  public boolean isLabelUppercase() {
    return mLabelTransform.contentEquals("uppercase");
  }

  public boolean isLock() {
    return mLock;
  }

  public float getRoundCorner() {
    return mRoundCorner;
  }

  public Drawable getBackground() {
    return mBackground;
  }
}
