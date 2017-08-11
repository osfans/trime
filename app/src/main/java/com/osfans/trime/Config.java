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

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Environment;
import android.os.SystemClock;
import android.util.Log;
import android.util.TypedValue;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 解析YAML配置文件 */
public class Config {
  public static final int INLINE_NONE = 0;
  public static final int INLINE_PREVIEW = 1;
  public static final int INLINE_COMPOSITION = 2;
  public static final int INLINE_INPUT = 3;

  public static final int CAND_POS_LEFT = 0;
  public static final int CAND_POS_RIGHT = 1;
  public static final int CAND_POS_FIXED = 2;

  // 默认的用户数据路径
  private static final String RIME = "rime";
  public static final String EXTERNAL_STORAGE_PATH =
      Environment.getExternalStorageDirectory().getPath();
  private static final String USER_DATA_DIR = new File(EXTERNAL_STORAGE_PATH, RIME).getPath();
  public static final String OPENCC_DATA_DIR = new File(USER_DATA_DIR, "opencc").getPath();

  private Map<String, Object> mStyle, mDefaultStyle;
  private static String defaultName = "trime";
  private static String defaultFile = "trime.yaml";
  private String themeName;
  private String schema_id;

  private static int BLK_SIZE = 1024;
  private static Config self = null;
  private SharedPreferences mPref;

  private Map<String, String> fallbackColors;
  private Map presetColorSchemes, presetKeyboards;

  public Config(Context context) {
    self = this;
    mPref = Function.getPref(context);
    themeName = mPref.getString("pref_selected_theme", "trime");
    init();
  }

  public String getTheme() {
    return themeName;
  }

  public static void prepareRime(Context context) {
    boolean isExist = new File(USER_DATA_DIR).exists();
    boolean isOverwrite = Function.isDiffVer(context);
    if (isOverwrite) {
      copyFileOrDir(context, RIME, true);
    } else if (isExist) {
      String path = new File(RIME, defaultFile).getPath();
      copyFileOrDir(context, path, false);
    } else {
      copyFileOrDir(context, RIME, false);
    }
    while (!new File(USER_DATA_DIR, defaultFile).exists()) {
      SystemClock.sleep(3000);
      copyFileOrDir(context, RIME, isOverwrite);
    }
    Rime.get(!isExist); //覆蓋時不強制部署
  }

  public static String[] getThemeKeys() {
    File d = new File(USER_DATA_DIR);
    FilenameFilter trimeFilter =
        new FilenameFilter() {
          @Override
          public boolean accept(File dir, String filename) {
            return filename.endsWith("trime.yaml");
          }
        };
    return d.list(trimeFilter);
  }

  public static String[] getThemeNames(String[] keys) {
    if (keys == null) return null;
    int n = keys.length;
    String[] names = new String[n];
    for (int i = 0; i < n; i++) {
      String k = keys[i].replace(".trime.yaml", "").replace(".yaml", "");
      names[i] = k;
    }
    return names;
  }

  public static boolean deployOpencc() {
    File d = new File(OPENCC_DATA_DIR);
    if (d.exists()) {
      FilenameFilter txtFilter =
          new FilenameFilter() {
            @Override
            public boolean accept(File dir, String filename) {
              return filename.endsWith(".txt");
            }
          };
      for (String txtName : d.list(txtFilter)) {
        txtName = new File(OPENCC_DATA_DIR, txtName).getPath();
        String ocdName = txtName.replace(".txt", ".ocd");
        Rime.opencc_convert_dictionary(txtName, ocdName, "text", "ocd");
      }
    }
    return true;
  }

  public static String[] list(Context context, String path) {
    AssetManager assetManager = context.getAssets();
    String assets[] = null;
    try {
      assets = assetManager.list(path);
    } catch (IOException ex) {
      Log.e("Config", "I/O Exception", ex);
    }
    return assets;
  }

  public static boolean copyFileOrDir(Context context, String path, boolean overwrite) {
    AssetManager assetManager = context.getAssets();
    String assets[] = null;
    try {
      assets = assetManager.list(path);
      if (assets.length == 0) {
        copyFile(context, path, overwrite);
      } else {
        File dir = new File(EXTERNAL_STORAGE_PATH, path);
        if (!dir.exists()) dir.mkdir();
        for (int i = 0; i < assets.length; ++i) {
          String assetPath = new File(path, assets[i]).getPath();
          copyFileOrDir(context, assetPath, overwrite);
        }
      }
    } catch (IOException ex) {
      Log.e("Config", "I/O Exception", ex);
      return false;
    }
    return true;
  }

