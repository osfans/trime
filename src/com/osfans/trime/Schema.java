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
import java.io.IOException;

import org.yaml.snakeyaml.Yaml;

public class Schema {
  private Map<String,Object> mDefaultSchema;
  private Context mContext;

  public Schema(Context context) {
    mContext = context;
    mDefaultSchema = loadPreset("trime");
  }

  private Map<String,Object> loadPreset(String name) {
    try{
      return (Map<String,Object>)new Yaml().load(mContext.getAssets().open(name + ".yaml"));
    } catch (IOException e) {
      throw new RuntimeException("Error load " + name + ".yaml", e);
    }
  }


  private Object _getValue(String k1) {
    if (mDefaultSchema.containsKey(k1)) return mDefaultSchema.get(k1);
    return null;
  }

  private Object _getValue(String k1, String k2) {
    Map<String, Object> m;
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

}
