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

package com.osfans.trime.data.theme;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.NinePatch;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.NinePatchDrawable;
import android.util.TypedValue;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.osfans.trime.core.Rime;
import com.osfans.trime.data.AppPrefs;
import com.osfans.trime.data.DataManager;
import com.osfans.trime.ime.enums.PositionType;
import com.osfans.trime.ime.enums.SymbolKeyboardType;
import com.osfans.trime.ime.keyboard.Key;
import com.osfans.trime.ime.keyboard.Sound;
import com.osfans.trime.ime.symbol.TabManager;
import com.osfans.trime.util.ConfigGetter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import kotlin.Pair;
import kotlin.collections.MapsKt;
import timber.log.Timber;

/** 解析 YAML 配置文件 */
public class Config {
  private static Config self = null;

  private static final AppPrefs appPrefs = AppPrefs.defaultInstance();

  private static final String sharedDataDir = appPrefs.getProfile().getSharedDataDir();
  private static final String userDataDir = appPrefs.getProfile().getUserDataDir();

  public static Config get() {
    if (self == null) self = new Config();
    return self;
  }

  private Map<String, Object> defaultKeyboardStyle;
  private String currentThemeName, soundPackageName, currentSound;
  private static final String defaultThemeName = "trime";
  private String currentSchemaId, currentColorSchemeId;

  private Map<String, String> fallbackColors;
  private Map<String, Map<String, String>> presetColorSchemes;
  private Map<String, Map<String, Object>> presetKeyboards;
  private Map<String, Object> liquidKeyboard;

  public Config() {
    this(false);
  }

  public Config(boolean skipDeploy) {
    String methodName =
        "\t<TrimeInit>\t" + Thread.currentThread().getStackTrace()[2].getMethodName() + "\t";
    Timber.d(methodName);
    self = this;
    currentThemeName = appPrefs.getThemeAndColor().getSelectedTheme();
    soundPackageName = appPrefs.getKeyboard().getSoundPackage();

    Timber.d(methodName + "sync");
    DataManager.sync();
    Rime.get(!DataManager.INSTANCE.getSharedDataDir().exists());

    //    正常逻辑不应该部署全部主题，init()方法已经做过当前主题的部署
    //    Timber.d(methodName + "deployTheme");
    //    deployTheme();

    Timber.d(methodName + "init");
    init(true);

    Timber.d(methodName + "setSoundFromColor");
    setSoundFromColor();

    Timber.d(methodName + "finish");
  }

  public String getTheme() {
    return currentThemeName;
  }

  public String getSoundPackage() {
    return soundPackageName;
  }

  public void setTheme(String theme) {
    currentThemeName = theme;
    appPrefs.getThemeAndColor().setSelectedTheme(currentThemeName);
    init(false);
  }

  // 设置音效包
  public void setSoundPackage(String name) {
    soundPackageName = name;
    String path = userDataDir + File.separator + "sound" + File.separator + name + ".sound.yaml";
    File file = new File(path);
    if (file.exists()) {
      applySoundPackage(file, name);
    }
    appPrefs.getKeyboard().setSoundPackage(soundPackageName);
  }

  // 应用音效包
  private void applySoundPackage(File file, String name) {
    // copy soundpackage yaml file from sound folder to build folder
    try {
      InputStream in = new FileInputStream(file);
      OutputStream out =
          new FileOutputStream(
              userDataDir + File.separator + "build" + File.separator + name + ".sound.yaml");

      byte[] buffer = new byte[1024];
      int len;
      while ((len = in.read(buffer)) > 0) {
        out.write(buffer, 0, len);
      }
      in.close();
      out.close();
      Timber.i("applySoundPackage = " + name);
    } catch (Exception e) {
      e.printStackTrace();
    }
    Sound.get(name);
    currentSound = name;
  }

  // 配色指定音效时自动切换音效效果（不会自动修改设置）。
  public void setSoundFromColor() {
    final Map<String, String> m = presetColorSchemes.get(currentColorSchemeId);
    assert m != null;
    if (m.containsKey("sound")) {
      String sound = m.get("sound");
      if (!Objects.equals(sound, currentSound)) {
        File file = new File(userDataDir + "/sound/" + sound + ".sound.yaml");
        if (file.exists()) {
          applySoundPackage(file, sound);
          return;
        }
      }
    }

    if (!Objects.equals(currentSound, soundPackageName)) {
      setSoundPackage(soundPackageName);
    }
  }