  public static boolean copyFile(Context context, String filename, boolean overwrite) {
    AssetManager assetManager = context.getAssets();
    InputStream in = null;
    OutputStream out = null;
    try {
      in = assetManager.open(filename);
      String newFileName = new File(EXTERNAL_STORAGE_PATH, filename).getPath();
      if (new File(newFileName).exists() && !overwrite) return true;
      out = new FileOutputStream(newFileName);
      byte[] buffer = new byte[BLK_SIZE];
      int read;
      while ((read = in.read(buffer)) != -1) {
        out.write(buffer, 0, read);
      }
      in.close();
      in = null;
      out.flush();
      out.close();
      out = null;
    } catch (Exception e) {
      Log.e("Config", e.getMessage());
      return false;
    }
    return true;
  }

  private void deployConfig() {
    Rime.deploy_config_file(themeName + ".yaml", "config_version");
  }

  public void setTheme(String theme) {
    themeName = theme;
    SharedPreferences.Editor edit = mPref.edit();
    edit.putString("pref_selected_theme", themeName);
    edit.apply();
    init();
  }

  public void init() {
    String name = Rime.config_get_string(themeName, "config_version");
    if (Function.isEmpty(name)) themeName = defaultName;
    deployConfig();
    mDefaultStyle = (Map<String, Object>) Rime.config_get_map(themeName, "style");
    fallbackColors = (Map<String, String>) Rime.config_get_map(themeName, "fallback_colors");
    List androidKeys = Rime.config_get_list(themeName, "android_keys/name");
    Key.androidKeys = new ArrayList<String>(androidKeys.size());
    for (Object o : androidKeys) {
      Key.androidKeys.add(o.toString());
    }
    Key.symbolStart = Key.androidKeys.contains("A") ? Key.androidKeys.indexOf("A") : 284;
    Key.symbols = Rime.config_get_string(themeName, "android_keys/symbols");
    if (Function.isEmpty(Key.symbols)) Key.symbols = "ABCDEFGHIJKLMNOPQRSTUVWXYZ!\"$%&:<>?^_{|}~";
    Key.presetKeys = (Map<String, Map>) Rime.config_get_map(themeName, "preset_keys");
    presetColorSchemes = Rime.config_get_map(themeName, "preset_color_schemes");
    presetKeyboards = Rime.config_get_map(themeName, "preset_keyboards");
    Rime.setShowSwitches(getShowSwitches());
    reset();
  }

  public void reset() {
    schema_id = Rime.getSchemaId();
    mStyle = (Map<String, Object>) Rime.schema_get_value(schema_id, "style");
  }

  private Object _getValue(String k1, String k2) {
    Map<String, Object> m;
    if (mStyle != null && mStyle.containsKey(k1)) {
      m = (Map<String, Object>) mStyle.get(k1);
      if (m != null && m.containsKey(k2)) return m.get(k2);
    }
    if (mDefaultStyle != null && mDefaultStyle.containsKey(k1)) {
      m = (Map<String, Object>) mDefaultStyle.get(k1);
      if (m != null && m.containsKey(k2)) return m.get(k2);
    }
    return null;
  }

  private Object _getValue(String k1) {
    if (mStyle != null && mStyle.containsKey(k1)) return mStyle.get(k1);
    if (mDefaultStyle != null && mDefaultStyle.containsKey(k1)) return mDefaultStyle.get(k1);
    return null;
  }

  public Object getValue(String s) {
    String[] ss = s.split("/");
    if (ss.length == 1) return _getValue(ss[0]);
    else if (ss.length == 2) return _getValue(ss[0], ss[1]);
    return null;
  }

  public boolean hasKey(String s) {
    return getValue(s) != null;
  }

  public String getKeyboardName(String name) {
    if (name.contentEquals(".default")) {
      if (presetKeyboards.containsKey(schema_id)) name = schema_id; //匹配方案名
      else {
        if (schema_id.indexOf("_") >= 0) name = schema_id.split("_")[0];
        if (!presetKeyboards.containsKey(name)) { //匹配“_”前的方案名
          Object o = Rime.schema_get_value(schema_id, "speller/alphabet");
          name = "qwerty"; //26
          if (o != null) {
            String alphabet = o.toString();
            if (presetKeyboards.containsKey(alphabet)) name = alphabet; //匹配字母表
            else {
              if (alphabet.indexOf(",") >= 0 || alphabet.indexOf(";") >= 0) name += "_";
              if (alphabet.indexOf("0") >= 0 || alphabet.indexOf("1") >= 0) name += "0";
            }
          }
        }
      }
    }
    if (!presetKeyboards.containsKey(name)) name = "default";
    Map<String, Object> m = (Map<String, Object>) presetKeyboards.get(name);
    if (m.containsKey("import_preset")) {
      name = m.get("import_preset").toString();
    }
    return name;
  }

