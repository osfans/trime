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

import java.util.Map;
import java.util.List;
import java.io.*;

import org.yaml.snakeyaml.Yaml;

public class Schema {
  private Map<String,Object> mSchema, mDefaultSchema;
  private String defaultName = "trime.yaml";
  private int BLK_SIZE = 1024;

  public Schema(Context context) {
    mDefaultSchema = (Map<String,Object>)new Yaml().load(openFile(context, defaultName));
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

  public void load() {
    File f = new File("/sdcard/rime", Rime.getRime().getCurrentSchema() + "." + defaultName);
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
    load();
    String[] ss = s.split("/");
    if (ss.length == 1) return _getValue(ss[0]);
    else if(ss.length == 2) return _getValue(ss[0], ss[1]);
    return null;
  }

  public List<Object> getKeyboards() {
    return (List<Object>)getValue("keyboard");
  }

}
