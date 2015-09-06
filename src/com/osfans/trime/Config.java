/*
 * Copyright 2010 Google Inc.
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

import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.io.*;

public class Config {
  private Map<String, Object> mConfig, mDefaultConfig;
  private Map<String, Map<String, Object>> maps;
  private String defaultName = "trime.yaml";
  private static int BLK_SIZE = 1024;
  private static Config self = null;
  private Typeface tf;

  private static Map<String,String> fallback = new HashMap<String, String>() {
    {
      put("candidate_text_color", "text_color");
      put("border_color", "back_color");
      put("hilited_text_color", "text_color");
      put("hilited_back_color", "back_color");
      put("hilited_candidate_text_color", "hilited_text_color");
      put("hilited_candidate_back_color", "hilited_back_color");
      put("hilited_comment_text_color", "comment_text_color");
      put("text_back_color", "back_color");

      put("hilited_key_back_color", "hilited_candidate_back_color");
      put("hilited_key_text_color", "hilited_candidate_text_color");
      put("hilited_key_symbol_color", "hilited_key_text_color");
      put("hilited_off_key_back_color", "hilited_key_back_color");
      put("hilited_on_key_back_color", "hilited_key_back_color");
      put("hilited_off_key_text_color", "hilited_key_text_color");
      put("hilited_on_key_text_color", "hilited_key_text_color");
      put("key_back_color", "back_color");
      put("keyboard_back_color", "key_back_color");
      put("key_border_color", "border_color");
      put("key_text_color", "text_color");
      put("key_symbol_color", "key_text_color");
      put("label_color", "candidate_text_color");
      put("off_key_back_color", "key_back_color");
      put("off_key_text_color", "key_text_color");
      put("on_key_back_color", "hilited_key_back_color");
      put("on_key_text_color", "hilited_key_text_color");
      put("preview_back_color", "key_back_color");
      put("preview_text_color", "key_text_color");
      put("shadow_color", "border_color");
    }
  };

  public Config(Context context) {
    self = this;
    tf = Typeface.DEFAULT;
    maps = new HashMap<String, Map<String, Object>>();
    mDefaultConfig = (Map<String,Object>)Rime.config_get_map("trime", "");
    reset();
  }

  public static boolean prepareRime(Context context) {
    Log.e("Config", "prepare rime");
    if (new File("/sdcard/rime").exists()) return false;
    copyFileOrDir(context, "rime", false);
    new Rime(true);
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
        String fullPath = "/sdcard/" + path;
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
      String newFileName = "/sdcard/" + filename;
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

  public void reset() {
    String schema_id = Rime.getRime().getSchemaId();
    if (maps.containsKey(schema_id)) {
      mConfig = maps.get(schema_id);
      return;
    }
    mConfig = null;
    File f = new File("/sdcard/rime", schema_id + "." + defaultName);
    if (!f.exists()) return;
    mConfig = (Map<String,Object>)Rime.config_get_map(schema_id + ".trime", "");
    maps.put(schema_id, mConfig); //緩存各方案自定義配置
  }

  private Object _getValue(String k1) {
    if (mConfig != null && mConfig.containsKey(k1)) return mConfig.get(k1);
    if (mDefaultConfig != null && mDefaultConfig.containsKey(k1)) return mDefaultConfig.get(k1);
    return null;
  }

  private Object _getValue(String k1, String k2) {
    Map<String, Object> m;
    if (mConfig != null && mConfig.containsKey(k1)) {
      m = (Map<String, Object>)mConfig.get(k1);
      if (m != null && m.containsKey(k2)) return m.get(k2);
    }
    if (mDefaultConfig != null && mDefaultConfig.containsKey(k1)) {
      m = (Map<String, Object>)mDefaultConfig.get(k1);
      if (m != null && m.containsKey(k2)) return m.get(k2);
    }
    return null;
  }

  public Object getValue(String s) {
    String[] ss = s.split("/");
    if (ss.length == 1) return _getValue(ss[0]);
    else if(ss.length == 2) return _getValue(ss[0], ss[1]);
    return null;
  }

  public List<Object> getKeyboards() {
    return (List<Object>)getValue("keyboard");
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
    if (maps != null) maps.clear();
    if (mDefaultConfig != null) mDefaultConfig.clear();
    if (mConfig != null) mConfig.clear();
    self = null;
  }

  public boolean getBoolean(String key) {
    Object o = getValue("style/" + key);
    return o == null ? true : (Boolean)o;
  }

  public double getDouble(String key) {
    Object o = getValue("style/" + key);
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

  public int getPixel(String key) {
    return (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, getFloat(key), Resources.getSystem().getDisplayMetrics());
  }

  public String getString(String key) {
    return (String)getValue("style/" + key);
  }

  public int getColor(String key) {
    String scheme = "preset_color_schemes/" + getString("color_scheme");
    Map map = (Map<String, Object>)getValue(scheme);
    String fallbackScheme = "preset_color_schemes/default";
    Object o = map.get(key);
    String fallbackKey = key;
    while (o == null && fallback.containsKey(fallbackKey)) {
      fallbackKey = fallback.get(fallbackKey);
      o = map.get(fallbackKey);
    }
    if (o == null) {
      map = (Map<String, Object>)getValue(fallbackScheme);
      o = map.get(key);
    }
    if (o instanceof Integer) return ((Integer)o).intValue();
    return ((Long)o).intValue();
  }

  public void setColor(String color) {
    Rime.customize_string("trime", "style/color_scheme", color);
    Rime.deployConfigFile();
    Map<String, Object> style = (Map<String, Object>)mDefaultConfig.get("style");
    style.put("color_scheme", color);
    mDefaultConfig.put("style", style);
  }

  public String[] getColorKeys() {
    Map<String, Object> m = (Map<String, Object>)getValue("preset_color_schemes");
    if (m == null) return null;
    String[] keys = new String[m.size()];
    m.keySet().toArray(keys);
    return keys;
  }

  public String[] getColorNames(String[] keys) {
    if (keys == null) return null;
    int n = keys.length;
    String[] names = new String[n];
    Map<String, Object> m = (Map<String, Object>)getValue("preset_color_schemes");
    for (int i = 0; i < n; i++) {
      Map<String, Object> m2 = (Map<String, Object>)m.get(keys[i]);
      names[i] = (String)m2.get("name");
    }
    return names;
  }

  public Typeface getFont(String key){
    String name = getString(key);
    if (name == null) return tf;
    File f = new File("/sdcard/rime/fonts", name);
    if(f.exists()) return Typeface.createFromFile(f);
    return tf;
  }
}