  public List<String> getKeyboardNames() {
    List<String> names = (List<String>) getValue("keyboards");
    List<String> keyboards = new ArrayList<String>();
    for (String s : names) {
      s = getKeyboardName(s);
      if (!keyboards.contains(s)) keyboards.add(s);
    }
    return keyboards;
  }

  public Map<String, Object> getKeyboard(String name) {
    if (!presetKeyboards.containsKey(name)) name = "default";
    return (Map<String, Object>) presetKeyboards.get(name);
  }

  public static Config get() {
    return self;
  }

  public static Config get(Context context) {
    if (self == null) {
      prepareRime(context);
      self = new Config(context);
    }
    return self;
  }

  public void destroy() {
    if (mDefaultStyle != null) mDefaultStyle.clear();
    if (mStyle != null) mStyle.clear();
    self = null;
  }

  public static int getPixel(Float f) {
    if (f == null) return 0;
    return (int)
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, f, Resources.getSystem().getDisplayMetrics());
  }

  public int getPixel(String key) {
    return getPixel(getFloat(key));
  }

  public static Integer getPixel(Map m, String k, Object defaultValue) {
    Object o = getValue(m, k, defaultValue);
    if (o instanceof Integer) return getPixel(((Integer) o).floatValue());
    if (o instanceof Float) return getPixel((Float) o);
    if (o instanceof Double) return getPixel(((Double) o).floatValue());
    return null;
  }

  public static Integer getPixel(Map m, String k) {
    return getPixel(m, k, null);
  }

  public static Integer getColor(Map m, String k) {
    Integer color = null;
    if (m.containsKey(k)) {
      Object o = m.get(k);
      if (o instanceof Integer) color = (Integer) o;
      else {
        color = get().getCurrentColor(o.toString());
      }
    }
    return color;
  }

  public static Drawable getColorDrawable(Map m, String k) {
    Integer color = null;
    if (m.containsKey(k)) {
      Object o = m.get(k);
      if (o instanceof Integer) {
        color = (Integer) o;
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        return gd;
      } else if (o instanceof String) {
        Config config = get();
        Drawable d = config.getCurrentColorDrawable(o.toString());
        if (d == null) d = config.drawableObject(o);
        return d;
      }
    }
    return null;
  }

  public static Object getValue(Map m, String k, Object o) {
    return m.containsKey(k) ? m.get(k) : o;
  }

  public static Float getFloat(Map m, String k) {
    Object o = getValue(m, k, null);
    if (o instanceof Integer) return ((Integer) o).floatValue();
    if (o instanceof Float) return ((Float) o);
    if (o instanceof Double) return ((Double) o).floatValue();
    return null;
  }

  public static double getDouble(Map m, String k, Object i) {
    Object o = getValue(m, k, i);
    if (o instanceof Integer) return ((Integer) o).doubleValue();
    else if (o instanceof Float) return ((Float) o).doubleValue();
    else if (o instanceof Double) return ((Double) o).doubleValue();
    return 0f;
  }

  public static String getString(Map m, String k) {
    if (m.containsKey(k)) {
      Object o = m.get(k);
      if (o != null) return o.toString();
    }
    return "";
  }

  public boolean getBoolean(String key) {
    Object o = getValue(key);
    return o == null ? true : (Boolean) o;
  }

  public double getDouble(String key) {
    Object o = getValue(key);
    double size = 0;
    if (o instanceof Integer) size = ((Integer) o).doubleValue();
    else if (o instanceof Float) size = ((Float) o).doubleValue();
    else if (o instanceof Double) size = ((Double) o).doubleValue();
    return size;
  }

  public float getFloat(String key) {
    return (float) getDouble(key);
  }

  public int getInt(String key) {
    return (int) getDouble(key);
  }

  public String getString(String key) {
    Object o = getValue(key);
    return (o == null) ? "" : o.toString();
  }

  private Object getColorObject(String key) {
    String scheme = getColorScheme();
    Map map = (Map<String, Object>) presetColorSchemes.get(scheme);
    if (map == null) {
      scheme = getString("color_scheme");
      map = (Map<String, Object>) presetColorSchemes.get(scheme);
      setColor(scheme);
    }
    Object o = map.get(key);
    String fallbackKey = key;
    while (o == null && fallbackColors.containsKey(fallbackKey)) {
      fallbackKey = fallbackColors.get(fallbackKey);
      o = map.get(fallbackKey);
    }
    return o;
  }

  public Integer getCurrentColor(String key) {
    Object o = getColorObject(key);
    if (o instanceof Integer) return ((Integer) o).intValue();
    if (o instanceof Float || o instanceof Double) return ((Long) o).intValue();
    return null;
  }

  public Integer getColor(String key) {
    Object o = getColorObject(key);
    if (o == null) {
      o = ((Map<String, Object>) presetColorSchemes.get("default")).get(key);
    }

    if (o instanceof Integer) return ((Integer) o).intValue();
    if (o instanceof Float || o instanceof Double || o instanceof Long)
      return ((Long) o).intValue();
    return null;
  }

  public String getColorScheme() {
    return mPref.getString("pref_selected_color_scheme", "default");
  }

  public void setColor(String color) {
    SharedPreferences.Editor edit = mPref.edit();
    edit.putString("pref_selected_color_scheme", color);
    edit.apply();
    deployConfig();
  }

  public String[] getColorKeys() {
    if (presetColorSchemes == null) return null;
    String[] keys = new String[presetColorSchemes.size()];
    presetColorSchemes.keySet().toArray(keys);
    return keys;
  }

  public String[] getColorNames(String[] keys) {
    if (keys == null) return null;
    int n = keys.length;
    String[] names = new String[n];
    for (int i = 0; i < n; i++) {
      Map<String, Object> m = (Map<String, Object>) presetColorSchemes.get(keys[i]);
      names[i] = m.get("name").toString();
    }
    return names;
  }

  public Typeface getFont(String key) {
    String name = getString(key);
    if (name != null) {
      File f = new File(USER_DATA_DIR, "fonts" + name);
      if (f.exists()) return Typeface.createFromFile(f);
    }
    return Typeface.DEFAULT;
  }

  public Drawable drawableObject(Object o) {
    if (o == null) return null;
    Integer color = null;
    if (o instanceof Integer) color = ((Integer) o).intValue();
    else if (o instanceof Float || o instanceof Double || o instanceof Long)
      color = ((Long) o).intValue();
    else if (o instanceof String) {
      String name = o.toString();
      File nameDirectory = new File(USER_DATA_DIR, "backgrounds");
      name = new File(nameDirectory, name).getPath();
      File f = new File(name);
      if (f.exists()) {
        return new BitmapDrawable(BitmapFactory.decodeFile(name));
      }
    }
    if (color != null) {
      GradientDrawable gd = new GradientDrawable();
      gd.setColor(color);
      return gd;
    }
    return null;
  }

  public Drawable getCurrentColorDrawable(String key) {
    Object o = getColorObject(key);
    return drawableObject(o);
  }

  public Drawable getColorDrawable(String key) {
    Object o = getColorObject(key);
    if (o == null) {
      o = ((Map<String, Object>) presetColorSchemes.get("default")).get(key);
    }
    return drawableObject(o);
  }

  public Drawable getDrawable(String key) {
    Object o = getValue(key);
    return drawableObject(o);
  }

  public int getInlinePreedit() {
    switch (mPref.getString("inline_preedit", "preview")) {
      case "preview":
      case "preedit":
      case "true":
        return INLINE_PREVIEW;
      case "composition":
        return INLINE_COMPOSITION;
      case "input":
        return INLINE_INPUT;
    }
    return INLINE_NONE;
  }

  public WinPos getWinPos() {
    WinPos wp = WinPos.fromString(getString("layout/position"));
    if (getInlinePreedit() == 0 && wp == WinPos.RIGHT) wp = WinPos.LEFT;
    return wp;
  }

  public boolean isShowStatusIcon() {
    return mPref.getBoolean("pref_notification_icon", false);
  }

  public boolean isDestroyOnQuit() {
    return mPref.getBoolean("pref_destroy_on_quit", false);
  }

  public int getLongTimeout() {
    int progress = mPref.getInt("longpress_timeout", 20);
    if (progress > 60) progress = 60;
    return progress * 10 + 100;
  }

  public int getRepeatInterval() {
    int progress = mPref.getInt("repeat_interval", 4);
    if (progress > 9) progress = 9;
    return progress * 10 + 10;
  }

  public boolean getShowSwitches() {
    return mPref.getBoolean("show_switches", true);
  }

  public boolean getShowPreview() {
    return mPref.getBoolean("show_preview", false);
  }

  public boolean getShowWindow() {
    return mPref.getBoolean("show_window", true);
  }

  public boolean getSoftCursor() {
    return mPref.getBoolean("soft_cursor", true);
  }
}