  public void init(boolean skipDeployment) {
    Timber.d("Initializing theme ..., skip deployment: %s", skipDeployment);
    Timber.d("Current theme: %s, current schema id: %s", currentThemeName, currentSchemaId);
    try {
      final String fullThemeFileName = currentThemeName + ".yaml";
      final File themeFile = new File(Rime.get_user_data_dir(), "build/" + fullThemeFileName);
      if (skipDeployment && themeFile.exists()) {
        Timber.d("Skipped theme file deployment");
      } else {
        Timber.d("The theme has not been deployed yet, deploying ...");
        Rime.deploy_config_file(fullThemeFileName, "config_version");
      }

      Timber.d("Fetching global theme config map ...");
      long start = System.currentTimeMillis();
      Map<String, Object> fullThemeConfigMap;
      if ((fullThemeConfigMap = Rime.getRimeConfigMap(currentThemeName, "")) == null) {
        fullThemeConfigMap = Rime.getRimeConfigMap(defaultThemeName, "");
      }

      Objects.requireNonNull(fullThemeConfigMap, "The theme file cannot be empty!");
      Timber.d("Fetching done");

      defaultKeyboardStyle = (Map<String, Object>) fullThemeConfigMap.get("style");
      fallbackColors = (Map<String, String>) fullThemeConfigMap.get("fallback_colors");
      Key.presetKeys = (Map<String, Map<String, String>>) fullThemeConfigMap.get("preset_keys");
      presetColorSchemes =
          (Map<String, Map<String, String>>) fullThemeConfigMap.get("preset_color_schemes");
      presetKeyboards =
          (Map<String, Map<String, Object>>) fullThemeConfigMap.get("preset_keyboards");
      liquidKeyboard = (Map<String, Object>) fullThemeConfigMap.get("liquid_keyboard");
      long end = System.currentTimeMillis();
      Timber.d("Setting up all theme config map takes %s ms", end - start);
      initLiquidKeyboard();
      Timber.d("init() initLiquidKeyboard done");
      reloadSchemaId();
      Timber.d("init() reset done");
      initCurrentColors();
      initEnterLabels();
      Timber.i("The theme is initialized");
      long initEnd = System.currentTimeMillis();
      Timber.d("Initializing cache takes %s ms", initEnd - end);
    } catch (Exception e) {
      Timber.e(e, "Failed to parse the theme!");
      if (!currentThemeName.equals(defaultThemeName)) setTheme(defaultThemeName);
    }
  }

  public void reloadSchemaId() {
    currentSchemaId = Rime.getSchemaId();
  }

  @Nullable
  private Object _getValue(String k1, String k2) {
    if (defaultKeyboardStyle != null && defaultKeyboardStyle.containsKey(k1)) {
      final Map<String, Object> m = (Map<String, Object>) defaultKeyboardStyle.get(k1);
      if (m != null && m.containsKey(k2)) return m.get(k2);
    }
    return null;
  }

  private Object _getValue(String k1, String k2, Object defaultValue) {
    if (defaultKeyboardStyle != null && defaultKeyboardStyle.containsKey(k1)) {
      final Map<String, Object> m = (Map<String, Object>) defaultKeyboardStyle.get(k1);
      if (m != null && m.containsKey(k2)) return m.get(k2);
    }
    return defaultValue;
  }

  @Nullable
  private Object _getValue(String k1) {
    if (defaultKeyboardStyle != null && defaultKeyboardStyle.containsKey(k1))
      return defaultKeyboardStyle.get(k1);
    return null;
  }

