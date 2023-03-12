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

import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.math.MathUtils;
import com.osfans.trime.core.Rime;
import com.osfans.trime.data.AppPrefs;
import com.osfans.trime.data.DataManager;
import com.osfans.trime.data.schema.RimeSchema;
import com.osfans.trime.data.schema.SchemaManager;
import com.osfans.trime.data.sound.SoundThemeManager;
import com.osfans.trime.ime.keyboard.Key;
import com.osfans.trime.util.CollectionUtils;
import com.osfans.trime.util.ColorUtils;
import com.osfans.trime.util.DimensionsKt;
import com.osfans.trime.util.DrawableKt;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import kotlin.Pair;
import kotlin.collections.MapsKt;
import timber.log.Timber;

/** 解析 YAML 配置文件 */
public class Config {
  private static final String VERSION_KEY = "config_version";
  private static Config self = null;

  private static final AppPrefs appPrefs = AppPrefs.defaultInstance();

  public static Config get() {
    if (self == null) self = new Config();
    return self;
  }

  private static final String defaultThemeName = "trime";
  private String currentColorSchemeId;

  private Map<String, Object> generalStyle;
  private Map<String, String> fallbackColors;
  private Map<String, Map<String, Object>> presetColorSchemes;
  private Map<String, Object> presetKeyboards;
  private Map<String, Object> liquidKeyboard;

  public Style style;
  public Liquid liquid;
  public Colors colors;
  public Keyboards keyboards;

  public Config() {
    self = this;
    ThemeManager.init();

    Rime.get(!DataManager.INSTANCE.getSharedDataDir().exists());

    init();

    Timber.d("Setting sound from color ...");
    SoundThemeManager.switchSound(colors.getString("sound"));

    Timber.d("Initialization finished");
  }

  public void init() {
    final String active = ThemeManager.getActiveTheme();
    Timber.i("Initializing theme, currentThemeName=%s ...", active);
    try {
      final String themeFileName = active + ".yaml";
      Timber.i("Deploying theme '%s' ...", themeFileName);
      if (!Rime.deployRimeConfigFile(themeFileName, VERSION_KEY)) {
        Timber.w("Deploying theme '%s' failed", themeFileName);
      }

      Timber.d("Fetching global theme config map ...");
      long start = System.currentTimeMillis();
      Map<String, Object> fullThemeConfigMap;
      if ((fullThemeConfigMap = Rime.getRimeConfigMap(active, "")) == null) {
        fullThemeConfigMap = Rime.getRimeConfigMap(defaultThemeName, "");
      }

      Objects.requireNonNull(fullThemeConfigMap, "The theme file cannot be empty!");
      Timber.d("Fetching done");

      generalStyle = (Map<String, Object>) fullThemeConfigMap.get("style");
      fallbackColors = (Map<String, String>) fullThemeConfigMap.get("fallback_colors");
      Key.presetKeys = (Map<String, Map<String, Object>>) fullThemeConfigMap.get("preset_keys");
      presetColorSchemes =
          (Map<String, Map<String, Object>>) fullThemeConfigMap.get("preset_color_schemes");
      presetKeyboards = (Map<String, Object>) fullThemeConfigMap.get("preset_keyboards");
      liquidKeyboard = (Map<String, Object>) fullThemeConfigMap.get("liquid_keyboard");
      style = new Style(this);
      liquid = new Liquid(this);
      colors = new Colors(this);
      keyboards = new Keyboards(this);
      long end = System.currentTimeMillis();
      Timber.d("Setting up all theme config map takes %s ms", end - start);
      initCurrentColors();
      Timber.i("The theme is initialized");
      long initEnd = System.currentTimeMillis();
      Timber.d("Initializing cache takes %s ms", initEnd - end);
    } catch (Exception e) {
      Timber.e(e, "Failed to parse the theme!");
      if (!ThemeManager.getActiveTheme().equals(defaultThemeName)) {
        ThemeManager.switchTheme(defaultThemeName);
        init();
      }
    }
  }

  public static class Style {
    private final Config theme;

    public Style(@NonNull final Config theme) {
      this.theme = theme;
    }

    public String getString(@NonNull String key) {
      return CollectionUtils.obtainString(theme.generalStyle, key, "");
    }

