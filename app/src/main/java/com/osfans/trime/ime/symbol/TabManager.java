package com.osfans.trime.ime.symbol;

import androidx.annotation.NonNull;
import com.osfans.trime.data.schema.SchemaManager;
import com.osfans.trime.data.theme.Theme;
import com.osfans.trime.ime.enums.KeyCommandType;
import com.osfans.trime.ime.enums.SymbolKeyboardType;
import com.osfans.trime.util.CollectionUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// 使用TabManager时，不应该使用变量保存TabManager实例，应该使用TabManager.get()方法获取
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

  public static void updateSelf() {
    self = new TabManager();
  }

  public static TabManager get() {
    if (null == self) self = new TabManager();
    return self;
  }

  public List<SimpleKeyBean> getTabSwitchData() {
    if (tabSwitchData.size() > 0) return tabSwitchData;

    for (TabTag tag : tabTags) {
      if (SymbolKeyboardType.hasKey(tag.type)) tabSwitchData.add(new SimpleKeyBean(tag.text));
    }
    return tabSwitchData;
  }

  /**
   * 得到TABS中对应的TabTag 去除不显示的tagTab(没有keys列表的tagTab)之后按顺序排列tagTab,再从中获取TabTag
   *
   * @param position 位置（索引）
   * @return TabTag
   */
  public TabTag getTabSwitchTabTag(int position) {
    int i = 0;
    for (TabTag tag : tabTags) {
      if (SymbolKeyboardType.hasKey(tag.type)) {
        if (i++ == position) return tag;
      }
    }
    return null;
  }

  /**
   * 得到TABS中对应的真实索引 真实的索引是去除 没有keys列表的tagTab 之后按顺序排列的tagTab索引
   *
   * @param position 位置（索引）
   * @return int TABS中显示的真实索引
   */
  public int getTabSwitchPosition(int position) {
    int i = 0;
    for (TabTag tag : tabTags) {
      if (SymbolKeyboardType.hasKey(tag.type)) {
        if (position-- <= 0) break;
      }
      i++;
    }
    return i;
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
    final Theme theme = Theme.get();
    final List<String> availables = (List<String>) theme.liquid.getObject("keyboards");
    if (availables != null) {
      for (final String id : availables) {
        final Map<String, Object> keyboard;
        if ((keyboard = (Map<String, Object>) theme.liquid.getObject(id)) != null) {
          final String name = (String) CollectionUtils.getOrDefault(keyboard, "name", id);
          if (keyboard.containsKey("type")) {
            addTab(
                name,
                SymbolKeyboardType.fromString((String) keyboard.get("type")),
                keyboard.get("keys"));
          }
        }
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
  public void addTab(String name, SymbolKeyboardType type, Object obj) {
    if (SymbolKeyboardType.Companion.hasKeys(type)) {
      if (obj instanceof String) {
        addTab(name, type, (String) obj);
      } else if (obj instanceof List<?>) {
        List<?> list = (List<?>) obj;
        if (list.size() > 0) {
          int i = 0;
          Object o;
          List<SimpleKeyBean> keys = new ArrayList<>();

          while (i < list.size()) {
            o = list.get(i);
            if (o instanceof String) {
              String s = (String) o;
              keys.add(new SimpleKeyBean(s));
            } else if (o instanceof Map<?, ?>) {
              Map<String, String> p = (Map<String, String>) o;
              if (p.containsKey("click")) {
                if (p.containsKey("label"))
                  keys.add(new SimpleKeyBean((String) p.get("click"), (String) p.get("label")));
                else keys.add(new SimpleKeyBean((String) p.get("click")));
              } else {
                final Map<String, List<String>> symbolMaps =
                    SchemaManager.getActiveSchema().getSymbols();
                for (Map.Entry<String, String> entry : p.entrySet()) {
                  if (symbolMaps != null && symbolMaps.containsKey(entry.getValue()))
                    keys.add(new SimpleKeyBean(entry.getValue(), entry.getKey()));
                }
              }
            }

            i++;
          }
          addTab(name, type, keys);
        }
      }
    } else {
      if (obj == null) addTab(name, type, "1");
      addTab(name, type, (String) obj);
    }
  }

  public List<SimpleKeyBean> select(int tabIndex) {
    selected = tabIndex;
    if (tabIndex >= tabTags.size()) return keyboardData;
    TabTag tag = tabTags.get(tabIndex);
    if (tag.type == SymbolKeyboardType.TABS) tabSwitchPosition = selected;
    return keyboards.get(tabIndex);
  }

  public int getSelected() {
    return selected;
  }

  public int getSelectedOrZero() {
    return (selected == -1) ? 0 : selected;
  }

  public void setTabExited() {
    this.selected = -1;
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
