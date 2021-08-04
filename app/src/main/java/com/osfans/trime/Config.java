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
import android.util.TypedValue;

import com.osfans.trime.enums.WindowsPositionType;
import com.osfans.trime.ime.core.Preferences;
import com.osfans.trime.util.AppVersionUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
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

  private Map<String, Object> mStyle, mDefaultStyle;
  private String themeName;
  private static String defaultName = "trime";
  private String schema_id;

  private static Config self = null;

  private Map<String, String> fallbackColors;
  private Map presetColorSchemes, presetKeyboards;

  private String[] ClipBoardCompare,ClipBoardOutput,ClipBoardManager;

  private Preferences getPrefs() { return Preferences.Companion.defaultInstance(); }

  public Config(Context context) {
    self = this;
    themeName = getPrefs().getLooks().getSelectedTheme();
    prepareRime(context);
    deployTheme(context);
    init();
    prepareCLipBoardRule();
  }

  private void prepareCLipBoardRule(){
    ClipBoardCompare = getPrefs().getOther().getClipboardCompareRules().trim().split("\n");
    ClipBoardOutput =  getPrefs().getOther().getClipboardOutputRules().trim().split("\n");
    ClipBoardManager = getPrefs().getOther().getClipboardManagerRules().trim().split(",");
  }

  public String[] getClipBoardCompare(){ return ClipBoardCompare;}

  public String[] getClipBoardOutput(){ return ClipBoardOutput;}

  public String[] getClipBoardManager(){ return ClipBoardManager; }

  public boolean hasClipBoardManager(){
    if(ClipBoardManager.length==2){
      if(ClipBoardManager[0].length()>0 && ClipBoardManager[1].length()>0)
        return true;
    }
    return false;
  }

  public void setClipBoardManager(String str) {
    getPrefs().getOther().setClipboardManagerRules(str);
    prepareCLipBoardRule();
  }

  public void setClipBoardCompare(String str) {
    String s = str.replaceAll("\\s*\n\\s*","\n").trim();
    ClipBoardCompare = s.split("\n");

    getPrefs().getOther().setClipboardCompareRules(s);
  }

  public void setClipBoardOutput(String str) {
    String s = str.replaceAll("\\s*\n\\s*","\n").trim();
    ClipBoardOutput = s.split("\n");

    getPrefs().getOther().setClipboardOutputRules(s);
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

  private void prepareRime(Context context) {
    boolean isExist = new File(getSharedDataDir()).exists();
    boolean isOverwrite = AppVersionUtils.INSTANCE.isDifferentVersion(context);
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
    Rime.get(context, !isExist); //覆蓋時不強制部署
  }

  public static String[] getThemeKeys(Context context, boolean isUser) {
    File d = new File(isUser ? get(context).getUserDataDir() : get(context).getSharedDataDir());
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

  public static boolean deployOpencc(Context context) {
    String dataDir = get(context).getResDataDir("opencc");
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
        String ocdName = txtName.replace(".txt", ".ocd2");
        Rime.opencc_convert_dictionary(txtName, ocdName, "text", "ocd2");
      }
    }
    return true;
  }

  public static String[] list(Context context, String path) {
    AssetManager assetManager = context.getAssets();
    String assets[] = null;
    try {
      assets = assetManager.list(path);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return assets;
  }

  public boolean copyFileOrDir(Context context, String path, boolean overwrite) {
    AssetManager assetManager = context.getAssets();
    String assets[] = null;
    try {
      String assetPath = new File(RIME, path).getPath();
      assets = assetManager.list(assetPath);
      if (assets.length == 0) {
        // Files
        copyFile(context, path, overwrite); 
      } else {
        // Dirs
        File dir = new File(getSharedDataDir(), path);
        if (!dir.exists()) dir.mkdir();
        for (int i = 0; i < assets.length; ++i) {
          String subPath = new File(path, assets[i]).getPath();
          copyFileOrDir(context, subPath, overwrite);
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
    return true;
  }

  private boolean copyFile(Context context, String filename, boolean overwrite) {
    AssetManager assetManager = context.getAssets();
    InputStream in = null;
    OutputStream out = null;
    try {
      String assetPath = new File(RIME, filename).getPath();
      in = assetManager.open(assetPath);
      String newFileName = new File(filename.endsWith(".bin") ? getUserDataDir() : getSharedDataDir(), filename).getPath();
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
      e.printStackTrace();
      return false;
    }
    return true;
  }

  private void deployTheme(Context context) {
    if (getUserDataDir().contentEquals(getSharedDataDir())) return; //相同文件夾不部署主題
    String[] configs = get(context).getThemeKeys(context, false);
    for (String config: configs) Rime.deploy_config_file(config, "config_version");
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
    if (schema_id != null)
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


  private Object _getValue(String k1, String k2,Object defaultValue) {
    Map<String, Object> m;
    if (mStyle != null && mStyle.containsKey(k1)) {
      m = (Map<String, Object>) mStyle.get(k1);
      if (m != null && m.containsKey(k2)) return m.get(k2);
    }
    if (mDefaultStyle != null && mDefaultStyle.containsKey(k1)) {
      m = (Map<String, Object>) mDefaultStyle.get(k1);
      if (m != null && m.containsKey(k2)) return m.get(k2);
    }
    return defaultValue;
  }

  private Object _getValue(String k1) {
    if (mStyle != null && mStyle.containsKey(k1)) return mStyle.get(k1);
    if (mDefaultStyle != null && mDefaultStyle.containsKey(k1)) return mDefaultStyle.get(k1);
    return null;
  }

  private Object _getValue(String k1,Object defaultValue) {
    if (mStyle != null && mStyle.containsKey(k1)) return mStyle.get(k1);
    return defaultValue;
  }

  public Object getValue(String s) {
    String[] ss = s.split("/");
    if (ss.length == 1) return _getValue(ss[0]);
    else if (ss.length == 2) return _getValue(ss[0], ss[1]);
    return null;
  }

  public Object getValue(String s,Object defaultValue) {
    String[] ss = s.split("/");
    if (ss.length == 1) return _getValue(ss[0],defaultValue);
    else if (ss.length == 2) return _getValue(ss[0], ss[1],defaultValue);
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


  public int getPixel(String key,int defaultValue) {
    float v = getFloat(key,Float.MAX_VALUE);
    if(v==Float.MAX_VALUE)
      return defaultValue;
    return getPixel(v);
  }

  public static Integer getPixel(Map m, String k, Object s) {
    Object o = getValue(m, k, s);
    if (o == null) return null;
    return getPixel(Float.valueOf(o.toString()));
  }

  public static Integer getPixel(Map m, String k) {
    return getPixel(m, k, null);
  }

  public static Integer getColor(Context context, Map m, String k) {
    Integer color = null;
    if (m.containsKey(k)) {
      Object o = m.get(k);
      String s = o.toString();
      color = parseColor(s);
      if (color == null)
        color = get(context).getCurrentColor(s);
    }
    return color;
  }

  public Integer getColor(String key) {
    Object o = getColorObject(key);
    if (o == null) {
      o = ((Map<String, Object>) presetColorSchemes.get("default")).get(key);
    }
    if (o == null) return null;
    return parseColor(o.toString());
  }

  public Integer getColor(String key,Integer defaultValue) {
    Object o = getColorObject(key);
    if (o == null) {
      o = ((Map<String, Object>) presetColorSchemes.get("default")).get(key);
    }
    if (o == null) return defaultValue;
    return parseColor(o.toString());
  }

  public static Drawable getColorDrawable(Context context, Map m, String k) {
    if (m.containsKey(k)) {
      Object o = m.get(k);
      String s = o.toString();
      Integer color = parseColor(s);
      if (color != null) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(color);
        return gd;
      } else {
        Config config = get(context);
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

  public float getFloat(String key,float defaultValue) {
    Object o = getValue(key,defaultValue);
    if (o == null) return defaultValue;
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
    String scheme = getPrefs().getLooks().getSelectedColor();
    if (!presetColorSchemes.containsKey(scheme)) scheme = getString("color_scheme"); //主題中指定的配色
    if (!presetColorSchemes.containsKey(scheme)) scheme = "default"; //主題中的default配色
    Map map = (Map<String, Object>) presetColorSchemes.get(scheme);
    if (map == null) return null;
    getPrefs().getLooks().setSelectedColor(scheme);
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
        if (s.length() == 3 || s.length() == 4) s = String.format("#%02x000000", Long.decode(s.substring(2))); //0xAA
        else if (s.length() < 8) s = String.format("#%06x", Long.decode(s.substring(2)));
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
        if(name.contains(".9.png")){
          Bitmap bitmap= BitmapFactory.decodeFile(name);
          byte[] chunk = bitmap.getNinePatchChunk();
          // 如果.9.png没有经过第一步，那么chunk就是null, 只能按照普通方式加载
          if(NinePatch.isNinePatchChunk(chunk))
            return new NinePatchDrawable(bitmap, chunk, new Rect(), null);
        }
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

  public WindowsPositionType getWinPos() {
    return WindowsPositionType.fromString(getString("layout/position"));
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
