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
import com.osfans.trime.data.Config;
import com.osfans.trime.util.ConfigGetter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import timber.log.Timber;

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
  private Key mShiftKey, mCtrlKey, mAltKey, mMetaKey, mSymKey;
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
  private int mModifierState;
  /** Width of the screen available to fit the keyboard */
  private int mDisplayWidth;
  /** Keyboard mode, or zero, if none. */
  private int mAsciiMode;

  private boolean resetAsciiMode;

  // Variables for pre-computing nearest keys.
  private String mLabelTransform;
  private int mCellWidth;
  private int mCellHeight;
  private int[][] mGridNeighbors;
  private int mProximityThreshold;

  private boolean mLock; // 切換程序時記憶鍵盤
  private String mAsciiKeyboard; // 英文鍵盤

  // 定义 新的键盘尺寸计算方式， 避免尺寸计算不恰当，导致切换键盘时键盘高度发生变化，UI闪烁的问题。同时可以快速调整整个键盘的尺寸
  // 1. default键盘的高度 = 其他键盘的高度
  // 2. 当键盘高度(不含padding)与keyboard_height不一致时，每行按键等比例缩放按键高度高度，行之间的间距向上取整数、padding不缩放；
  // 3. 由于高度只能取整数，缩放后仍然存在余数的，由 auto_height_index 指定的行吸收（遵循四舍五入）
  //    特别的，当值为负数时，为倒序序号（-1即倒数第一个）;当值大于按键行数时，为最后一行
  private int autoHeightIndex, keyboardHeight;

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
    int[] keyboardPadding = config.getKeyboardPadding();
    mDisplayWidth = mDisplayWidth - keyboardPadding[0] - keyboardPadding[1];
    /* Height of the screen */
    // final int mDisplayHeight = dm.heightPixels;
    // Log.v(TAG, "keyboard's display metrics:" + dm);

    mDefaultHorizontalGap = config.getPixel("horizontal_gap");
    mDefaultVerticalGap = config.getPixel("vertical_gap");
    mDefaultWidth = (int) (mDisplayWidth * config.getDouble("key_width") / 100);

    mDefaultHeight = config.getPixel("key_height");

    mProximityThreshold = (int) (mDefaultWidth * SEARCH_DISTANCE);
    mProximityThreshold = mProximityThreshold * mProximityThreshold; // Square it for comparison
    mRoundCorner = config.getFloat("round_corner");
    mBackground = config.getColorDrawable("keyboard_back_color");

    keyboardHeight = config.getPixel("keyboard_height");
    if (land) {
      int keyBoardHeightLand = config.getPixel("keyboard_height_land");
      if (keyBoardHeightLand > 0) keyboardHeight = keyBoardHeightLand;
    }

    mKeys = new ArrayList<>();
    mComposingKeys = new ArrayList<>();
  }

  // todo 把按下按键弹出的内容改为单独设计的view，而不是keyboard
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
    final boolean land =
        (context.getResources().getConfiguration().orientation
            == Configuration.ORIENTATION_LANDSCAPE);
    Config config = Config.get(context);
    final Map<String, ?> keyboardConfig = config.getKeyboard(name);
    mLabelTransform = ConfigGetter.getString(keyboardConfig, "label_transform", "none");
    mAsciiMode = ConfigGetter.getInt(keyboardConfig, "ascii_mode", 1);
    if (mAsciiMode == 0)
      mAsciiKeyboard = ConfigGetter.getString(keyboardConfig, "ascii_keyboard", "");
    resetAsciiMode = ConfigGetter.getBoolean(keyboardConfig, "reset_ascii_mode", false);
    mLock = ConfigGetter.getBoolean(keyboardConfig, "lock", false);
    int columns = ConfigGetter.getInt(keyboardConfig, "columns", 30);
    int defaultWidth =
        (int) (ConfigGetter.getDouble(keyboardConfig, "width", 0d) * mDisplayWidth / 100);
    if (defaultWidth == 0) defaultWidth = mDefaultWidth;

    // 按键高度取值顺序： keys > keyboard/height > style/key_height
    // 考虑到key设置height_land需要对皮肤做大量修改，而当部分key设置height而部分没有设时会造成按键高度异常，故取消普通按键的height_land参数
    int height = ConfigGetter.getPixel(keyboardConfig, "height", 0);
    int defaultHeight = (height > 0) ? height : mDefaultHeight;
    int rowHeight = defaultHeight;
    autoHeightIndex = ConfigGetter.getInt(keyboardConfig, "auto_height_index", -1);
    List<Map<String, Object>> lm = (List<Map<String, Object>>) keyboardConfig.get("keys");

    mDefaultHorizontalGap =
        ConfigGetter.getPixel(
            keyboardConfig, "horizontal_gap", config.getFloat("horizontal_gap", 3));
    mDefaultVerticalGap =
        ConfigGetter.getPixel(keyboardConfig, "vertical_gap", config.getFloat("vertical_gap", 5));
    mRoundCorner =
        ConfigGetter.getFloat(keyboardConfig, "round_corner", config.getFloat("round_corner", 5));

    Drawable background = config.getDrawable(keyboardConfig, "keyboard_back_color");
    if (background != null) mBackground = background;

    int x = mDefaultHorizontalGap / 2;
    int y = mDefaultVerticalGap;
    int row = 0;
    int column = 0;
    mTotalWidth = 0;

    final int maxColumns = columns == -1 ? Integer.MAX_VALUE : columns;
    float scale;
    int[] newHeight = new int[0];

    if (keyboardHeight > 0) {
      int mkeyboardHeight = ConfigGetter.getPixel(keyboardConfig, "keyboard_height", 0);
      if (land) {
        int mkeyBoardHeightLand = ConfigGetter.getPixel(keyboardConfig, "keyboard_height_land", 0);
        if (mkeyBoardHeightLand > 0) mkeyboardHeight = mkeyBoardHeightLand;
      }

      if (mkeyboardHeight > 0) keyboardHeight = mkeyboardHeight;

      int rawSumHeight = 0;
      List<Integer> rawHeight = new ArrayList<>();
      for (Map<String, Object> mk : lm) {
        int gap = mDefaultHorizontalGap;
        int w = (int) (ConfigGetter.getDouble(mk, "width", 0) * mDisplayWidth / 100);
        if (w == 0 && mk.containsKey("click")) w = defaultWidth;
        w -= gap;
        if (column >= maxColumns || x + w > mDisplayWidth) {
          x = gap / 2;
          y += mDefaultVerticalGap + rowHeight;
          column = 0;
          row++;
          rawSumHeight += rowHeight;
          rawHeight.add(rowHeight);
        }
        if (column == 0) {
          int heightK = ConfigGetter.getPixel(mk, "height", 0);
          rowHeight = (heightK > 0) ? heightK : defaultHeight;
        }
        if (!mk.containsKey("click")) { // 無按鍵事件
          x += w + gap;
          continue; // 縮進
        }
        column++;
        int right_gap = Math.abs(mDisplayWidth - x - w - gap / 2);
        x += ((right_gap <= mDisplayWidth / 100) ? mDisplayWidth - x - gap / 2 : w) + gap;
      }

      rawSumHeight += rowHeight;
      rawHeight.add(rowHeight);

      scale =
          (float) keyboardHeight / (rawSumHeight + mDefaultVerticalGap * (rawHeight.size() + 1));

      Timber.d(
          "name="
              + name
              + ", yGap "
              + mDefaultVerticalGap
              + " keyboardHeight="
              + keyboardHeight
              + " rawSumHeight="
              + rawSumHeight);
      mDefaultVerticalGap = (int) Math.ceil(mDefaultVerticalGap * scale);

      Timber.d("scale=" + scale + ", yGap > " + mDefaultVerticalGap);

      int autoHeight = keyboardHeight - mDefaultVerticalGap * (rawHeight.size() + 1);

      scale = ((float) autoHeight) / rawSumHeight;

      if (autoHeightIndex < 0) {
        autoHeightIndex = rawHeight.size() + autoHeightIndex;
        if (autoHeightIndex < 0) autoHeightIndex = 0;
      } else if (autoHeightIndex >= rawHeight.size()) {
        autoHeightIndex = rawHeight.size() - 1;
      }

      newHeight = new int[rawHeight.size()];
      for (int i = 0; i < rawHeight.size(); i++) {
        if (i != autoHeightIndex) {
          int h = (int) (rawHeight.get(i) * scale);
          newHeight[i] = h;
          autoHeight -= h;
        }
      }
      if (autoHeight < 1) {
        if (rawHeight.get(autoHeight) > 0) autoHeight = 1;
      }
      newHeight[autoHeightIndex] = autoHeight;

      for (int h : rawHeight) System.out.print(" " + h);
      System.out.print('\n');

      Timber.d("scale=" + scale + " keyboardHeight: ");
      for (int h : newHeight) System.out.print(" " + h);
      System.out.print('\n');

      x = mDefaultHorizontalGap / 2;
      y = mDefaultVerticalGap;
      row = 0;
      column = 0;
      mTotalWidth = 0;
    }

    try {
      for (Map<String, Object> mk : lm) {
        int gap = mDefaultHorizontalGap;
        int w = (int) (ConfigGetter.getDouble(mk, "width", 0) * mDisplayWidth / 100);
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
          if (keyboardHeight > 0) {
            rowHeight = newHeight[row];
          } else {
            int heightK = ConfigGetter.getPixel(mk, "height", 0);
            rowHeight = (heightK > 0) ? heightK : defaultHeight;
          }
        }
        if (!mk.containsKey("click")) { // 無按鍵事件
          x += w + gap;
          continue; // 縮進
        }

        final int defaultKeyTextOffsetX =
            ConfigGetter.getPixel(
                keyboardConfig, "key_text_offset_x", config.getFloat("key_text_offset_x"));
        final int defaultKeyTextOffsetY =
            ConfigGetter.getPixel(
                keyboardConfig, "key_text_offset_y", config.getFloat("key_text_offset_y"));
        final int defaultKeySymbolOffsetX =
            ConfigGetter.getPixel(
                keyboardConfig, "key_symbol_offset_x", config.getFloat("key_symbol_offset_x"));
        final int defaultKeySymbolOffsetY =
            ConfigGetter.getPixel(
                keyboardConfig, "key_symbol_offset_y", config.getFloat("key_symbol_offset_y"));
        final int defaultKeyHintOffsetX =
            ConfigGetter.getPixel(
                keyboardConfig, "key_hint_offset_x", config.getFloat("key_hint_offset_x"));
        final int defaultKeyHintOffsetY =
            ConfigGetter.getPixel(
                keyboardConfig, "key_hint_offset_y", config.getFloat("key_hint_offset_y"));
        final int defaultKeyPressOffsetX =
            ConfigGetter.getInt(
                keyboardConfig, "key_press_offset_x", config.getInt("key_press_offset_x"));
        final int defaultKeyPressOffsetY =
            ConfigGetter.getInt(
                keyboardConfig, "key_press_offset_y", config.getInt("key_press_offset_y"));

        final Key key = new Key(context, this, mk);
        key.setKey_text_offset_x(
            ConfigGetter.getPixel(mk, "key_text_offset_x", defaultKeyTextOffsetX));
        key.setKey_text_offset_y(
            ConfigGetter.getPixel(mk, "key_text_offset_y", defaultKeyTextOffsetY));
        key.setKey_symbol_offset_x(
            ConfigGetter.getPixel(mk, "key_symbol_offset_x", defaultKeySymbolOffsetX));
        key.setKey_symbol_offset_y(
            ConfigGetter.getPixel(mk, "key_symbol_offset_y", defaultKeySymbolOffsetY));
        key.setKey_hint_offset_x(
            ConfigGetter.getPixel(mk, "key_hint_offset_x", defaultKeyHintOffsetX));
        key.setKey_hint_offset_y(
            ConfigGetter.getPixel(mk, "key_hint_offset_y", defaultKeyHintOffsetY));
        key.setKey_press_offset_x(
            ConfigGetter.getInt(mk, "key_press_offset_x", defaultKeyPressOffsetX));
        key.setKey_press_offset_y(
            ConfigGetter.getInt(mk, "key_press_offset_y", defaultKeyPressOffsetY));

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
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public Key getmShiftKey() {
    return mShiftKey;
  }

  public Key getmAltKey() {
    return mAltKey;
  }

  public Key getmMetaKey() {
    return mMetaKey;
  }

  public Key getmSymKey() {
    return mSymKey;
  }

  public Key getmCtrlKey() {
    return mCtrlKey;
  }

  public void setModiferKey(int c, Key key) {
    if (c == KeyEvent.KEYCODE_SHIFT_LEFT || c == KeyEvent.KEYCODE_SHIFT_RIGHT) this.mShiftKey = key;
    else if (c == KeyEvent.KEYCODE_CTRL_LEFT || c == KeyEvent.KEYCODE_CTRL_RIGHT)
      this.mCtrlKey = key;
    else if (c == KeyEvent.KEYCODE_META_LEFT || c == KeyEvent.KEYCODE_META_RIGHT)
      this.mMetaKey = key;
    else if (c == KeyEvent.KEYCODE_ALT_LEFT || c == KeyEvent.KEYCODE_ALT_RIGHT) this.mAltKey = key;
    else if (c == KeyEvent.KEYCODE_SYM) this.mSymKey = key;
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

  public boolean hasModifier(int modifierMask) {
    return (mModifierState & modifierMask) != 0;
  }

  public static boolean hasModifier(int modifierMask, int mModifierState) {
    return (mModifierState & modifierMask) != 0;
  }

  public boolean hasModifier() {
    return mModifierState != 0;
  }

  public int getModifer() {
    return mModifierState;
  }

  private boolean setModifier(int mask, boolean value) {
    boolean b = hasModifier(mask);
    if (b == value) return false;
    printModifierKeyState("");
    if (value) mModifierState |= mask;
    else mModifierState &= ~mask;

    printModifierKeyState("->");
    return true;
  }

  public void printModifierKeyState(String tag) {
    Timber.d(
        "\t<TrimeInput>\tkeyState() ctrl=%s, alt=%s, shift=%s, sym=%s, meta=%s\t%s",
        hasModifier(KeyEvent.META_CTRL_ON),
        hasModifier(KeyEvent.META_ALT_ON),
        hasModifier(KeyEvent.META_SHIFT_ON),
        hasModifier(KeyEvent.META_SYM_ON),
        hasModifier(KeyEvent.META_META_ON),
        tag);
  }

  public static void printModifierKeyState(int state, String tag) {
    Timber.d(
        "\t<TrimeInput>\tkeyState() ctrl=%s, alt=%s, shift=%s, sym=%s, meta=%s, state=%d\t%s",
        hasModifier(KeyEvent.META_CTRL_ON, state),
        hasModifier(KeyEvent.META_ALT_ON, state),
        hasModifier(KeyEvent.META_SHIFT_ON, state),
        hasModifier(KeyEvent.META_SYM_ON, state),
        hasModifier(KeyEvent.META_META_ON, state),
        state,
        tag);
  }

  public boolean isAlted() {
    return hasModifier(KeyEvent.META_ALT_ON);
  }

  public boolean isShifted() {
    return hasModifier(KeyEvent.META_SHIFT_ON);
  }

  // 需要优化
  public boolean needUpCase() {
    if (mShiftKey != null) if (mShiftKey.isOn()) return true;
    return hasModifier(KeyEvent.META_SHIFT_ON);
  }

  /**
   * 設定鍵盤的Shift鍵狀態
   *
   * @param on 是否保持Shift按下狀態(锁定)
   * @param shifted 是否按下Shift
   * @return Shift鍵狀態是否改變
   */
  public boolean setShifted(boolean on, boolean shifted) {
    on = on & shifted;
    if (mShiftKey != null) mShiftKey.setOn(on);
    return setModifier(KeyEvent.META_SHIFT_ON, on || shifted);
  }

  /**
   * 设定修饰键的状态
   *
   * @param on 是否锁定修饰键
   * @param keycode 修饰键on的keyevent mask code
   * @return
   */
  public boolean clikModifierKey(boolean on, int keycode) {
    boolean keyDown = !hasModifier(keycode);
    on = on & keyDown;
    if (mShiftKey != null) mShiftKey.setOn(on);

    if (keycode == KeyEvent.META_SHIFT_ON) {
      mShiftKey.setOn(on);
    } else if (keycode == KeyEvent.META_ALT_ON) {
      mAltKey.setOn(on);
    } else if (keycode == KeyEvent.META_CTRL_ON) {
      mCtrlKey.setOn(on);
    } else if (keycode == KeyEvent.META_META_ON) {
      mMetaKey.setOn(on);
    } else if (keycode == KeyEvent.KEYCODE_SYM) {
      mSymKey.setOn(on);
    }
    return setModifier(keycode, on || keyDown);
  }

  public boolean setAltOn(boolean on, boolean keyDown) {
    on = on & keyDown;
    if (mAltKey != null) mAltKey.setOn(on);
    return setModifier(KeyEvent.META_ALT_ON, on || keyDown);
  }

  public boolean setCtrlOn(boolean on, boolean keyDown) {
    on = on & keyDown;
    if (mCtrlKey != null) mCtrlKey.setOn(on);
    return setModifier(KeyEvent.META_CTRL_ON, on || keyDown);
  }

  public boolean setSymOn(boolean on, boolean keyDown) {
    on = on & keyDown;
    if (mSymKey != null) mSymKey.setOn(on);
    return setModifier(KeyEvent.META_SYM_ON, on || keyDown);
  }

  public boolean setMetaOn(boolean on, boolean keyDown) {
    on = on & keyDown;
    if (mMetaKey != null) mMetaKey.setOn(on);
    return setModifier(KeyEvent.META_META_ON, on || keyDown);
  }

  //  public boolean setFunctionOn(boolean on, boolean keyDown) {
  //    on = on & keyDown;
  //    if (mFunctionKey != null) mFunctionKey.setOn(on);
  //    return setModifier(KeyEvent.META_FUNCTION_ON, on || keyDown);
  //  }

  public boolean resetShifted() {
    if (mShiftKey != null && !mShiftKey.isOn()) return setModifier(KeyEvent.META_SHIFT_ON, false);
    return false;
  }

  public boolean resetModifer() {
    // 这里改为了一次性重置全部修饰键状态并返回TRUE刷新UI，可能有bug
    mModifierState = 0;
    return true;
  }

  public boolean refreshModifier() {
    // 这里改为了一次性重置全部修饰键状态并返回TRUE刷新UI，可能有bug
    boolean result = false;

    if (mShiftKey != null && !mShiftKey.isOn())
      result = result || setModifier(KeyEvent.META_SHIFT_ON, false);
    if (mAltKey != null && !mAltKey.isOn())
      result = result || setModifier(KeyEvent.META_ALT_ON, false);
    if (mCtrlKey != null && !mCtrlKey.isOn())
      result = result || setModifier(KeyEvent.META_CTRL_ON, false);
    if (mMetaKey != null && !mMetaKey.isOn())
      result = result || setModifier(KeyEvent.META_META_ON, false);
    if (mSymKey != null && !mSymKey.isOn())
      result = result || setModifier(KeyEvent.KEYCODE_SYM, false);
    return result;
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

  public boolean isResetAsciiMode() {
    return resetAsciiMode;
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
