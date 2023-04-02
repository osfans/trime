package com.osfans.trime.ime.symbol;

import androidx.annotation.NonNull;
import com.osfans.trime.data.schema.SchemaManager;
import com.osfans.trime.data.theme.Theme;
import com.osfans.trime.data.theme.ThemeManager;
import com.osfans.trime.ime.enums.KeyCommandType;
import com.osfans.trime.ime.enums.SymbolKeyboardType;
import com.osfans.trime.util.config.ConfigItem;
import com.osfans.trime.util.config.ConfigList;
import com.osfans.trime.util.config.ConfigMap;
import com.osfans.trime.util.config.ConfigValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TabManager {
  private int selected = 0;
  private final List<SimpleKeyBean> keyboardData = new ArrayList<>();
  private final List<SimpleKeyBean> tabSwitchData = new ArrayList<>();
  private final ArrayList<TabTag> tabTags = new ArrayList<>();
  private int tabSwitchPosition = 0;
  private final List<List<SimpleKeyBean>> keyboards = new ArrayList<>();
  private static TabManager self;
  private final List<SimpleKeyBean> notKeyboard = new ArrayList<>();
  private final TabTag tagExit = new TabTag("返回", SymbolKeyboardType.NO_KEY, KeyCommandType.EXIT);

  public static TabManager get() {
    if (null == self) self = new TabManager();
    return self;
  }

  public List<SimpleKeyBean> getTabSwitchData() {
    if (tabSwitchData.size() > 0) return tabSwitchData;

    for (TabTag tag : tabTags) {
      if (SymbolKeyboardType.hasKey(tag.type)) tabSwitchData.add(new SimpleKeyBean(tag.text));
      else tabSwitchData.add(new SimpleKeyBean(""));
    }
    return tabSwitchData;
  }

  public static TabTag getTag(int i) {
    return self.tabTags.get(i);
  }

  public static int getTagIndex(String name) {
    if (name == null || name.length() < 1) return 0;
    for (int i = 0; i < self.tabTags.size(); i++) {
      TabTag tag = self.tabTags.get(i);
      if (tag.text.equals(name)) {
        return i;
      }
    }
    return 0;
  }

  public static int getTagIndex(SymbolKeyboardType type) {
    if (type == null) return 0;
    for (int i = 0; i < self.tabTags.size(); i++) {
      TabTag tag = self.tabTags.get(i);
      if (tag.type.equals(type)) {
        return i;
      }
    }
    return 0;
  }

  private TabManager() {
    final Theme theme = ThemeManager.getActiveTheme();
    final List<String> availables = new ArrayList<>();
    ConfigItem keyboards = theme.o("liquid_keyboard/keyboards");
    if (keyboards != null) {
      ConfigList list = keyboards.getConfigList();
      for (ConfigItem e : list.getItems()) {
        availables.add(e.getConfigValue().getString());
      }
    }

    for (final String id : availables) {
      ConfigItem k = theme.o("liquid_keyboard/" + id);
      if (k != null) {
        ConfigValue t = k.getConfigMap().getValue("type");
        if (t == null) return;

        ConfigValue n = k.getConfigMap().getValue("name");
        String name = n != null ? n.getString() : id;

        ConfigItem keys = k.getConfigMap().get("keys");
        addTab(name, SymbolKeyboardType.fromString(t.getString()), keys);
      }
    }
  }

  public void addTab(@NonNull String name, SymbolKeyboardType type, List<SimpleKeyBean> keyBeans) {
    if (name.trim().isEmpty()) return;

    if (SymbolKeyboardType.hasKeys(type)) {
      for (int i = 0; i < tabTags.size(); i++) {
        TabTag tag = tabTags.get(i);
        if (tag.text.equals(name)) {
          keyboards.set(i, keyBeans);
          return;
        }
      }
    }
    tabTags.add(new TabTag(name, type, ""));
    keyboards.add(keyBeans);
  }

  // 处理single类型和no_key类型。前者把字符串切分为多个按键，后者把字符串转换为命令
  public void addTab(String name, SymbolKeyboardType type, String string) {
    if (string == null) return;
    switch (type) {
      case SINGLE:
        addTab(name, type, SimpleKeyDao.Single(string));
        break;
      case NO_KEY:
        final KeyCommandType commandType = KeyCommandType.fromString(string);
        tabTags.add(new TabTag(name, type, commandType));
        keyboards.add(notKeyboard);
        break;
      default:
        addTab(name, type, SimpleKeyDao.SimpleKeyboard(string));
        break;
    }
  }

  // 解析config的数据
  public void addTab(String name, SymbolKeyboardType type, ConfigItem obj) {
    if (SymbolKeyboardType.Companion.hasKeys(type)) {
      if (obj instanceof ConfigValue) {
        addTab(name, type, obj.getConfigValue().getString());
      } else if (obj instanceof ConfigList) {
        List<ConfigItem> list = obj.getConfigList().getItems();
        List<SimpleKeyBean> keys = new ArrayList<>();
        for (ConfigItem e : list) {
          if (e instanceof ConfigValue) {
            ConfigValue v = e.getConfigValue();
            keys.add(new SimpleKeyBean(v.getString()));
          } else if (e instanceof ConfigMap) {
            ConfigMap m = e.getConfigMap();
            if (m.containsKey("click")) {
              if (m.containsKey("label")) {
                keys.add(
                    new SimpleKeyBean(
                        m.getValue("click").getString(), m.getValue("label").getString()));
              } else {
                keys.add(new SimpleKeyBean(m.getValue("click").getString()));
              }
            } else {
              Map<String, List<String>> symbolMap = SchemaManager.getActiveSchema().getSymbols();
              for (Map.Entry<String, ConfigItem> s : m.getEntries().entrySet()) {
                if (symbolMap != null
                    && symbolMap.containsKey(s.getValue().getConfigValue().getString())) {
                  keys.add(
                      new SimpleKeyBean(s.getValue().getConfigValue().getString(), s.getKey()));
                }
              }
            }
          }
        }
        addTab(name, type, keys);
      }
    } else {
      addTab(name, type, obj != null ? obj.getConfigValue().getString() : "1");
    }
  }

  public List<SimpleKeyBean> select(int tabIndex) {
    if (tabIndex >= tabTags.size()) return keyboardData;
    selected = tabIndex;
    TabTag tag = tabTags.get(tabIndex);
    if (tag.type == SymbolKeyboardType.TABS) tabSwitchPosition = selected;
    return keyboards.get(tabIndex);
  }

  public int getSelected() {
    return selected;
  }

  public boolean isAfterTabSwitch(int position) {
    return tabSwitchPosition <= position;
  }

  public ArrayList<TabTag> getTabCandidates() {
    boolean addExit = true;
    for (TabTag tag : tabTags) {
      if (tag.command == KeyCommandType.EXIT) {
        addExit = false;
        break;
      }
    }
    if (addExit) {
      tabTags.add(tagExit);
      keyboards.add(notKeyboard);
    }
    return tabTags;
  }
}
