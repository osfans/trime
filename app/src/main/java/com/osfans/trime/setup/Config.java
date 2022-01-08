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

package com.osfans.trime.setup;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.NinePatch;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.NinePatchDrawable;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.osfans.trime.Rime;
import com.osfans.trime.ime.core.Preferences;
import com.osfans.trime.ime.enums.SymbolKeyboardType;
import com.osfans.trime.ime.enums.WindowsPositionType;
import com.osfans.trime.ime.keyboard.Key;
import com.osfans.trime.ime.keyboard.Sound;
import com.osfans.trime.ime.symbol.TabManager;
import com.osfans.trime.util.AppVersionUtils;
import com.osfans.trime.util.DataUtils;
import com.osfans.trime.util.YamlUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import kotlin.jvm.Synchronized;
import timber.log.Timber;

/** 解析 YAML 配置文件 */
public class Config {
  // 默认的用户数据路径
  private static final String RIME = "rime";
  // private static final String TAG = "Config";

  private static Config self = null;
  private static AssetManager assetManager = null;

  private final String sharedDataDir = DataUtils.getSharedDataDir();
  private final String userDataDir = DataUtils.getUserDataDir();

  @Synchronized
  public static Config get(Context context) {
    if (self == null) self = new Config(context);
    return self;
  }

  private Map<?, ?> mStyle, mDefaultStyle;
  private String themeName, soundPackageName, currentSound;
  private static final String defaultName = "trime";
  private String schema_id, colorID;

  private Map<?, ?> fallbackColors;
  private Map<String, ?> presetColorSchemes, presetKeyboards;
  private Map<String, ?> liquidKeyboard;

  private static final Pattern pattern = Pattern.compile("\\s*\n\\s*");

  private String[] clipBoardCompare, clipBoardOutput, draftOutput;

  @NonNull
  private Preferences getPrefs() {
    return Preferences.Companion.defaultInstance();
  }

  public Config(@NonNull Context context) {
    self = this;
    assetManager = context.getAssets();
    themeName = getPrefs().getLooks().getSelectedTheme();
    soundPackageName = getPrefs().getKeyboard().getSoundPackage();
    prepareRime(context);
    deployTheme();
    init();
    setSoundFromColor();
    clipBoardCompare = getPrefs().getOther().getClipboardCompareRules().trim().split("\n");
    clipBoardOutput = getPrefs().getOther().getClipboardOutputRules().trim().split("\n");
    draftOutput = getPrefs().getOther().getDraftOutputRules().trim().split("\n");
  }

  public String[] getClipBoardCompare() {
    return clipBoardCompare;
  }

  public String[] getClipBoardOutput() {
    return clipBoardOutput;
  }

  public String[] getDraftOutput() {
    return draftOutput;
  }

  public int getClipboardLimit() {
    return Integer.parseInt(getPrefs().getOther().getClipboardLimit());
  }

  public int getDraftLimit() {
    return Integer.parseInt(getPrefs().getOther().getDraftLimit());
  }

  public void setClipBoardCompare(String str) {
    String s = pattern.matcher(str).replaceAll("\n").trim();
    clipBoardCompare = s.split("\n");

    getPrefs().getOther().setClipboardCompareRules(s);
  }

  public void setClipBoardOutput(String str) {
    String s = pattern.matcher(str).replaceAll("\n").trim();
    clipBoardOutput = s.split("\n");

    getPrefs().getOther().setClipboardOutputRules(s);
  }

  public void setDraftOutput(String str) {
    String s = pattern.matcher(str).replaceAll("\n").trim();
    draftOutput = s.split("\n");

    getPrefs().getOther().setDraftOutputRules(s);
  }

  public String getTheme() {
    return themeName;
  }

  public String getSoundPackage() {
    return soundPackageName;
  }

