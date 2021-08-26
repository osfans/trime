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
import android.graphics.drawable.BitmapDrawable;
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
import com.osfans.trime.ime.enums.WindowsPositionType;
import com.osfans.trime.ime.keyboard.Key;
import com.osfans.trime.util.AppVersionUtils;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import kotlin.jvm.Synchronized;

/** 解析 YAML 配置文件 */
public class Config {
  // 默认的用户数据路径
  private static final String RIME = "rime";
  // private static final String TAG = "Config";

  private static Config self = null;

  @Synchronized
  public static Config get(Context context) {
    if (self == null) self = new Config(context);
    return self;
  }

  private Map<?, ?> mStyle, mDefaultStyle;
  private String themeName;
  private static final String defaultName = "trime";
  private String schema_id;

  private Map<?, ?> fallbackColors;
  private Map<?, ?> presetColorSchemes, presetKeyboards;

  private static final Pattern pattern = Pattern.compile("\\s*\n\\s*");

  private String[] clipBoardCompare, clipBoardOutput, clipBoardManager;

  @NonNull
  private Preferences getPrefs() {
    return Preferences.Companion.defaultInstance();
  }

  public Config(Context context) {
    self = this;
    themeName = getPrefs().getLooks().getSelectedTheme();
    prepareRime(context);
    deployTheme(context);
    init();
    prepareCLipBoardRule();
  }

  private void prepareCLipBoardRule() {
    clipBoardCompare = getPrefs().getOther().getClipboardCompareRules().trim().split("\n");
    clipBoardOutput = getPrefs().getOther().getClipboardOutputRules().trim().split("\n");
    clipBoardManager = getPrefs().getOther().getClipboardManagerRules().trim().split(",");
  }

  public String[] getClipBoardCompare() {
    return clipBoardCompare;
  }

  public String[] getClipBoardOutput() {
    return clipBoardOutput;
  }

  public String[] getClipBoardManager() {
    return clipBoardManager;
  }

  public boolean hasClipBoardManager() {
    if (clipBoardManager.length == 2) {
      return clipBoardManager[0].length() > 0 && clipBoardManager[1].length() > 0;
    }
    return false;
  }