    public int getInt(@NonNull String key) {
      return CollectionUtils.obtainInt(theme.generalStyle, key, 0);
    }

    public float getFloat(@NonNull String key) {
      return CollectionUtils.obtainFloat(theme.generalStyle, key, 0f);
    }

    public boolean getBoolean(@NonNull String key) {
      return CollectionUtils.obtainBoolean(theme.generalStyle, key, false);
    }

    public Object getObject(@NonNull String key) {
      return CollectionUtils.obtainValue(theme.generalStyle, key);
    }
  }

  public static class Liquid {
    private final Config theme;

    public Liquid(@NonNull final Config theme) {
      this.theme = theme;
    }

    public Object getObject(@NonNull String key) {
      return CollectionUtils.obtainValue(theme.liquidKeyboard, key);
    }

    public int getInt(@NonNull String key) {
      return CollectionUtils.obtainInt(theme.liquidKeyboard, key, 0);
    }

    public float getFloat(@NonNull String key) {
      return CollectionUtils.obtainFloat(theme.liquidKeyboard, key, theme.style.getFloat(key));
    }
  }

  public static class Colors {
    private final Config theme;

    public Colors(@NonNull final Config theme) {
      this.theme = theme;
    }

    public String getString(@NonNull String key) {
      return CollectionUtils.obtainString(theme.presetColorSchemes, key, "");
    }

    // API 2.0
    public Integer getColor(String key) {
      final Object o = theme.currentColors.get(key);
      if (o instanceof Integer) {
        return (Integer) o;
      }
      return null;
    }

    public Integer getColor(@NonNull Map<String, Object> m, String key) {
      if (!m.containsKey(key) || m.get(key) == null) return null;
      final Integer color = ColorUtils.parseColor((String) m.get(key));
      return color != null ? color : getColor((String) m.get(key));
    }

    //  返回drawable。  Config 2.0
    //  参数可以是颜色或者图片。如果参数缺失，返回null
    public Drawable getDrawable(@NonNull String key) {
      Object o = theme.currentColors.get(key);
      if (o instanceof Integer) {
        final Integer color = (Integer) o;
        final GradientDrawable gradient = new GradientDrawable();
        gradient.setColor(color);
        return gradient;
      } else if (o instanceof String) {
        final String path = (String) o;
        return DrawableKt.bitmapDrawable(path);
      }
      return null;
    }

    // API 2.0
    public Drawable getDrawable(@NonNull Map<String, Object> m, String key) {
      if (!m.containsKey(key) || m.get(key) == null) return null;
      final String value = (String) m.get(key);
      final Integer override = ColorUtils.parseColor(value);
      if (override != null) {
        final GradientDrawable gradient = new GradientDrawable();
        gradient.setColor(override);
        return gradient;
      }
      return theme.currentColors.containsKey(value) ? getDrawable(value) : getDrawable(key);
    }

    //  返回图片或背景的drawable,支持null参数。 Config 2.0
    public Drawable getDrawable(
        String key,
        String borderKey,
        String borderColorKey,
        String roundCornerKey,
        String alphaKey) {
      if (key == null) return null;
      Object o = theme.currentColors.get(key);
      if (o instanceof String) {
        final String path = (String) o;
        final Drawable bitmap;
        if ((bitmap = DrawableKt.bitmapDrawable(path)) != null) {
          if (!TextUtils.isEmpty(alphaKey) || theme.style.getObject(alphaKey) != null) {
            bitmap.setAlpha(MathUtils.clamp(theme.style.getInt(alphaKey), 0, 255));
          }
          return bitmap;
        }
      } else if (o instanceof Integer) {
        final Integer color = (Integer) o;
        final GradientDrawable gradient = new GradientDrawable();
        gradient.setColor(color);
        if (!TextUtils.isEmpty(roundCornerKey)) {
          gradient.setCornerRadius(theme.style.getFloat(roundCornerKey));
        }
        if (!TextUtils.isEmpty(borderColorKey) && !TextUtils.isEmpty(borderKey)) {
          float border = DimensionsKt.dp2px(theme.style.getFloat(borderKey));
          final Integer stroke = getColor(borderColorKey);
          if (stroke != null && border > 0) {
            gradient.setStroke((int) border, stroke);
          }
        }
        if (!TextUtils.isEmpty(alphaKey) || theme.style.getObject(alphaKey) != null) {
          gradient.setAlpha(MathUtils.clamp(theme.style.getInt(alphaKey), 0, 255));
        }
        return gradient;
      }
      return null;
    }
  }

