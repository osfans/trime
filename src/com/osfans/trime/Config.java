/*
 * Copyright 2015 osfans
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.osfans.trime;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.util.TypedValue;
import android.util.Log;
import android.graphics.Typeface;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;
import java.io.*;

/** 解析YAML配置文件 */
public class Config {
  public static String SDCARD = "/sdcard/";
  public final static int INLINE_NONE = 0;
  public final static int INLINE_PREVIEW = 1;
  public final static int INLINE_COMPOSITION = 2;
  public final static int INLINE_INPUT = 3;

  public final static int CAND_POS_LEFT = 0;
  public final static int CAND_POS_RIGHT = 1;
  public final static int CAND_POS_FIXED = 2;

  private Map<String, Object> mStyle, mDefaultStyle;
  private static String defaultName = "trime";
  private static String defaultFile = "trime.yaml";
  private String themeName;
  private String schema_id;
  private static String RIME = "rime";
  private static String USER_DATA_DIR = SDCARD + RIME;
  public static String OPENCC_DATA_DIR = USER_DATA_DIR + "/opencc/";
  private static int BLK_SIZE = 1024;
  private static Config self = null;

  private Map<String,String> fallbackColors;
  private Map presetColorSchemes, presetKeyboards;

  public Config(Context context) {
    self = this;
    themeName = Rime.config_get_string("user", "var/previously_selected_theme");
    if (!Function.isEmpty(themeName)) themeName += ".trime";
    init();
  }

  public String getTheme() {
    return themeName;
  }
  public static void prepareRime(Context context) {
    boolean b = new File(USER_DATA_DIR).exists();
    if (b) {
      copyFileOrDir(context, RIME + "/" + defaultFile, false);
    } else {
      copyFileOrDir(context, RIME, false);
    }
    Rime.get(!b);
  }

  public static String[] getThemeKeys() {
    File d = new File(USER_DATA_DIR);
    FilenameFilter trimeFilter = new FilenameFilter(){
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
      String k = keys[i].replace(".trime.yaml","").replace(".yaml","");
      names[i] = k;
    }
    return names;
  }

