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
import android.util.Log;

import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.io.*;

import org.yaml.snakeyaml.Yaml;

public class Schema {
  private Map<String,Object> mSchema, mDefaultSchema;
  private String defaultName = "trime.yaml";
  private int BLK_SIZE = 1024;
  private static Schema self = null;
  private Map<String,String> fallback = new HashMap<String, String>();

  public Schema(Context context) {
    self = this;
    mDefaultSchema = (Map<String,Object>)new Yaml().load(openFile(context, defaultName));
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

  public FileInputStream openFile(Context context, String name) {
    try{
      File f = new File("/sdcard/rime", name);
      if (!f.exists()) { //從assets複製默認文件
        InputStream is = context.getAssets().open(name);
        OutputStream os = new FileOutputStream(f);
        byte[] buffer = new byte[BLK_SIZE];
        int length = 0;
        while ((length = is.read(buffer)) > 0) os.write(buffer, 0, length);
        os.flush();
        os.close();
        is.close();
      }
      return new FileInputStream(f);
    } catch (IOException e) {
      throw new RuntimeException("Error load " + defaultName, e);
    }
  }

  public void refresh() {
    File f = new File("/sdcard/rime", Rime.getRime().getSchemaId() + "." + defaultName);
    mSchema = null;
    if (!f.exists()) return;
    try {
      mSchema = (Map<String,Object>)new Yaml().load(new FileInputStream(f));
    } catch (IOException e) {
    }
  }

  private Object _getValue(String k1) {
    if (mSchema != null && mSchema.containsKey(k1)) return mSchema.get(k1);
    if (mDefaultSchema.containsKey(k1)) return mDefaultSchema.get(k1);
    return null;
  }

  private Object _getValue(String k1, String k2) {
    Map<String, Object> m;
    if (mSchema != null && mSchema.containsKey(k1)) {
      m = (Map<String, Object>)mSchema.get(k1);
      if (m != null && m.containsKey(k2)) return m.get(k2);
    }
    if (mDefaultSchema.containsKey(k1)) {
      m = (Map<String, Object>)mDefaultSchema.get(k1);
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

  public static Schema get() {
    return self;
  }

  public boolean getBoolean(String key) {
    Map map = (Map<String, Object>)getValue("style/layout");
    return (Boolean)map.get(key);
  }

  public int getInt(String key) {
    Map map = (Map<String, Object>)getValue("style/layout");
    return (Integer)map.get(key);
  }

  public float getFloat(String key) {
    Map map = (Map<String, Object>)getValue("style/layout");
    return ((Double)map.get(key)).floatValue();
  }

  public String getString(String key) {
    Map map = (Map<String, Object>)getValue("style/layout");
    return (String)map.get(key);
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
    return ((Long)o).intValue();
  }
}
