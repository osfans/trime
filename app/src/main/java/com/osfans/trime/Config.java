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
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.util.Log;
import android.util.TypedValue;
import com.osfans.trime.enums.InlineModeType;
import com.osfans.trime.enums.WindowsPositionType;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 解析YAML配置文件 */
public class Config {
  // 默认的用户数据路径
  private static final String RIME = "rime";
  private static final String TAG = "Config";
  private static String userDataDir;
  private static String sharedDataDir;

  private Map<String, Object> mStyle, mDefaultStyle;
  private String themeName;
  private static String defaultName = "trime";
  private String schema_id;

  private static Config self = null;
  private SharedPreferences mPref;

  private Map<String, String> fallbackColors;
  private Map presetColorSchemes, presetKeyboards;

  public Config(Context context) {
    self = this;
    mPref = Function.getPref(context);
    userDataDir = context.getString(R.string.default_user_data_dir);
    sharedDataDir = context.getString(R.string.default_shared_data_dir);
    themeName = mPref.getString("pref_selected_theme", "trime");
    prepareRime(context);
    deployTheme();
    init();
  }

  public String getTheme() {
    return themeName;
  }

  public String getSharedDataDir() {
    return mPref.getString("shared_data_dir", sharedDataDir);
  }

  public String getUserDataDir() {
    return mPref.getString("user_data_dir", userDataDir);
  }

  public String getResDataDir(String sub) {
    String name = new File(getSharedDataDir(), sub).getPath();
    if (new File(name).exists()) return name;
    return new File(getUserDataDir(), sub).getPath();
  }

  private void prepareRime(Context context) {
    boolean isExist = new File(getSharedDataDir()).exists();
    boolean isOverwrite = Function.isDiffVer(context);
    String defaultFile = "trime.yaml";
    if (isOverwrite) {
      copyFileOrDir(context, RIME, true);
    } else if (isExist) {
      String path = new File(RIME, defaultFile).getPath();
      copyFileOrDir(context, path, false);
    } else {
      copyFileOrDir(context, RIME, false);
    }
    while (!new File(getSharedDataDir(), defaultFile).exists()) {
      SystemClock.sleep(3000);
      copyFileOrDir(context, RIME, isOverwrite);
    }
    Rime.get(!isExist); //覆蓋時不強制部署
  }