  public static class Keyboards {
    private final Config theme;

    public Keyboards(@NonNull final Config theme) {
      this.theme = theme;
    }

    public Object getObject(@NonNull String key) {
      return CollectionUtils.obtainValue(theme.presetKeyboards, key);
    }

    public String remapKeyboardId(@NonNull String name) {
      final String remapped;
      if (".default".equals(name)) {
        final String currentSchemaId = Rime.getCurrentRimeSchema();
        final String shortSchemaId = currentSchemaId.split("_")[0];
        if (theme.presetKeyboards.containsKey(shortSchemaId)) {
          return shortSchemaId;
        } else {
          final RimeSchema.Speller speller = SchemaManager.getActiveSchema().getSpeller();
          final String alphabet = speller != null ? speller.getAlphabet() : null;
          final String twentySix = "qwerty";
          if (theme.presetKeyboards.containsKey(alphabet)) {
            return alphabet;
          } else {
            if (alphabet != null && (alphabet.contains(",") || alphabet.contains(";"))) {
              remapped = twentySix + "_";
            } else if (alphabet != null && (alphabet.contains("0") || alphabet.contains("1"))) {
              remapped = twentySix + "0";
            } else {
              remapped = twentySix;
            }
          }
        }
      } else {
        remapped = name;
      }
      if (!theme.presetKeyboards.containsKey(remapped)) {
        Timber.w("Cannot find keyboard definition %s, fallback ...", remapped);
        final Map<String, Object> defaultMap =
            (Map<String, Object>) theme.presetKeyboards.get("default");
        if (defaultMap == null)
          throw new IllegalStateException("The default keyboard definition is missing!");
        if (defaultMap.containsKey("import_preset")) {
          final String v;
          return ((v = (String) defaultMap.get("import_preset")) != null) ? v : "default";
        }
      }
      return remapped;
    }
  }