  public static boolean deployOpencc() {
    File d = new File(OPENCC_DATA_DIR);
    if (d.exists()) {
      FilenameFilter txtFilter = new FilenameFilter(){
        @Override
        public boolean accept(File dir, String filename) {
          return filename.endsWith(".txt");
        }
      };
      for (String txtName: d.list(txtFilter)) {
        txtName = OPENCC_DATA_DIR + txtName;
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
        String fullPath = SDCARD + path;
        File dir = new File(fullPath);
        if (!dir.exists()) dir.mkdir();
        for (int i = 0; i < assets.length; ++i) {
          copyFileOrDir(context, path + "/" + assets[i], overwrite);
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
      String newFileName = SDCARD + filename;
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
    Rime.deploy_config_file(themeName + ".yaml" , "config_version");
  }

  public void setTheme(String theme) {
    themeName = theme;
    Rime.config_set_string("user", "var/previously_selected_theme", themeName.replace(".trime", ""));
    init();
  }

  public void init() {
    String name = Rime.config_get_string(themeName, "config_version");
    if (Function.isEmpty(name)) themeName = defaultName;
    deployConfig();
    mDefaultStyle = (Map<String,Object>)Rime.config_get_map(themeName, "style");
    fallbackColors = (Map<String,String>)Rime.config_get_map(themeName, "fallback_colors");
    List androidKeys = Rime.config_get_list(themeName, "android_keys/name");
    Key.androidKeys = new ArrayList<String>(androidKeys.size());
    for (Object o : androidKeys) {
      Key.androidKeys.add(o.toString());
    }
    Key.presetKeys = (Map<String, Map>)Rime.config_get_map(themeName, "preset_keys");
    presetColorSchemes = Rime.config_get_map(themeName, "preset_color_schemes");
    presetKeyboards = Rime.config_get_map(themeName, "preset_keyboards");
    reset();
  }

  public void reset() {
    schema_id = Rime.getSchemaId();
    mStyle = (Map<String,Object>)Rime.schema_get_value(schema_id, "style");
  }

  private Object _getValue(String k1, String k2) {
    Map<String, Object> m;
    if (mStyle != null && mStyle.containsKey(k1)) {
      m = (Map<String, Object>)mStyle.get(k1);
      if (m != null && m.containsKey(k2)) return m.get(k2);
    }
    if (mDefaultStyle != null && mDefaultStyle.containsKey(k1)) {
      m = (Map<String, Object>)mDefaultStyle.get(k1);
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
    else if(ss.length == 2) return _getValue(ss[0], ss[1]);
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
    Map<String, Object> m = (Map<String, Object>)presetKeyboards.get(name);
    if (m.containsKey("import_preset")) {
      name = m.get("import_preset").toString();
    }
    return name;
  }

  public List<String> getKeyboardNames() {
    List<String> names = (List<String>)getValue("keyboards");
    List<String> keyboards = new ArrayList<String>();
    for (String s: names) {
      s = getKeyboardName(s);
      if (!keyboards.contains(s)) keyboards.add(s);
    }
    return keyboards;
  }

  public Map<String, Object> getKeyboard(String name) {
    if (!presetKeyboards.containsKey(name)) name = "default";
    return (Map<String, Object>)presetKeyboards.get(name);
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

  public boolean getBoolean(String key) {
    Object o = getValue(key);
    return o == null ? true : (Boolean)o;
  }

  public double getDouble(String key) {
    Object o = getValue(key);
    double size = 0;
    if (o instanceof Integer) size = ((Integer)o).doubleValue();
    else if (o instanceof Float) size = ((Float)o).doubleValue();
    else if (o instanceof Double) size = ((Double)o).doubleValue();
    return size;
  }

  public float getFloat(String key) {
    return (float)getDouble(key);
  }

  public int getInt(String key) {
    return (int)getDouble(key);
  }

  public static int getPixel(float f) {
    return (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, f, Resources.getSystem().getDisplayMetrics());
  }

  public int getPixel(String key) {
    return getPixel(getFloat(key));
  }

  public String getString(String key) {
    Object o = getValue(key);
    return (o == null) ? "" : o.toString();
  }

  public Integer getCurrentColor(String key) {
    String scheme = getString("color_scheme");
    Map map = (Map<String, Object>)presetColorSchemes.get(scheme);
    Object o = map.get(key);
    String fallbackKey = key;
    while (o == null && fallbackColors.containsKey(fallbackKey)) {
      fallbackKey = fallbackColors.get(fallbackKey);
      o = map.get(fallbackKey);
    }

    if (o instanceof Integer) return ((Integer)o).intValue();
    if (o instanceof Float || o instanceof Double) return ((Long)o).intValue();
    return null;
  }

  public Integer getColor(String key) {
    Object o = getCurrentColor(key);

    if (o == null) {
      o = ((Map<String, Object>)presetColorSchemes.get("default")).get(key);
    }

    if (o instanceof Integer) return ((Integer)o).intValue();
    if (o instanceof Float || o instanceof Double || o instanceof Long) return ((Long)o).intValue();
    return null;
  }

  public void setColor(String color) {
    Rime.customize_string(themeName, "style/color_scheme", color);
    deployConfig();
    mDefaultStyle.put("color_scheme", color);
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
      Map<String, Object> m = (Map<String, Object>)presetColorSchemes.get(keys[i]);
      names[i] = m.get("name").toString();
    }
    return names;
  }

  public Typeface getFont(String key){
    String name = getString(key);
    if (name != null) {
      File f = new File(USER_DATA_DIR + "/fonts", name);
      if(f.exists()) return Typeface.createFromFile(f);
    }
    return Typeface.DEFAULT;
  }

  public Drawable getColorDrawable(String key) {
    String scheme = getString("color_scheme");
    Map map = (Map<String, Object>)presetColorSchemes.get(scheme);
    Object o = map.get(key);
    String fallbackKey = key;
    while (o == null && fallbackColors.containsKey(fallbackKey)) {
      fallbackKey = fallbackColors.get(fallbackKey);
      o = map.get(fallbackKey);
    }
    if (o == null) {
      o = ((Map<String, Object>)presetColorSchemes.get("default")).get(key);
    }
    Integer color = null;
    if (o instanceof Integer) color = ((Integer)o).intValue();
    else if (o instanceof Float || o instanceof Double || o instanceof Long) color = ((Long)o).intValue();
    else if (o instanceof String) {
      String name = o.toString();
      name = USER_DATA_DIR + "/backgrounds/" + name;
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

  public Drawable getDrawable(String key){
    Object o = getValue(key);
    if (o != null) {
      Integer color = null;
      if (o instanceof Integer) color = ((Integer)o).intValue();
      else if (o instanceof Float || o instanceof Double || o instanceof Long) color = ((Long)o).intValue();
      else if (o instanceof String) {
        String name = o.toString();
        name = USER_DATA_DIR + "/backgrounds/" + name;
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
    }
    return null;
  }

  public int getInlinePreedit() {
    if (getString("inline_code").contentEquals("true")) return INLINE_INPUT;
    switch (getString("inline_preedit")) {
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
}