  public void prepareRime(Context context) {
    boolean isExist = new File(sharedDataDir).exists();
    boolean isOverwrite = AppVersionUtils.INSTANCE.isDifferentVersion(getPrefs());
    String defaultFile = "trime.yaml";
    if (isOverwrite) {
      copyFileOrDir("", true);
    } else if (isExist) {
      String path = new File("", defaultFile).getPath();
      copyFileOrDir(path, false);
    } else {
      copyFileOrDir("", false);
    }
    while (!new File(sharedDataDir, defaultFile).exists()) {
      SystemClock.sleep(3000);
      copyFileOrDir("", isOverwrite);
    }
    // 缺失导致获取方案列表为空
    final String defaultCustom = "default.custom.yaml";
    if (!new File(sharedDataDir, defaultCustom).exists()) {
      try {
        new File(sharedDataDir, defaultCustom).createNewFile();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    Rime.get(context, !isExist); // 覆蓋時不強制部署
  }

  public static String[] getThemeKeys(boolean isUser) {
    File d = new File(isUser ? DataUtils.getUserDataDir() : DataUtils.getSharedDataDir());
    FilenameFilter trimeFilter = (dir, filename) -> filename.endsWith("trime.yaml");
    String[] list = d.list(trimeFilter);
    if (list != null) return list;
    return new String[] {};
  }

  public static String[] getSoundPackages() {
    File d = new File(DataUtils.getUserDataDir(), "sound");
    FilenameFilter trimeFilter = (dir, filename) -> filename.endsWith(".sound.yaml");
    String[] list = d.list(trimeFilter);
    if (list != null) return list;
    return new String[] {};
  }

  public static String[] getYamlFileNames(String[] keys) {
    if (keys == null) return null;
    final int n = keys.length;
    final String[] names = new String[n];
    for (int i = 0; i < keys.length; i++) {
      final String k =
          keys[i].replace(".trime.yaml", "").replace(".sound.yaml", "").replace(".yaml", "");
      names[i] = k;
    }
    return names;
  }

  @SuppressWarnings("UnusedReturnValue")
  public static boolean deployOpencc() {
    final String dataDir = DataUtils.getAssetsDir("opencc");
    final File d = new File(dataDir);
    if (d.exists()) {
      final FilenameFilter txtFilter = (dir, filename) -> filename.endsWith(".txt");
      for (String txtName : Objects.requireNonNull(d.list(txtFilter))) {
        txtName = new File(dataDir, txtName).getPath();
        String ocdName = txtName.replace(".txt", ".ocd2");
        Rime.opencc_convert_dictionary(txtName, ocdName, "text", "ocd2");
      }
    }
    return true;
  }

  public boolean copyFileOrDir(String path, boolean overwrite) {
    try {
      final String assetPath = new File(RIME, path).getPath();
      final String[] assets = assetManager.list(assetPath);
      if (assets.length == 0) {
        // Files
        copyFile(path, overwrite);
      } else {
        // Dirs
        final File dir = new File(sharedDataDir, path);
        if (!dir.exists()) // noinspection ResultOfMethodCallIgnored
        dir.mkdir();
        for (String asset : assets) {
          final String subPath = new File(path, asset).getPath();
          copyFileOrDir(subPath, overwrite);
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
    return true;
  }

  private void copyFile(String fileName, boolean overwrite) {
    if (fileName == null) return;

    final String targetFileName = new File(sharedDataDir, fileName).getPath();
    if (new File(targetFileName).exists() && !overwrite) return;
    final String sourceFileName = new File(RIME, fileName).getPath();
    try (InputStream in = assetManager.open(sourceFileName);
        final FileOutputStream out = new FileOutputStream(targetFileName)) {
      final byte[] buffer = new byte[1024];
      int read;
      while ((read = in.read(buffer)) != -1) {
        out.write(buffer, 0, read);
      }
      out.flush();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void deployTheme() {
    if (userDataDir.contentEquals(sharedDataDir)) return; // 相同文件夾不部署主題
    final String[] configs = getThemeKeys(false);
    for (String config : configs) Rime.deploy_config_file(config, "config_version");
  }

  public void setTheme(String theme) {
    themeName = theme;
    getPrefs().getLooks().setSelectedTheme(themeName);
    init();
  }

  // 设置音效包
  public void setSoundPackage(String name) {
    soundPackageName = name;
    String path =
        DataUtils.getUserDataDir()
            + File.separator
            + "sound"
            + File.separator
            + name
            + ".sound.yaml";
    File file = new File(path);
    if (file.exists()) {
      applySoundPackage(file, name);
    }
    getPrefs().getKeyboard().setSoundPackage(soundPackageName);
  }

  // 应用音效包
  private void applySoundPackage(File file, String name) {
    // copy soundpackage yaml file from sound folder to build folder
    try {
      InputStream in = new FileInputStream(file);
      OutputStream out =
          new FileOutputStream(
              DataUtils.getUserDataDir()
                  + File.separator
                  + "build"
                  + File.separator
                  + name
                  + ".sound.yaml");

      byte[] buffer = new byte[1024];
      int len;
      while ((len = in.read(buffer)) > 0) {
        out.write(buffer, 0, len);
      }

      Timber.i("applySoundPackage = " + name);
    } catch (Exception e) {
      e.printStackTrace();
    }
    Sound.get(name);
    currentSound = name;
  }

  // 配色指定音效时自动切换音效效果（不会自动修改设置）。
  public void setSoundFromColor() {
    final Map<String, ?> m = (Map<String, ?>) presetColorSchemes.get(colorID);
    if (m.containsKey("sound")) {
      String sound = (String) m.get("sound");
      if (sound != currentSound) {
        String path =
            DataUtils.getUserDataDir()
                + File.separator
                + "sound"
                + File.separator
                + sound
                + ".sound.yaml";
        File file = new File(path);
        if (file.exists()) {
          applySoundPackage(file, sound);
          return;
        }
      }
    }

    if (currentSound != soundPackageName) {
      setSoundPackage(soundPackageName);
    }
  }

  private void init() {
    Timber.d("init() themeName=%s schema_id=%s", themeName, schema_id);
    try {
      Rime.deploy_config_file(themeName + ".yaml", "config_version");
      Map<String, ?> m = YamlUtils.INSTANCE.loadMap(themeName, "");
      if (m == null) {
        themeName = defaultName;
        m = YamlUtils.INSTANCE.loadMap(themeName, "");
      }
      final Map<?, ?> mk = (Map<?, ?>) m.get("android_keys");
      mDefaultStyle = (Map<?, ?>) m.get("style");
      fallbackColors = (Map<?, ?>) m.get("fallback_colors");
      Key.androidKeys = (List<String>) mk.get("name");
      Key.setSymbolStart(Key.androidKeys.contains("A") ? Key.androidKeys.indexOf("A") : 284);
      Key.setSymbols((String) mk.get("symbols"));
      if (TextUtils.isEmpty(Key.getSymbols()))
        Key.setSymbols("ABCDEFGHIJKLMNOPQRSTUVWXYZ!\"$%&:<>?^_{|}~");
      Key.presetKeys = (Map<String, Map<String, ?>>) m.get("preset_keys");
      presetColorSchemes = (Map<String, ?>) m.get("preset_color_schemes");
      presetKeyboards = (Map<String, ?>) m.get("preset_keyboards");
      liquidKeyboard = (Map<String, ?>) m.get("liquid_keyboard");
      initLiquidKeyboard();
      Rime.setShowSwitches(getPrefs().getKeyboard().getSwitchesEnabled());
      Rime.setShowSwitchArrow(getPrefs().getKeyboard().getSwitchArrowEnabled());
      reset();
      initCurrentColors();
      Timber.d("init() finins");
    } catch (Exception e) {
      e.printStackTrace();
      setTheme(defaultName);
    }
  }

  public void reset() {
    schema_id = Rime.getSchemaId();
    if (schema_id != null) mStyle = (Map<?, ?>) Rime.schema_get_value(schema_id, "style");
  }

  @Nullable
  private Object _getValue(String k1, String k2) {
    Map<?, ?> m;
    if (mStyle != null && mStyle.containsKey(k1)) {
      m = (Map<?, ?>) mStyle.get(k1);
      if (m != null && m.containsKey(k2)) return m.get(k2);
    }
    if (mDefaultStyle != null && mDefaultStyle.containsKey(k1)) {
      m = (Map<?, ?>) mDefaultStyle.get(k1);
      if (m != null && m.containsKey(k2)) return m.get(k2);
    }
    return null;
  }

  private Object _getValue(String k1, String k2, Object defaultValue) {
    Map<?, ?> m;
    if (mStyle != null && mStyle.containsKey(k1)) {
      m = (Map<?, ?>) mStyle.get(k1);
      if (m != null && m.containsKey(k2)) return m.get(k2);
    }
    if (mDefaultStyle != null && mDefaultStyle.containsKey(k1)) {
      m = (Map<?, ?>) mDefaultStyle.get(k1);
      if (m != null && m.containsKey(k2)) return m.get(k2);
    }
    return defaultValue;
  }

  @Nullable
  private Object _getValue(String k1) {
    if (mStyle != null && mStyle.containsKey(k1)) return mStyle.get(k1);
    if (mDefaultStyle != null && mDefaultStyle.containsKey(k1)) return mDefaultStyle.get(k1);
    return null;
  }

  private Object _getValue(String k1, Object defaultValue) {
    if (mStyle != null && mStyle.containsKey(k1)) return mStyle.get(k1);
    if (mDefaultStyle != null && mDefaultStyle.containsKey(k1)) return mDefaultStyle.get(k1);
    return defaultValue;
  }

  public Object getValue(@NonNull String s) {
    final String[] ss = s.split("/");
    if (ss.length == 1) return _getValue(ss[0]);
    else if (ss.length == 2) return _getValue(ss[0], ss[1]);
    return null;
  }

  public Object getValue(@NonNull String s, Object defaultValue) {
    final String[] ss = s.split("/");
    if (ss.length == 1) return _getValue(ss[0], defaultValue);
    else if (ss.length == 2) return _getValue(ss[0], ss[1], defaultValue);
    return null;
  }

  public boolean hasKey(String s) {
    return getValue(s) != null;
  }

  private String getKeyboardName(@NonNull String name) {
    if (name.contentEquals(".default")) {
      if (presetKeyboards.containsKey(schema_id)) name = schema_id; // 匹配方案名
      else {
        if (schema_id.contains("_")) name = schema_id.split("_")[0];
        if (!presetKeyboards.containsKey(name)) { // 匹配“_”前的方案名
          Object o = Rime.schema_get_value(schema_id, "speller/alphabet");
          name = "qwerty"; // 26
          if (o != null) {
            final String alphabet = o.toString();
            if (presetKeyboards.containsKey(alphabet)) name = alphabet; // 匹配字母表
            else {
              if (alphabet.contains(",") || alphabet.contains(";")) name += "_";
              if (alphabet.contains("0") || alphabet.contains("1")) name += "0";
            }
          }
        }
      }
    }
    if (!presetKeyboards.containsKey(name)) name = "default";
    @Nullable final Map<?, ?> m = (Map<?, ?>) presetKeyboards.get(name);
    assert m != null;
    if (m.containsKey("import_preset")) {
      name = Objects.requireNonNull(m.get("import_preset")).toString();
    }
    return name;
  }

  public List<String> getKeyboardNames() {
    final List<?> names = (List<?>) getValue("keyboards");
    final List<String> keyboards = new ArrayList<>();
    for (Object s : names) {
      s = getKeyboardName((String) s);
      if (!keyboards.contains(s)) keyboards.add((String) s);
    }
    return keyboards;
  }

  public void initLiquidKeyboard() {
    TabManager.clear();
    if (liquidKeyboard == null) return;
    final List<?> names = (List<?>) liquidKeyboard.get("keyboards");
    if (names == null) return;
    for (Object s : names) {
      String name = (String) s;
      if (liquidKeyboard.containsKey(name)) {
        Map<?, ?> keyboard = (Map<?, ?>) liquidKeyboard.get(name);
        if (keyboard != null) {
          if (keyboard.containsKey("name")) {
            name = (String) keyboard.get("name");
          }
          if (keyboard.containsKey("type")) {
            TabManager.get()
                .addTab(
                    name,
                    SymbolKeyboardType.Companion.fromObject(keyboard.get("type")),
                    keyboard.get("keys"));
          }
        }
      }
    }
  }

  public Map<String, ?> getKeyboard(String name) {
    if (!presetKeyboards.containsKey(name)) name = "default";
    return (Map<String, ?>) presetKeyboards.get(name);
  }

  public Map<String, ?> getLiquidKeyboard() {
    return liquidKeyboard;
  }

  public void destroy() {
    if (mDefaultStyle != null) mDefaultStyle.clear();
    if (mStyle != null) mStyle.clear();
    self = null;
  }

  private int[] keyboardPadding;

  public int[] getKeyboardPadding() {
    return keyboardPadding;
  }

  public int[] getKeyboardPadding(boolean land_mode) {
    Timber.i("update KeyboardPadding: getKeyboardPadding(boolean land_mode) ");
    return getKeyboardPadding(one_hand_mode, land_mode);
  }

  private int one_hand_mode;

  public int[] getKeyboardPadding(int one_hand_mode, boolean land_mode) {
    keyboardPadding = new int[3];
    this.one_hand_mode = one_hand_mode;
    if (land_mode) {
      keyboardPadding[0] = getPixel("keyboard_padding_land");
      keyboardPadding[1] = keyboardPadding[0];
      keyboardPadding[2] = getPixel("keyboard_padding_land_bottom");
    } else {
      switch (one_hand_mode) {
        case 0:
          // 普通键盘 预留，目前未实装
          keyboardPadding[0] = getPixel("keyboard_padding");
          keyboardPadding[1] = keyboardPadding[0];
          keyboardPadding[2] = getPixel("keyboard_padding_bottom");
          break;
        case 1:
          // 左手键盘
          keyboardPadding[0] = getPixel("keyboard_padding_left");
          keyboardPadding[1] = getPixel("keyboard_padding_right");
          keyboardPadding[2] = getPixel("keyboard_padding_bottom");
          break;
        case 2:
          // 右手键盘
          keyboardPadding[1] = getPixel("keyboard_padding_left");
          keyboardPadding[0] = getPixel("keyboard_padding_right");
          keyboardPadding[2] = getPixel("keyboard_padding_bottom");
          break;
      }
    }
    Timber.d(
        "update KeyboardPadding: %s %s %s one_hand_mode=%s",
        keyboardPadding[0], keyboardPadding[1], keyboardPadding[2], one_hand_mode);
    return keyboardPadding;
  }

  private static int getPixel(Float f) {
    if (f == null) return 0;
    return (int)
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, f, Resources.getSystem().getDisplayMetrics());
  }

  public int getPixel(String key) {
    return getPixel(getFloat(key));
  }

  public int getPixel(String key, int defaultValue) {
    float v = getFloat(key, Float.MAX_VALUE);
    if (v == Float.MAX_VALUE) return defaultValue;
    return getPixel(v);
  }

  public static Integer getPixel(Map<?, ?> m, String k, Object s) {
    Object o = getValue(m, k, s);
    if (o == null) return null;
    return getPixel(Float.valueOf(o.toString()));
  }

  public static Integer getPixel(Map<?, ?> m, String k) {
    return getPixel(m, k, null);
  }

  public static Integer getColor(Context context, @NonNull Map<?, ?> m, String k) {
    Integer color = null;
    if (m.containsKey(k)) {
      Object o = m.get(k);
      assert o != null;
      String s = o.toString();
      color = parseColor(s);
      if (color == null) color = get(context).getCurrentColor(s);
    }
    return color;
  }

  public Integer getColor(String key) {
    Object o = getColorObject(key);
    if (o == null) {
      o = ((Map<?, ?>) Objects.requireNonNull(presetColorSchemes.get(colorID))).get(key);
    }
    if (o == null) return null;
    return parseColor(o.toString());
  }

  // API 2.0
  public Drawable getDrawable(@NonNull Map<?, ?> m, String k) {
    if (m.containsKey(k)) {
      final Object o = m.get(k);
      //      Timber.d("getColorDrawable()" + k + " " + o);
      return drawableObject(o);
    }
    return null;
  }

  public static Object getValue(@NonNull Map<?, ?> m, String k, Object o) {
    return m.containsKey(k) ? m.get(k) : o;
  }

  @NonNull
  public static String getString(Map<?, ?> m, String k, Object s) {
    final Object o = getValue(m, k, s);
    if (o == null) return "";
    return o.toString();
  }

  @NonNull
  public static String getString(Map<?, ?> m, String k) {
    return getString(m, k, "");
  }

  public boolean getBoolean(String key) {
    final Object o = getValue(key);
    if (o == null) return true;
    return Boolean.parseBoolean(o.toString());
  }

  public double getDouble(String key) {
    final Object o = getValue(key);
    if (o == null) return 0d;
    return Double.parseDouble(o.toString());
  }

  public float getFloat(String key) {
    final Object o = getValue(key);
    if (o == null) return 0f;
    return Float.parseFloat(o.toString());
  }

  public float getFloat(String key, float defaultValue) {
    final Object o = getValue(key, defaultValue);
    if (o == null) return defaultValue;
    return Float.parseFloat(o.toString());
  }

  public int getInt(String key) {
    final Object o = getValue(key);
    if (o == null) return 0;
    return Long.decode(o.toString()).intValue();
  }

  public String getString(String key) {
    final Object o = getValue(key);
    if (o == null) return "";
    return o.toString();
  }

  //  获取当前配色方案的key的value，或者从fallback获取值。
  @Nullable
  private Object getColorObject(String key) {
    final Map<?, ?> map = (Map<?, ?>) presetColorSchemes.get(colorID);
    if (map == null) return null;
    getPrefs().getLooks().setSelectedColor(colorID);
    Object o = map.get(key);
    String fallbackKey = key;
    while (o == null && fallbackColors.containsKey(fallbackKey)) {
      fallbackKey = (String) fallbackColors.get(fallbackKey);
      o = map.get(fallbackKey);
    }
    return o;
  }

  /**
   * 获取配色方案名<br>
   * 优先级：设置>color_scheme>default <br>
   * 避免直接读取 default
   *
   * @return java.lang.String 首个已配置的主题方案名
   */
  private String getColorSchemeName() {
    String scheme = getPrefs().getLooks().getSelectedColor();
    if (!presetColorSchemes.containsKey(scheme)) scheme = getString("color_scheme"); // 主題中指定的配色
    if (!presetColorSchemes.containsKey(scheme)) scheme = "default"; // 主題中的default配色
    return scheme;
  }

  private static Integer parseColor(String s) {
    Integer color = null;
    if (s.contains(".")) return color; // picture name
    try {
      s = s.toLowerCase(Locale.getDefault());
      if (s.startsWith("0x")) {
        if (s.length() == 3 || s.length() == 4)
          s = String.format("#%02x000000", Long.decode(s.substring(2))); // 0xAA
        else if (s.length() < 8) s = String.format("#%06x", Long.decode(s.substring(2)));
        else if (s.length() == 9) s = "#0" + s.substring(2);
      }
      color = Color.parseColor(s.replace("0x", "#"));
    } catch (Exception e) {
      // Log.e(TAG, "unknown color " + s);
    }
    return color;
  }

  public Integer getCurrentColor(String key) {
    Object o = getColorObject(key);
    if (o == null) return null;
    return parseColor(o.toString());
  }

  public String[] getColorKeys() {
    if (presetColorSchemes == null) return null;
    final Object[] keys = new String[presetColorSchemes.size()];
    presetColorSchemes.keySet().toArray(keys);
    return (String[]) keys;
  }

  @Nullable
  public String[] getColorNames(String[] keys) {
    if (keys == null) return null;
    final int n = keys.length;
    final String[] names = new String[n];
    for (int i = 0; i < n; i++) {
      final Map<?, ?> m = (Map<?, ?>) presetColorSchemes.get(keys[i]);
      assert m != null;
      names[i] = Objects.requireNonNull(m.get("name")).toString();
    }
    return names;
  }

  public Typeface getFont(String key) {
    final String name = getString(key);
    if (name != null) {
      final File f = new File(DataUtils.getAssetsDir("fonts"), name);
      if (f.exists()) return Typeface.createFromFile(f);
    }
    return Typeface.DEFAULT;
  }

  //  返回drawable。参数可以是颜色或者图片。如果参数缺失，返回null
  private Drawable drawableObject(Object o) {
    if (o == null) return null;
    String name = o.toString();
    Integer color = parseColor(name);
    if (color == null) {
      if (curcentColors.containsKey(name)) {
        o = curcentColors.get(name);
        if (o instanceof Integer) color = (Integer) o;
      }
    }
    if (color != null) {
      final GradientDrawable gd = new GradientDrawable();
      gd.setColor(color);
      return gd;
    }
    return drawableBitmapObject(name);
  }

  //  返回图片的drawable。如果参数缺失、非图片，返回null
  private Drawable drawableBitmapObject(Object o) {
    if (o == null) return null;
    if (o instanceof String) {
      String name = (String) o;
      String nameDirectory =
          DataUtils.getAssetsDir("backgrounds" + File.separator + backgroundFolder);
      File f = new File(nameDirectory, name);

      if (!f.exists()) {
        nameDirectory = DataUtils.getAssetsDir("backgrounds");
        f = new File(nameDirectory, name);
      }

      if (!f.exists()) {
        if (curcentColors.containsKey(name)) {
          o = curcentColors.get(name);
          if (o instanceof String) f = new File((String) o);
        }
      }

      if (f.exists()) {
        name = f.getPath();
        if (name.contains(".9.png")) {
          final Bitmap bitmap = BitmapFactory.decodeFile(name);
          final byte[] chunk = bitmap.getNinePatchChunk();
          // 如果 .9.png 没有经过第一步，那么 chunk 就是 null, 只能按照普通方式加载
          if (NinePatch.isNinePatchChunk(chunk))
            return new NinePatchDrawable(bitmap, chunk, new Rect(), null);
        }
        return Drawable.createFromPath(name);
      }
    }
    return null;
  }

  public Drawable getColorDrawable(String key) {
    final Object o = getColorObject(key);
    return drawableObject(o);
  }

  public WindowsPositionType getWinPos() {
    return WindowsPositionType.Companion.fromString(getString("layout/position"));
  }

  public int getLongTimeout() {
    int progress = getPrefs().getKeyboard().getLongPressTimeout();
    if (progress > 60) progress = 60;
    return progress * 10 + 100;
  }

  public int getRepeatInterval() {
    int progress = getPrefs().getKeyboard().getRepeatInterval();
    if (progress > 9) progress = 9;
    return progress * 10 + 10;
  }

  public int getLiquidPixel(String key) {
    if (liquidKeyboard != null) {
      if (liquidKeyboard.containsKey(key)) {
        return YamlUtils.INSTANCE.getPixel(liquidKeyboard, key, 0);
      }
    }
    return getPixel(key);
  }

  public Integer getLiquidColor(String key) {
    if (liquidKeyboard != null) {
      if (liquidKeyboard.containsKey(key)) {
        Integer value = parseColor((String) Objects.requireNonNull(liquidKeyboard.get(key)));
        if (value != null) return value;
      }
    }
    return getColor(key);
  }

  // 获取当前色彩 Config 2.0
  public Integer getCurrentColor_(String key) {
    Object o = curcentColors.get(key);
    return (Integer) o;
  }

  // 获取当前背景图路径 Config 2.0
  public String getCurrentImage(String key) {
    Object o = curcentColors.get(key);
    if (o instanceof String) return (String) o;
    return "";
  }

  //  返回drawable。  Config 2.0
  //  参数可以是颜色或者图片。如果参数缺失，返回null
  public Drawable getDrawable_(String key) {
    if (key == null) return null;
    Object o = curcentColors.get(key);
    if (o instanceof Integer) {
      Integer color = (Integer) o;
      final GradientDrawable gd = new GradientDrawable();
      gd.setColor(color);
      return gd;
    } else if (o instanceof String) return getDrawableBitmap_(key);

    return null;
  }

  //  返回图片或背景的drawable,支持null参数。 Config 2.0
  public Drawable getDrawable(
      String key, String borderKey, String borderColorKey, String roundCornerKey, String alphaKey) {
    if (key == null) return null;
    Drawable drawable = getDrawableBitmap_(key);
    if (drawable != null) {
      if (alphaKey != null) {
        if (hasKey(alphaKey)) {
          int alpha = getInt("layout/alpha");
          if (alpha <= 0) alpha = 0;
          else if (alpha >= 255) alpha = 255;
          drawable.setAlpha(alpha);
        }
      }
      return drawable;
    }

    GradientDrawable gd = new GradientDrawable();
    Object o = curcentColors.get(key);
    if (!(o instanceof Integer)) return null;
    gd.setColor((int) o);

    if (roundCornerKey != null) gd.setCornerRadius(getFloat(roundCornerKey));

    if (borderColorKey != null && borderKey != null) {
      int border = getPixel(borderKey);
      Object borderColor = curcentColors.get(borderColorKey);
      if (borderColor instanceof Integer && border > 0) {
        gd.setStroke(border, getCurrentColor_(borderColorKey));
      }
    }

    if (alphaKey != null) {
      if (hasKey(alphaKey)) {
        int alpha = getInt("layout/alpha");
        if (alpha <= 0) alpha = 0;
        else if (alpha >= 255) alpha = 255;
        gd.setAlpha(alpha);
      }
    }

    return gd;
  }

  //  返回图片的drawable。 Config 2.0
  //  如果参数缺失、非图片，返回null. 在genCurrentColors()中已经验证存在文件，因此不需要重新验证。
  public Drawable getDrawableBitmap_(String key) {
    if (key == null) return null;

    Object o = curcentColors.get(key);
    if (o instanceof String) {
      String path = (String) o;
      if (path.contains(".9.png")) {
        final Bitmap bitmap = BitmapFactory.decodeFile(path);
        final byte[] chunk = bitmap.getNinePatchChunk();
        if (NinePatch.isNinePatchChunk(chunk))
          return new NinePatchDrawable(bitmap, chunk, new Rect(), null);
      }
      return Drawable.createFromPath(path);
    }
    return null;
  }

  // 遍历当前配色方案的值、fallback的值，从而获得当前方案的全部配色Map
  private final Map<String, Object> curcentColors = new HashMap<>();
  private String backgroundFolder;
  // 初始化当前配色 Config 2.0
  public void initCurrentColors() {
    curcentColors.clear();
    colorID = getColorSchemeName();
    backgroundFolder = getString("background_folder");
    Timber.d(
        "initCurrentColors() colorID=%s themeName=%s schema_id=%s", colorID, themeName, schema_id);
    final Map<?, ?> map = (Map<?, ?>) presetColorSchemes.get(colorID);
    if (map == null) {
      Timber.i("no colorID %s", colorID);
      return;
    }
    getPrefs().getLooks().setSelectedColor(colorID);

    for (Map.Entry<?, ?> entry : map.entrySet()) {
      Object value = getColorRealValue(entry.getValue());
      if (value != null) curcentColors.put(entry.getKey().toString(), value);
    }

    for (Map.Entry<?, ?> entry : fallbackColors.entrySet()) {
      String key = entry.getKey().toString();
      if (!curcentColors.containsKey(key)) {
        Object o = map.get(key);
        String fallbackKey = key;
        while (o == null && fallbackColors.containsKey(fallbackKey)) {
          fallbackKey = (String) fallbackColors.get(fallbackKey);
          o = map.get(fallbackKey);
        }
        if (o != null) {
          Object value = getColorRealValue(o);
          if (value != null) {
            curcentColors.put(key, value);
            curcentColors.put(fallbackKey, value);
          }
        }
      }
    }
  }

  // 获取参数的真实value，Config 2.0
  // 如果是色彩返回int，如果是背景图返回path string，如果处理失败返回null
  private Object getColorRealValue(Object object) {
    if (object == null) return null;
    if (object instanceof Integer) {
      return object;
    }
    String s = object.toString();
    if (!s.matches(".*[.\\\\/].*")) {
      try {
        s = s.toLowerCase(Locale.getDefault());
        if (s.startsWith("0x")) {
          if (s.length() == 3 || s.length() == 4)
            s = String.format("#%02x000000", Long.decode(s.substring(2))); // 0xAA
          else if (s.length() < 8) s = String.format("#%06x", Long.decode(s.substring(2)));
          else if (s.length() == 9) s = "#0" + s.substring(2);
        }
        if (s.matches("(0x|#)?[a-f0-9]+")) return Color.parseColor(s.replace("0x", "#"));
      } catch (Exception e) {
        Timber.e("getColorRealValue() unknown color, %s ; object %s", s, object);
        e.printStackTrace();
      }
    }

    String nameDirectory =
        DataUtils.getAssetsDir("backgrounds" + File.separator + backgroundFolder);
    File f = new File(nameDirectory, s);

    if (!f.exists()) {
      nameDirectory = DataUtils.getAssetsDir("backgrounds");
      f = new File(nameDirectory, s);
    }

    if (f.exists()) return f.getPath();
    return null;
  }
}
