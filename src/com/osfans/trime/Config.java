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
import android.content.res.Resources;
import android.util.TypedValue;
import android.util.Log;
import android.graphics.Typeface;

import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.io.*;

import org.yaml.snakeyaml.Yaml;

public class Config {
  private Map<String,Object> mConfig, mDefaultConfig;
  private Map<String, Map> maps;
  private String defaultName = "trime.yaml";
  private static int BLK_SIZE = 1024;
  private static Config self = null;
  private Map<String,String> fallback = new HashMap<String, String>();
  private Typeface tf;

  public Config(Context context) {
    self = this;
    tf = Typeface.createFromAsset(context.getAssets(), "DejaVuSans.ttf");
    maps = new HashMap<String, Map>();
    mDefaultConfig = (Map<String,Object>)new Yaml().load(openFile(context, defaultName));
    fallback.put("candidate_text_color", "text_color");
    fallback.put("border_color", "back_color");
    fallback.put("hilited_text_color", "text_color");
    fallback.put("hilited_back_color", "back_color");
    fallback.put("hilited_candidate_text_color", "hilited_text_color");
    fallback.put("hilited_candidate_back_color", "hilited_back_color");
    fallback.put("hilited_comment_text_color", "comment_text_color");

    fallback.put("hilited_key_back_color", "hilited_candidate_back_color");
    fallback.put("hilited_key_text_color", "hilited_candidate_text_color");
    fallback.put("hilited_key_symbol_color", "hilited_key_text_color");
    fallback.put("hilited_off_key_back_color", "hilited_key_back_color");
    fallback.put("hilited_on_key_back_color", "hilited_key_back_color");
    fallback.put("hilited_off_key_text_color", "hilited_key_text_color");
    fallback.put("hilited_on_key_text_color", "hilited_key_text_color");
    fallback.put("key_back_color", "back_color");
    fallback.put("keyboard_back_color", "key_back_color");
    fallback.put("key_border_color", "border_color");
    fallback.put("key_text_color", "text_color");
    fallback.put("key_symbol_color", "key_text_color");
    fallback.put("label_color", "candidate_text_color");
    fallback.put("off_key_back_color", "key_back_color");
    fallback.put("off_key_text_color", "key_text_color");
    fallback.put("on_key_back_color", "hilited_key_back_color");
    fallback.put("on_key_text_color", "hilited_key_text_color");
    fallback.put("preview_back_color", "key_back_color");
    fallback.put("preview_text_color", "key_text_color");
    fallback.put("shadow_color", "border_color");

    refresh();
  }

  public static boolean copyFromAssets(Context context, String name) {
    try {
      File f = new File("/sdcard/rime", name);
      InputStream is = context.getAssets().open(name);
      OutputStream os = new FileOutputStream(f);
      byte[] buffer = new byte[BLK_SIZE];
      int length = 0;
      while ((length = is.read(buffer)) > 0) os.write(buffer, 0, length);
      os.flush();
      os.close();
      is.close();
      return true;
    } catch (IOException e) {
      Log.e("Config", "Error copy file: " + e);
      return false;
    }
  }

  public InputStream openFile(Context context, String name) {
    try {
      File f = new File("/sdcard/rime", name);
      boolean b = true;
      if (!f.exists()) b = copyFromAssets(context, name); //從assets複製默認文件
      if (b) return new FileInputStream(f);
      return context.getAssets().open(name);
    } catch (IOException e) {
      Log.e("Config", "Error open file: " + e);
      return null;
    }
  }

  public void refresh() {
    String schema_id = Rime.getRime().getSchemaId();
    if (maps.containsKey(schema_id)) {
      mConfig = maps.get(schema_id);
      return;
    }
    mConfig = null;
    File f = new File("/sdcard/rime", schema_id + "." + defaultName);
    if (!f.exists()) return;
    try {
      mConfig = (Map<String,Object>)new Yaml().load(new FileInputStream(f));
      maps.put(schema_id, mConfig); //緩存各方案自定義配置
    } catch (IOException e) {
    }
  }

  private Object _getValue(String k1) {
    if (mConfig != null && mConfig.containsKey(k1)) return mConfig.get(k1);
    if (mDefaultConfig.containsKey(k1)) return mDefaultConfig.get(k1);
    return null;
  }

  private Object _getValue(String k1, String k2) {
    Map<String, Object> m;
    if (mConfig != null && mConfig.containsKey(k1)) {
      m = (Map<String, Object>)mConfig.get(k1);
      if (m != null && m.containsKey(k2)) return m.get(k2);
    }
    if (mDefaultConfig.containsKey(k1)) {
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
    if (self == null) self = new Config(context);
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
    return o == null ? false : (Boolean)o;
  }

  public float getFloat(String key) {
    Object o = getValue("style/" + key);
    float size = 0;
    if (o instanceof Integer) size = ((Integer)o).floatValue();
    else if (o instanceof Float) size = ((Float)o).floatValue();
    return size;
  }

  public int getInt(String key) {
    return (int)getFloat(key);
  }

  public int getPixel(String key) {
    return (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, getFloat(key), Resources.getSystem().getDisplayMetrics());
  }

  public String getString(String key) {
    return (String)getValue("style/" + key);
  }

  public int getColor(String key) {
    String scheme = "preset_color_schemes/" + (String)getValue("style/color_scheme");
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

  public Typeface getFont(String key){
    String name = getString(key);
    if (name == null) return tf;
    File f = new File("/sdcard/rime/fonts", name);
    if(f.exists()) return Typeface.createFromFile(f);
    return tf;
  }
}