  public static String[] getThemeKeys(boolean isUser) {
    File d = new File(isUser ? get().getUserDataDir() : get().getSharedDataDir());
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
    String dataDir = get().getResDataDir("opencc");
    File d = new File(dataDir);
    if (d.exists()) {
      FilenameFilter txtFilter =
          new FilenameFilter() {
            @Override
            public boolean accept(File dir, String filename) {
              return filename.endsWith(".txt");
            }
          };
      for (String txtName : d.list(txtFilter)) {
        txtName = new File(dataDir, txtName).getPath();
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
      Log.e(TAG, "I/O Exception", ex);
    }
    return assets;
  }

  public boolean copyFileOrDir(Context context, String path, boolean overwrite) {
    AssetManager assetManager = context.getAssets();
    String assets[] = null;
    try {
      assets = assetManager.list(path);
      if (assets.length == 0) {
        copyFile(context, path, overwrite);
      } else {
        File dir = new File(getSharedDataDir(), path.length() >= 5 ? path.substring(5) : "");
        if (!dir.exists()) dir.mkdir();
        for (int i = 0; i < assets.length; ++i) {
          String assetPath = new File(path, assets[i]).getPath();
          copyFileOrDir(context, assetPath, overwrite);
        }
      }
    } catch (IOException ex) {
      Log.e(TAG, "I/O Exception", ex);
      return false;
    }
    return true;
  }

  private boolean copyFile(Context context, String filename, boolean overwrite) {
    AssetManager assetManager = context.getAssets();
    InputStream in = null;
    OutputStream out = null;
    try {
      in = assetManager.open(filename);
      String newFileName = new File(filename.endsWith(".bin") ? getUserDataDir() : getSharedDataDir(), filename.length() >= 5 ? filename.substring(5) : "").getPath();
      if (new File(newFileName).exists() && !overwrite) return true;
      out = new FileOutputStream(newFileName);
      int BLK_SIZE = 1024;
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
      Log.e(TAG, e.getMessage());
      return false;
    }
    return true;
  }

  private void deployTheme() {
    if (getUserDataDir().contentEquals(getSharedDataDir())) return; //相同文件夾不部署主題
    String[] configs = get().getThemeKeys(false);
    for (String config: configs) Rime.deploy_config_file(config, "config_version");
  }

  public void setTheme(String theme) {
    themeName = theme;
    SharedPreferences.Editor edit = mPref.edit();
    edit.putString("pref_selected_theme", themeName);
    edit.apply();
    init();
  }

  private void init() {
    Map<String, Object> m = Rime.config_get_map(themeName, "");
    if (m == null) {
      themeName = defaultName;
      m = Rime.config_get_map(themeName, "");
    }
    Map mk = (Map<String, Object>) m.get("android_keys");
    mDefaultStyle = (Map<String, Object>) m.get("style");
    fallbackColors = (Map<String, String>) m.get("fallback_colors");
    Key.androidKeys = (List<String>) mk.get("name");
    Key.setSymbolStart(Key.androidKeys.contains("A") ? Key.androidKeys.indexOf("A") : 284);
    Key.setSymbols((String) mk.get("symbols"));
    if (Function.isEmpty(Key.getSymbols()))
      Key.setSymbols("ABCDEFGHIJKLMNOPQRSTUVWXYZ!\"$%&:<>?^_{|}~");
    Key.presetKeys = (Map<String, Map>) m.get("preset_keys");
    presetColorSchemes = (Map<String, Object>) m.get("preset_color_schemes");
    presetKeyboards = (Map<String, Object>) m.get("preset_keyboards");
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

  private String getKeyboardName(String name) {
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
    if (self == null) self = new Config(context);
    return self;
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

  public static Integer getPixel(Map m, String k, Object s) {
    Object o = getValue(m, k, s);
    if (o == null) return null;
    return getPixel(Float.valueOf(o.toString()));
  }

  public static Integer getPixel(Map m, String k) {
    return getPixel(m, k, null);
  }

  public static Integer getColor(Map m, String k) {
    Integer color = null;
    if (m.containsKey(k)) {
      Object o = m.get(k);
      String s = o.toString();
      color = parseColor(s);
      if (color == null) color = get().getCurrentColor(s);
    }
    return color;
  }

  public static Drawable getColorDrawable(Map m, String k) {
    if (m.containsKey(k)) {
      Object o = m.get(k);
      String s = o.toString();
      Integer color = parseColor(s);
      if (color != null) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        return gd;
      } else {
        Config config = get();
        Drawable d = config.getCurrentColorDrawable(s);
        if (d == null) d = config.drawableObject(o);
        return d;
      }
    }
    return null;
  }

  public static Object getValue(Map m, String k, Object o) {
    return m.containsKey(k) ? m.get(k) : o;
  }

  public static Integer getInt(Map m, String k, Object s) {
    Object o = getValue(m, k, s);
    if (o == null) return null;
    return Long.decode(o.toString()).intValue();
  }

  public static Float getFloat(Map m, String k) {
    Object o = getValue(m, k, null);
    if (o == null) return null;
    return Float.valueOf(o.toString());
  }

  public static Double getDouble(Map m, String k, Object s) {
    Object o = getValue(m, k, s);
    if (o == null) return null;
    return Double.valueOf(o.toString());
  }

  public static String getString(Map m, String k, Object s) {
    Object o = getValue(m, k, s);
    if (o == null) return "";
    return o.toString();
  }

  public static String getString(Map m, String k) {
    return getString(m, k, "");
  }

  public static Boolean getBoolean(Map m, String k, Object s) {
    Object o = getValue(m, k, s);
    if (o == null) return null;
    return Boolean.valueOf(o.toString());
  }

  public static Boolean getBoolean(Map m, String k) {
    return getBoolean(m, k, true);
  }

  public boolean getBoolean(String key) {
    Object o = getValue(key);
    if (o == null) return true;
    return Boolean.valueOf(o.toString());
  }

  public double getDouble(String key) {
    Object o = getValue(key);
    if (o == null) return 0d;
    return Double.valueOf(o.toString());
  }

  public float getFloat(String key) {
    Object o = getValue(key);
    if (o == null) return 0f;
    return Float.valueOf(o.toString());
  }

  public int getInt(String key) {
    Object o = getValue(key);
    if (o == null) return 0;
    return Long.decode(o.toString()).intValue();
  }

  public String getString(String key) {
    Object o = getValue(key);
    if (o == null) return "";
    return o.toString();
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

  private static Integer parseColor(String s) {
    Integer color = null;
    if (s.contains(".")) return color; //picture name
    try {
      s = s.toLowerCase(Locale.getDefault());
      if (s.startsWith("0x")) {
        if (s.length() < 8) s = "#"+String.format("%06x", Long.decode(s.substring(2)));
        else if (s.length() == 9) s = "#0" + s.substring(2);
      }
      color = Color.parseColor(s.replace("0x", "#"));
    } catch (Exception e) {
      //Log.e(TAG, "unknown color " + s);
    }
    return color;
  }

  public Integer getCurrentColor(String key) {
    Object o = getColorObject(key);
    if (o == null) return null;
    return parseColor(o.toString());
  }

  public Integer getColor(String key) {
    Object o = getColorObject(key);
    if (o == null) {
      o = ((Map<String, Object>) presetColorSchemes.get("default")).get(key);
    }
    if (o == null) return null;
    return parseColor(o.toString());
  }

  public String getColorScheme() {
    return mPref.getString("pref_selected_color_scheme", "default");
  }

  public void setColor(String color) {
    SharedPreferences.Editor edit = mPref.edit();
    edit.putString("pref_selected_color_scheme", color);
    edit.apply();
    //deployTheme();
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
      File f = new File(getResDataDir("fonts"), name);
      if (f.exists()) return Typeface.createFromFile(f);
    }
    return Typeface.DEFAULT;
  }

  private Drawable drawableObject(Object o) {
    if (o == null) return null;
    String name = o.toString();
    Integer color = parseColor(name);
    if (color != null) {
      GradientDrawable gd = new GradientDrawable();
      gd.setColor(color);
      return gd;
    } else {
      String nameDirectory = getResDataDir("backgrounds");
      name = new File(nameDirectory, name).getPath();
      File f = new File(name);
      if (f.exists()) {
        return new BitmapDrawable(BitmapFactory.decodeFile(name));
      }
    }
    return null;
  }

  private Drawable getCurrentColorDrawable(String key) {
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

  public InlineModeType getInlinePreedit() {
    switch (mPref.getString("inline_preedit", "preview")) {
      case "preview":
      case "preedit":
      case "true":
        return InlineModeType.INLINE_PREVIEW;
      case "composition":
        return InlineModeType.INLINE_COMPOSITION;
      case "input":
        return InlineModeType.INLINE_INPUT;
    }
    return InlineModeType.INLINE_NONE;
  }

  public WindowsPositionType getWinPos() {
    WindowsPositionType wp = WindowsPositionType.fromString(getString("layout/position"));
    if (getInlinePreedit() == InlineModeType.INLINE_PREVIEW && wp == WindowsPositionType.RIGHT)
      wp = WindowsPositionType.LEFT;
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

  private boolean getShowSwitches() {
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