  private Object _getValue(String k1, Object defaultValue) {
    if (defaultKeyboardStyle != null && defaultKeyboardStyle.containsKey(k1))
      return defaultKeyboardStyle.get(k1);
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
      if (presetKeyboards.containsKey(currentSchemaId)) name = currentSchemaId; // 匹配方案名
      else {
        if (currentSchemaId.contains("_")) name = currentSchemaId.split("_")[0];
        if (!presetKeyboards.containsKey(name)) { // 匹配“_”前的方案名
          Object o = Rime.getRimeSchemaValue(currentSchemaId, "speller/alphabet");
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
    final Map<String, Object> m = presetKeyboards.get(name);
    assert m != null;
    if (m.containsKey("import_preset")) {
      name = Objects.requireNonNull(m.get("import_preset")).toString();
    }
    return name;
  }

  public List<String> getKeyboardNames() {
    final List<String> names = (List<String>) getValue("keyboards");
    final List<String> keyboards = new ArrayList<>();
    for (String s : names) {
      s = getKeyboardName(s);
      if (!keyboards.contains(s)) keyboards.add(s);
    }
    return keyboards;
  }

  public void initLiquidKeyboard() {
    TabManager.clear();
    if (liquidKeyboard == null) return;
    Timber.d("Initializing LiquidKeyboard ...");
    final List<String> names = (List<String>) liquidKeyboard.get("keyboards");
    if (names == null) return;
    for (String s : names) {
      String name = s;
      if (liquidKeyboard.containsKey(name)) {
        Map<String, Object> keyboard = (Map<String, Object>) liquidKeyboard.get(name);
        if (keyboard != null) {
          if (keyboard.containsKey("name")) {
            name = (String) keyboard.get("name");
          }
          if (keyboard.containsKey("type")) {
            TabManager.get()
                .addTab(
                    name,
                    SymbolKeyboardType.Companion.fromObject(keyboard.get("type")),
                    keyboard.get("keys"));
          }
        }
      }
    }
  }

  public Map<String, Object> getKeyboard(String name) {
    if (!presetKeyboards.containsKey(name)) name = "default";
    return presetKeyboards.get(name);
  }

  public Map<String, Object> getLiquidKeyboard() {
    return liquidKeyboard;
  }

  public void destroy() {
    if (defaultKeyboardStyle != null) defaultKeyboardStyle.clear();
    self = null;
  }

  private int[] keyboardPadding;

  public int[] getKeyboardPadding() {
    return keyboardPadding;
  }

  public int[] getKeyboardPadding(boolean land_mode) {
    Timber.i("update KeyboardPadding: getKeyboardPadding(boolean land_mode) ");
    return getKeyboardPadding(one_hand_mode, land_mode);
  }

  private int one_hand_mode;

  public int[] getKeyboardPadding(int one_hand_mode, boolean land_mode) {
    keyboardPadding = new int[3];
    this.one_hand_mode = one_hand_mode;
    if (land_mode) {
      keyboardPadding[0] = getPixel("keyboard_padding_land");
      keyboardPadding[1] = keyboardPadding[0];
      keyboardPadding[2] = getPixel("keyboard_padding_land_bottom");
    } else {
      switch (one_hand_mode) {
        case 0:
          // 普通键盘 预留，目前未实装
          keyboardPadding[0] = getPixel("keyboard_padding");
          keyboardPadding[1] = keyboardPadding[0];
          keyboardPadding[2] = getPixel("keyboard_padding_bottom");
          break;
        case 1:
          // 左手键盘
          keyboardPadding[0] = getPixel("keyboard_padding_left");
          keyboardPadding[1] = getPixel("keyboard_padding_right");
          keyboardPadding[2] = getPixel("keyboard_padding_bottom");
          break;
        case 2:
          // 右手键盘
          keyboardPadding[1] = getPixel("keyboard_padding_left");
          keyboardPadding[0] = getPixel("keyboard_padding_right");
          keyboardPadding[2] = getPixel("keyboard_padding_bottom");
          break;
      }
    }
    Timber.d(
        "update KeyboardPadding: %s %s %s one_hand_mode=%s",
        keyboardPadding[0], keyboardPadding[1], keyboardPadding[2], one_hand_mode);
    return keyboardPadding;
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

  public static Integer getColor(@NonNull Map<?, ?> m, String k) {
    Integer color = null;
    if (m.containsKey(k)) {
      Object o = m.get(k);
      color = parseColor(o);
      if (color == null) color = get().getCurrentColor(o.toString());
    }
    return color;
  }
  /*

    public Integer getColor(String key) {
      Object o = getColorObject(key);
      if (o == null) {
        o = ((Map<?, ?>) Objects.requireNonNull(presetColorSchemes.get(colorID))).get(key);
      }
      return parseColor(o);
    }
  */

  // API 2.0
  public Integer getColor(String key) {
    Object o;
    if (curcentColors.containsKey(key)) {
      o = curcentColors.get(key);
      if (o instanceof Integer) return (Integer) o;
    }
    o = getColorValue(key);
    if (o == null) {
      o = (Objects.requireNonNull(presetColorSchemes.get(currentColorSchemeId))).get(key);
    }
    return parseColor(o);
  }

  // API 2.0
  public Drawable getDrawable(@NonNull Map<?, ?> m, String k) {
    if (m.containsKey(k)) {
      final Object o = m.get(k);
      //      Timber.d("getColorDrawable()" + k + " " + o);
      return drawableObject(o);
    }
    return null;
  }

  public static Object getValue(@NonNull Map<?, ?> m, String k, Object o) {
    return m.containsKey(k) ? m.get(k) : o;
  }

  @NonNull
  public static String getString(Map<?, ?> m, String k, Object s) {
    final Object o = getValue(m, k, s);
    if (o == null) return "";
    return o.toString();
  }

  @NonNull
  public static String getString(Map<?, ?> m, String k) {
    return getString(m, k, "");
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

  public float getLiquidFloat(String key) {
    if (liquidKeyboard != null) {
      if (liquidKeyboard.containsKey(key)) {
        return ConfigGetter.getFloat(liquidKeyboard, key, 0);
      }
    }
    return getFloat(key);
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

  //  获取当前配色方案的key的value，或者从fallback获取值。
  @Nullable
  private String getColorValue(String key) {
    final Map<String, String> map = presetColorSchemes.get(currentColorSchemeId);
    if (map == null) return null;
    appPrefs.getThemeAndColor().setSelectedColor(currentColorSchemeId);
    String colorValue = map.get(key);
    String fallbackKey = key;
    while (colorValue == null && fallbackColors.containsKey(fallbackKey)) {
      fallbackKey = fallbackColors.get(key);
      colorValue = map.get(fallbackKey);
    }
    return colorValue;
  }

  /**
   * 获取配色方案名<br>
   * 优先级：设置>color_scheme>default <br>
   * 避免直接读取 default
   *
   * @return java.lang.String 首个已配置的主题方案名
   */
  private String getColorSchemeName() {
    String schemeId = appPrefs.getThemeAndColor().getSelectedColor();
    if (!presetColorSchemes.containsKey(schemeId)) schemeId = getString("color_scheme"); // 主題中指定的配色
    if (!presetColorSchemes.containsKey(schemeId)) schemeId = "default"; // 主題中的default配色
    Map<String, String> colorMap = presetColorSchemes.get(schemeId);
    if (colorMap.containsKey("dark_scheme") || colorMap.containsKey("light_scheme"))
      hasDarkLight = true;
    return schemeId;
  }

  private boolean hasDarkLight;

  public boolean hasDarkLight() {
    return hasDarkLight;
  }

  /**
   * 获取暗黑模式/明亮模式下配色方案的名称
   *
   * @param darkMode 是否暗黑模式
   * @return 配色方案名称
   */
  private String getColorSchemeName(boolean darkMode) {
    String scheme = appPrefs.getThemeAndColor().getSelectedColor();
    if (!presetColorSchemes.containsKey(scheme)) scheme = getString("color_scheme"); // 主題中指定的配色
    if (!presetColorSchemes.containsKey(scheme)) scheme = "default"; // 主題中的default配色
    Map<String, String> colorMap = presetColorSchemes.get(scheme);
    if (darkMode) {
      if (colorMap.containsKey("dark_scheme")) {
        return colorMap.get("dark_scheme");
      }
    } else {
      if (colorMap.containsKey("light_scheme")) {
        return colorMap.get("light_scheme");
      }
    }
    return scheme;
  }

  // API 2.0
  private static Integer parseColor(Object object) {
    if (object == null) return null;
    if (object instanceof Integer) {
      return (Integer) object;
    }
    if (object instanceof Long) {
      Long o = (Long) object;
      // 这个方法可以把超出Integer.MAX_VALUE的值处理为负数int
      return o.intValue();
    }
    return parseColor(object.toString());
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
    Object o = getColorValue(key);
    return parseColor(o);
  }

  @NonNull
  public List<Pair<String, String>> getPresetColorSchemes() {
    if (presetColorSchemes == null) return new ArrayList<>();
    return MapsKt.map(
        presetColorSchemes,
        entry -> new Pair<>(entry.getKey(), Objects.requireNonNull(entry.getValue().get("name"))));
  }

  private Map<String, String> mEnterLabels;

  public Map<String, String> getmEnterLabels() {
    return mEnterLabels;
  }

  public void initEnterLabels() {
    Object enter_labels = getValue("enter_labels");
    if (enter_labels == null) mEnterLabels = new HashMap<>();
    else mEnterLabels = (Map<String, String>) enter_labels;

    String defaultEnterLabel = "Enter";
    if (mEnterLabels.containsKey("default")) defaultEnterLabel = mEnterLabels.get("default");
    else mEnterLabels.put("default", defaultEnterLabel);

    if (!mEnterLabels.containsKey("done")) mEnterLabels.put("done", defaultEnterLabel);
    if (!mEnterLabels.containsKey("go")) mEnterLabels.put("go", defaultEnterLabel);
    if (!mEnterLabels.containsKey("next")) mEnterLabels.put("next", defaultEnterLabel);
    if (!mEnterLabels.containsKey("none")) mEnterLabels.put("none", defaultEnterLabel);
    if (!mEnterLabels.containsKey("pre")) mEnterLabels.put("pre", defaultEnterLabel);
    if (!mEnterLabels.containsKey("search")) mEnterLabels.put("search", defaultEnterLabel);
    if (!mEnterLabels.containsKey("send")) mEnterLabels.put("send", defaultEnterLabel);
  }

  public Typeface getFont(String key) {
    final String name = getString(key);
    if (name != null) {
      final File f = new File(DataManager.getDataDir("fonts"), name);
      if (f.exists()) return Typeface.createFromFile(f);
    }
    return Typeface.DEFAULT;
  }

  //  返回drawable。参数可以是颜色或者图片。如果参数缺失，返回null
  private Drawable drawableObject(Object o) {
    if (o == null) return null;
    String name = o.toString();
    Integer color = parseColor(o);
    if (color == null) {
      if (curcentColors.containsKey(name)) {
        o = curcentColors.get(name);
        color = parseColor(o);
      }
    }
    if (color != null) {
      final GradientDrawable gd = new GradientDrawable();
      gd.setColor(color);
      return gd;
    }
    return drawableBitmapObject(name);
  }

  //  返回图片的drawable。如果参数缺失、非图片，返回null
  private Drawable drawableBitmapObject(Object o) {
    if (o == null) return null;
    if (o instanceof String) {
      String name = (String) o;
      String nameDirectory =
          DataManager.getDataDir("backgrounds" + File.separator + backgroundFolder);
      File f = new File(nameDirectory, name);

      if (!f.exists()) {
        nameDirectory = DataManager.getDataDir("backgrounds");
        f = new File(nameDirectory, name);
      }

      if (!f.exists()) {
        if (curcentColors.containsKey(name)) {
          o = curcentColors.get(name);
          if (o instanceof String) f = new File((String) o);
        }
      }

      if (f.exists()) {
        name = f.getPath();
        if (name.contains(".9.png")) {
          final Bitmap bitmap = BitmapFactory.decodeFile(name);
          final byte[] chunk = bitmap.getNinePatchChunk();
          // 如果 .9.png 没有经过第一步，那么 chunk 就是 null, 只能按照普通方式加载
          if (NinePatch.isNinePatchChunk(chunk))
            return new NinePatchDrawable(bitmap, chunk, new Rect(), null);
        }
        return Drawable.createFromPath(name);
      }
    }
    return null;
  }

  public Drawable getColorDrawable(String key) {
    final Object o = getColorValue(key);
    return drawableObject(o);
  }

  public PositionType getWinPos() {
    return PositionType.Companion.fromString(getString("layout/position"));
  }

  public int getLiquidPixel(String key) {
    if (liquidKeyboard != null) {
      if (liquidKeyboard.containsKey(key)) {
        return ConfigGetter.getPixel(liquidKeyboard, key, 0);
      }
    }
    return getPixel(key);
  }

  public Integer getLiquidColor(String key) {
    if (liquidKeyboard != null) {
      if (liquidKeyboard.containsKey(key)) {
        Integer value = parseColor(liquidKeyboard.get(key));
        if (value != null) return value;
      }
    }
    return getColor(key);
  }

  // 获取当前色彩 Config 2.0
  public Integer getCurrentColor_(String key) {
    Object o = curcentColors.get(key);
    return (Integer) o;
  }

  // 获取当前背景图路径 Config 2.0
  public String getCurrentImage(String key) {
    Object o = curcentColors.get(key);
    if (o instanceof String) return (String) o;
    return "";
  }

  //  返回drawable。  Config 2.0
  //  参数可以是颜色或者图片。如果参数缺失，返回null
  public Drawable getDrawable_(String key) {
    if (key == null) return null;
    Object o = curcentColors.get(key);
    if (o instanceof Integer) {
      Integer color = (Integer) o;
      final GradientDrawable gd = new GradientDrawable();
      gd.setColor(color);
      return gd;
    } else if (o instanceof String) return getDrawableBitmap_(key);

    return null;
  }

  //  返回图片或背景的drawable,支持null参数。 Config 2.0
  public Drawable getDrawable(
      String key, String borderKey, String borderColorKey, String roundCornerKey, String alphaKey) {
    if (key == null) return null;
    Drawable drawable = getDrawableBitmap_(key);
    if (drawable != null) {
      if (alphaKey != null) {
        if (hasKey(alphaKey)) {
          int alpha = getInt("layout/alpha");
          if (alpha <= 0) alpha = 0;
          else if (alpha >= 255) alpha = 255;
          drawable.setAlpha(alpha);
        }
      }
      return drawable;
    }

    GradientDrawable gd = new GradientDrawable();
    Object o = curcentColors.get(key);
    if (!(o instanceof Integer)) return null;
    gd.setColor((int) o);

    if (roundCornerKey != null) gd.setCornerRadius(getFloat(roundCornerKey));

    if (borderColorKey != null && borderKey != null) {
      int border = getPixel(borderKey);
      Object borderColor = curcentColors.get(borderColorKey);
      if (borderColor instanceof Integer && border > 0) {
        gd.setStroke(border, getCurrentColor_(borderColorKey));
      }
    }

    if (alphaKey != null) {
      if (hasKey(alphaKey)) {
        int alpha = getInt("layout/alpha");
        if (alpha <= 0) alpha = 0;
        else if (alpha >= 255) alpha = 255;
        gd.setAlpha(alpha);
      }
    }

    return gd;
  }

  //  返回图片的drawable。 Config 2.0
  //  如果参数缺失、非图片，返回null. 在genCurrentColors()中已经验证存在文件，因此不需要重新验证。
  public Drawable getDrawableBitmap_(String key) {
    if (key == null) return null;

    Object o = curcentColors.get(key);
    if (o instanceof String) {
      String path = (String) o;
      if (path.contains(".9.png")) {
        final Bitmap bitmap = BitmapFactory.decodeFile(path);
        final byte[] chunk = bitmap.getNinePatchChunk();
        if (NinePatch.isNinePatchChunk(chunk))
          return new NinePatchDrawable(bitmap, chunk, new Rect(), null);
      }
      return Drawable.createFromPath(path);
    }
    return null;
  }

  // 遍历当前配色方案的值、fallback的值，从而获得当前方案的全部配色Map
  private final Map<String, Object> curcentColors = new HashMap<>();
  private String backgroundFolder;
  // 初始化当前配色 Config 2.0
  public void initCurrentColors() {
    curcentColors.clear();
    currentColorSchemeId = getColorSchemeName();
    backgroundFolder = getString("background_folder");
    Timber.d("Initializing currentColors ...");
    Timber.d(
        "currentColorSchemeId = %s, currentThemeName = %s, currentSchemaId = %s",
        currentColorSchemeId, currentThemeName, currentSchemaId);
    final Map<String, String> colorMap = presetColorSchemes.get(currentColorSchemeId);
    if (colorMap == null) {
      Timber.d("Color scheme id not found: %s", currentColorSchemeId);
      return;
    }
    appPrefs.getThemeAndColor().setSelectedColor(currentColorSchemeId);

    for (Map.Entry<String, String> entry : colorMap.entrySet()) {
      Object value = getColorRealValue(entry.getValue());
      if (value != null) curcentColors.put(entry.getKey(), value);
    }

    for (Map.Entry<String, String> entry : fallbackColors.entrySet()) {
      String key = entry.getKey();
      if (!curcentColors.containsKey(key)) {
        String colorValue = colorMap.get(key);
        String fallbackKey = key;
        List<String> fallbackKeys = new ArrayList<>();
        while (colorValue == null && fallbackColors.containsKey(fallbackKey)) {
          fallbackKey = fallbackColors.get(fallbackKey);
          colorValue = colorMap.get(fallbackKey);
          fallbackKeys.add(fallbackKey);
          // 避免死循环
          if (fallbackKeys.size() > 40) break;
        }
        if (colorValue != null) {
          Object value = getColorRealValue(colorValue);
          if (value != null) {
            curcentColors.put(key, value);
            for (String k : fallbackKeys) {
              curcentColors.put(k, value);
            }
          }
        }
      }
    }
  }

  // 当切换暗黑模式时，刷新键盘配色方案
  public void initCurrentColors(boolean darkMode) {
    curcentColors.clear();
    currentColorSchemeId = getColorSchemeName(darkMode);
    backgroundFolder = getString("background_folder");
    Timber.d("Initializing currentColors ...");
    Timber.d(
        "currentColorSchemeId = %s, currentThemeName = %s, currentSchemaId = %s, isDarkMode = %s",
        currentColorSchemeId, currentThemeName, currentSchemaId, darkMode);
    final Map<String, String> colorMap = presetColorSchemes.get(currentColorSchemeId);
    if (colorMap == null) {
      Timber.i("Color scheme id not found: %s", currentColorSchemeId);
      return;
    }
    appPrefs.getThemeAndColor().setSelectedColor(currentColorSchemeId);

    for (Map.Entry<String, String> entry : colorMap.entrySet()) {
      Object value = getColorRealValue(entry.getValue());
      if (value != null) curcentColors.put(entry.getKey(), value);
    }

    for (Map.Entry<String, String> entry : fallbackColors.entrySet()) {
      String key = entry.getKey();
      if (!curcentColors.containsKey(key)) {
        String colorValue = colorMap.get(key);
        String fallbackKey = key;
        List<String> fallbackKeys = new ArrayList<>();
        while (colorValue == null && fallbackColors.containsKey(fallbackKey)) {
          fallbackKey = fallbackColors.get(fallbackKey);
          colorValue = colorMap.get(fallbackKey);
          fallbackKeys.add(fallbackKey);
          // 避免死循环
          if (fallbackKeys.size() > 40) break;
        }
        if (colorValue != null) {
          Object value = getColorRealValue(colorValue);
          if (value != null) {
            curcentColors.put(key, value);
            for (String k : fallbackKeys) {
              curcentColors.put(k, value);
            }
          }
        }
      }
    }
  }

  // 获取参数的真实value，Config 2.0
  // 如果是色彩返回int，如果是背景图返回path string，如果处理失败返回null
  private Object getColorRealValue(Object object) {
    if (object == null) return null;
    if (object instanceof Integer) {
      return object;
    }
    if (object instanceof Long) {
      Long o = (Long) object;
      //      Timber.w("getColorRealValue() Long, %d ; 0X%s", o, data2hex(object));
      return o.intValue();
    }
    String s = object.toString();
    if (!s.matches(".*[.\\\\/].*")) {
      try {
        s = s.toLowerCase(Locale.getDefault());
        if (s.startsWith("0x")) {
          if (s.length() == 3 || s.length() == 4)
            s = String.format("#%02x000000", Long.decode(s.substring(2))); // 0xAA
          else if (s.length() < 8) s = String.format("#%06x", Long.decode(s.substring(2)));
          else if (s.length() == 9) s = "#0" + s.substring(2);
        }
        if (s.matches("(0x|#)?[a-f0-9]+")) return Color.parseColor(s.replace("0x", "#"));
      } catch (Exception e) {
        Timber.e("getColorRealValue() unknown color, %s ; object %s", s, object);
        e.printStackTrace();
      }
    }

    String nameDirectory =
        DataManager.getDataDir("backgrounds" + File.separator + backgroundFolder);
    File f = new File(nameDirectory, s);

    if (!f.exists()) {
      nameDirectory = DataManager.getDataDir("backgrounds");
      f = new File(nameDirectory, s);
    }

    if (f.exists()) return f.getPath();
    return null;
  }

  //  把int和long打印为hex，对color做debug使用
  public static String data2hex(Object data) {
    Long a;
    if (data instanceof Integer) a = (long) (int) data;
    else a = (Long) data;
    int len = (int) Math.ceil(Math.log(a) / Math.log(16));
    char[] result = new char[len];
    String s = "0123456789ABCDEF";

    for (int i = len - 1; i >= 0; i--) {
      int b = (int) (15 & a);
      result[i] = s.charAt(b);
      a = a >> 4;
    }
    return new String(result);
  }
}