  public void destroy() {
    if (style != null) style = null;
    if (liquid != null) liquid = null;
    if (colors != null) colors = null;
    if (keyboards != null) keyboards = null;
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
      keyboardPadding[0] = (int) DimensionsKt.dp2px(style.getFloat("keyboard_padding_land"));
      keyboardPadding[1] = keyboardPadding[0];
      keyboardPadding[2] = (int) DimensionsKt.dp2px(style.getFloat("keyboard_padding_land_bottom"));
    } else {
      switch (one_hand_mode) {
        case 0:
          // 普通键盘 预留，目前未实装
          keyboardPadding[0] = (int) DimensionsKt.dp2px(style.getFloat("keyboard_padding"));
          keyboardPadding[1] = keyboardPadding[0];
          keyboardPadding[2] = (int) DimensionsKt.dp2px(style.getFloat("keyboard_padding_bottom"));
          break;
        case 1:
          // 左手键盘
          keyboardPadding[0] = (int) DimensionsKt.dp2px(style.getFloat("keyboard_padding_left"));
          keyboardPadding[1] = (int) DimensionsKt.dp2px(style.getFloat("keyboard_padding_right"));
          keyboardPadding[2] = (int) DimensionsKt.dp2px(style.getFloat("keyboard_padding_bottom"));
          break;
        case 2:
          // 右手键盘
          keyboardPadding[1] = (int) DimensionsKt.dp2px(style.getFloat("keyboard_padding_left"));
          keyboardPadding[0] = (int) DimensionsKt.dp2px(style.getFloat("keyboard_padding_right"));
          keyboardPadding[2] = (int) DimensionsKt.dp2px(style.getFloat("keyboard_padding_bottom"));
          break;
      }
    }
    Timber.d(
        "update KeyboardPadding: %s %s %s one_hand_mode=%s",
        keyboardPadding[0], keyboardPadding[1], keyboardPadding[2], one_hand_mode);
    return keyboardPadding;
  }

  //  获取当前配色方案的key的value，或者从fallback获取值。
  @Nullable
  private Object getColorValue(String key) {
    final Map<String, Object> map = presetColorSchemes.get(currentColorSchemeId);
    if (map == null) return null;
    Object value;
    String newKey = key;
    int limit = fallbackColors.size() * 2;
    for (int i = 0; i < limit; i++) {
      if ((value = map.get(newKey)) != null || !fallbackColors.containsKey(newKey)) return value;
      newKey = fallbackColors.get(newKey);
    }
    return null;
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
    if (!presetColorSchemes.containsKey(schemeId))
      schemeId = style.getString("color_scheme"); // 主題中指定的配色
    if (!presetColorSchemes.containsKey(schemeId)) schemeId = "default"; // 主題中的default配色
    Map<String, Object> colorMap = presetColorSchemes.get(schemeId);
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
    if (!presetColorSchemes.containsKey(scheme))
      scheme = style.getString("color_scheme"); // 主題中指定的配色
    if (!presetColorSchemes.containsKey(scheme)) scheme = "default"; // 主題中的default配色
    Map<String, Object> colorMap = presetColorSchemes.get(scheme);
    if (darkMode) {
      if (colorMap.containsKey("dark_scheme")) {
        return (String) colorMap.get("dark_scheme");
      }
    } else {
      if (colorMap.containsKey("light_scheme")) {
        return (String) colorMap.get("light_scheme");
      }
    }
    return scheme;
  }

  @NonNull
  private String joinToFullImagePath(String value) {
    File imgSrc;
    if ((imgSrc =
            new File(
                DataManager.getUserDataDir(),
                "backgrounds/" + style.getString("background_folder") + value))
        .exists()) {
      return imgSrc.getPath();
    } else if ((imgSrc = new File(DataManager.getUserDataDir(), "backgrounds/" + value)).exists()) {
      return imgSrc.getPath();
    }
    return "";
  }

  @NonNull
  public List<Pair<String, String>> getPresetColorSchemes() {
    if (presetColorSchemes == null) return new ArrayList<>();
    return MapsKt.map(
        presetColorSchemes,
        entry ->
            new Pair<>(
                entry.getKey(), Objects.requireNonNull((String) entry.getValue().get("name"))));
  }

  // 遍历当前配色方案的值、fallback的值，从而获得当前方案的全部配色Map
  private final Map<String, Object> currentColors = new HashMap<>();
  // 初始化当前配色 Config 2.0
  public void initCurrentColors() {
    currentColorSchemeId = getColorSchemeName();
    Timber.i("Caching color values (currentColorSchemeId=%s) ...", currentColorSchemeId);
    cacheColorValues();
  }

  // 当切换暗黑模式时，刷新键盘配色方案
  public void initCurrentColors(boolean darkMode) {
    currentColorSchemeId = getColorSchemeName(darkMode);
    Timber.i(
        "Caching color values (currentColorSchemeId=%s, isDarkMode=%s) ...",
        currentColorSchemeId, darkMode);
    cacheColorValues();
  }

  private void cacheColorValues() {
    currentColors.clear();
    final Map<String, Object> colorMap = presetColorSchemes.get(currentColorSchemeId);
    if (colorMap == null) {
      Timber.w("Color scheme id not found: %s", currentColorSchemeId);
      return;
    }
    appPrefs.getThemeAndColor().setSelectedColor(currentColorSchemeId);

    for (Map.Entry<String, Object> entry : colorMap.entrySet()) {
      final String key = entry.getKey();
      if (key.equals("name") || key.equals("author")) continue;
      Object value = parseColorValue(entry.getValue());
      if (value != null) currentColors.put(key, value);
    }

    for (Map.Entry<String, String> entry : fallbackColors.entrySet()) {
      final String key = entry.getKey();
      if (!currentColors.containsKey(key)) {
        final Object value = parseColorValue(getColorValue(key));
        if (value != null) currentColors.put(key, value);
      }
    }
  }

  // 获取参数的真实value，Config 2.0
  // 如果是色彩返回int，如果是背景图返回path string，如果处理失败返回null
  private Object parseColorValue(Object value) {
    if (value == null) return null;
    if (value instanceof String) {
      final String valueStr = (String) value;
      if (valueStr.matches(".*[.\\\\/].*")) {
        return joinToFullImagePath(valueStr);
      } else {
        try {
          return ColorUtils.parseColor(valueStr);
        } catch (Exception e) {
          Timber.e(e, "Unknown color value: %s", value);
        }
      }
    }
    return null;
  }
}