  public void setClipBoardManager(String str) {
    getPrefs().getOther().setClipboardManagerRules(str);
    prepareCLipBoardRule();
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

  public String getFullscreenMode() {
    return getPrefs().getKeyboard().getFullscreenMode();
  }

  public String getTheme() {
    return themeName;
  }

  public String getSharedDataDir() {
    return getPrefs().getConf().getSharedDataDir();
  }

  public String getUserDataDir() {
    return getPrefs().getConf().getUserDataDir();
  }

  public String getResDataDir(String sub) {
    String name = new File(getSharedDataDir(), sub).getPath();
    if (new File(name).exists()) return name;
    return new File(getUserDataDir(), sub).getPath();
  }

  public void prepareRime(Context context) {
    boolean isExist = new File(getSharedDataDir()).exists();
    boolean isOverwrite = AppVersionUtils.INSTANCE.isDifferentVersion(getPrefs());
    String defaultFile = "trime.yaml";
    if (isOverwrite) {
      copyFileOrDir(context, "", true);
    } else if (isExist) {
      String path = new File("", defaultFile).getPath();
      copyFileOrDir(context, path, false);
    } else {
      copyFileOrDir(context, "", false);
    }
    while (!new File(getSharedDataDir(), defaultFile).exists()) {
      SystemClock.sleep(3000);
      copyFileOrDir(context, "", isOverwrite);
    }
    // 缺失导致获取方案列表为空
    final String defaultCustom = "default.custom.yaml";
    if (!new File(getSharedDataDir(), defaultCustom).exists()) {
      try {
        new File(getSharedDataDir(), defaultCustom).createNewFile();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    Rime.get(context, !isExist); // 覆蓋時不強制部署
  }

  public static String[] getThemeKeys(Context context, boolean isUser) {
    File d = new File(isUser ? get(context).getUserDataDir() : get(context).getSharedDataDir());
    FilenameFilter trimeFilter = (dir, filename) -> filename.endsWith("trime.yaml");
    return d.list(trimeFilter);
  }

  public static String[] getThemeNames(String[] keys) {
    if (keys == null) return null;
    final int n = keys.length;
    final String[] names = new String[n];
    for (int i = 0; i < keys.length; i++) {
      final String k = keys[i].replace(".trime.yaml", "").replace(".yaml", "");
      names[i] = k;
    }
    return names;
  }

  @SuppressWarnings("UnusedReturnValue")
  public static boolean deployOpencc(Context context) {
    final String dataDir = get(context).getResDataDir("opencc");
    final File d = new File(dataDir);
    if (d.exists()) {
      FilenameFilter txtFilter = (dir, filename) -> filename.endsWith(".txt");
      for (String txtName : Objects.requireNonNull(d.list(txtFilter))) {
        txtName = new File(dataDir, txtName).getPath();
        String ocdName = txtName.replace(".txt", ".ocd2");
        Rime.opencc_convert_dictionary(txtName, ocdName, "text", "ocd2");
      }
    }
    return true;
  }

  public static String[] list(@NonNull Context context, String path) {
    final AssetManager assetManager = context.getAssets();
    String[] assets = null;
    try {
      assets = assetManager.list(path);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return assets;
  }

  public boolean copyFileOrDir(@NonNull Context context, String path, boolean overwrite) {
    final AssetManager assetManager = context.getAssets();
    try {
      final String assetPath = new File(RIME, path).getPath();
      final String[] assets = assetManager.list(assetPath);
      if (assets.length == 0) {
        // Files
        copyFile(context, path, overwrite);
      } else {
        // Dirs
        final File dir = new File(getSharedDataDir(), path);
        if (!dir.exists()) // noinspection ResultOfMethodCallIgnored
        dir.mkdir();
        for (String asset : assets) {
          final String subPath = new File(path, asset).getPath();
          copyFileOrDir(context, subPath, overwrite);
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
    return true;
  }

  private boolean copyFile(Context context, String fileName, boolean overwrite) {
    if (fileName == null) {
      return false;
    }
    final String targetFileName = new File(getSharedDataDir(), fileName).getPath();
    if (new File(targetFileName).exists() && !overwrite) {
      return true;
    }
    final String sourceFileName = new File(RIME, fileName).getPath();
    final AssetManager assetManager = context.getAssets();
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
      return false;
    }
    return true;
  }

  private void deployTheme(Context context) {
    if (getUserDataDir().contentEquals(getSharedDataDir())) return; // 相同文件夾不部署主題
    final String[] configs = getThemeKeys(context, false);
    for (String config : configs) Rime.deploy_config_file(config, "config_version");
  }

  public void setTheme(String theme) {
    themeName = theme;
    getPrefs().getLooks().setSelectedTheme(themeName);
    init();
  }

  private void init() {
    try {
      Rime.deploy_config_file(themeName + ".yaml", "config_version");
      Map<String, Object> m = Rime.config_get_map(themeName, "");
      if (m == null) {
        themeName = defaultName;
        m = Rime.config_get_map(themeName, "");
      }
      final Map<?, ?> mk = (Map<?, ?>) m.get("android_keys");
      mDefaultStyle = (Map<?, ?>) m.get("style");
      fallbackColors = (Map<?, ?>) m.get("fallback_colors");
      Key.androidKeys = (List<String>) mk.get("name");
      Key.setSymbolStart(Key.androidKeys.contains("A") ? Key.androidKeys.indexOf("A") : 284);
      Key.setSymbols((String) mk.get("symbols"));
      if (TextUtils.isEmpty(Key.getSymbols()))
        Key.setSymbols("ABCDEFGHIJKLMNOPQRSTUVWXYZ!\"$%&:<>?^_{|}~");
      Key.presetKeys = (Map<String, Map<?, ?>>) m.get("preset_keys");
      presetColorSchemes = (Map<?, ?>) m.get("preset_color_schemes");
      presetKeyboards = (Map<?, ?>) m.get("preset_keyboards");
      Rime.setShowSwitches(getPrefs().getKeyboard().getSwitchesEnabled());
      Rime.setShowSwitchArrow(getPrefs().getKeyboard().getSwitchArrowEnabled());
      reset();
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

  public Map<?, ?> getKeyboard(String name) {
    if (!presetKeyboards.containsKey(name)) name = "default";
    return (Map<?, ?>) presetKeyboards.get(name);
  }

  public void destroy() {
    if (mDefaultStyle != null) mDefaultStyle.clear();
    if (mStyle != null) mStyle.clear();
    self = null;
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

  public static Integer getColor(Context context, Map<?, ?> m, String k) {
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
      o =
          ((Map<?, ?>) Objects.requireNonNull(presetColorSchemes.get(getColorSchemeName())))
              .get(key);
    }
    if (o == null) return null;
    return parseColor(o.toString());
  }

  public Integer getColor(String key, Integer defaultValue) {
    Object o = getColorObject(key);
    if (o == null) {
      o =
          ((Map<?, ?>) Objects.requireNonNull(presetColorSchemes.get(getColorSchemeName())))
              .get(key);
    }
    if (o == null) return defaultValue;
    return parseColor(o.toString());
  }

  @Nullable
  public static Drawable getColorDrawable(Context context, @NonNull Map<?, ?> m, String k) {
    if (m.containsKey(k)) {
      final Object o = m.get(k);
      assert o != null;
      final String s = o.toString();
      @Nullable final Integer color = parseColor(s);
      if (color != null) {
        final GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        return gd;
      } else {
        final Config config = get(context);
        Drawable d = config.getCurrentColorDrawable(s);
        if (d == null) d = config.drawableObject(o);
        return d;
      }
    }
    return null;
  }

  public static Object getValue(Map<?, ?> m, String k, Object o) {
    return m.containsKey(k) ? m.get(k) : o;
  }

  public static Integer getInt(Map<?, ?> m, String k, Object s) {
    final Object o = getValue(m, k, s);
    if (o == null) return null;
    return Long.decode(o.toString()).intValue();
  }

  public static Float getFloat(Map<?, ?> m, String k) {
    final Object o = getValue(m, k, null);
    if (o == null) return null;
    return Float.valueOf(o.toString());
  }

  public static Double getDouble(Map<?, ?> m, String k, Object s) {
    final Object o = getValue(m, k, s);
    if (o == null) return null;
    return Double.valueOf(o.toString());
  }

  public static String getString(Map<?, ?> m, String k, Object s) {
    final Object o = getValue(m, k, s);
    if (o == null) return "";
    return o.toString();
  }

  public static String getString(Map<?, ?> m, String k) {
    return getString(m, k, "");
  }

  public static Boolean getBoolean(Map<?, ?> m, String k, Object s) {
    final Object o = getValue(m, k, s);
    if (o == null) return null;
    return Boolean.valueOf(o.toString());
  }

  public static Boolean getBoolean(Map<?, ?> m, String k) {
    return getBoolean(m, k, true);
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

  private Object getColorObject(String key) {
    String scheme = getColorSchemeName();
    final Map<?, ?> map = (Map<?, ?>) presetColorSchemes.get(scheme);
    if (map == null) return null;
    getPrefs().getLooks().setSelectedColor(scheme);
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
   * @date 8/13/21
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
      final File f = new File(getResDataDir("fonts"), name);
      if (f.exists()) return Typeface.createFromFile(f);
    }
    return Typeface.DEFAULT;
  }

  private Drawable drawableObject(Object o) {
    if (o == null) return null;
    String name = o.toString();
    final Integer color = parseColor(name);
    if (color != null) {
      final GradientDrawable gd = new GradientDrawable();
      gd.setColor(color);
      return gd;
    } else {
      final String nameDirectory = getResDataDir("backgrounds");
      name = new File(nameDirectory, name).getPath();
      final File f = new File(name);
      if (f.exists()) {
        if (name.contains(".9.png")) {
          final Bitmap bitmap = BitmapFactory.decodeFile(name);
          final byte[] chunk = bitmap.getNinePatchChunk();
          // 如果 .9.png 没有经过第一步，那么 chunk 就是 null, 只能按照普通方式加载
          if (NinePatch.isNinePatchChunk(chunk))
            return new NinePatchDrawable(bitmap, chunk, new Rect(), null);
        }
        return new BitmapDrawable(BitmapFactory.decodeFile(name));
      }
    }
    return null;
  }

  private Drawable getCurrentColorDrawable(String key) {
    final Object o = getColorObject(key);
    return drawableObject(o);
  }

  public Drawable getColorDrawable(String key) {
    Object o = getColorObject(key);
    if (o == null) {
      o =
          ((Map<?, ?>) Objects.requireNonNull(presetColorSchemes.get(getColorSchemeName())))
              .get(key);
    }
    return drawableObject(o);
  }

  public Drawable getDrawable(String key) {
    final Object o = getValue(key);
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
}
