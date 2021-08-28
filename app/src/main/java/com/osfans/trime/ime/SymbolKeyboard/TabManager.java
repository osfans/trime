package com.osfans.trime.ime.SymbolKeyboard;

import com.osfans.trime.ime.enums.KeyCommandType;
import com.osfans.trime.ime.enums.SymbolKeyboardType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TabManager {
  private int selected;
  private final List<SimpleKeyBean> keyboard;
  private final List<TabTag> tabTags;
  private final List<List<SimpleKeyBean>> keyboards;
  private static TabManager self;
  private final List<SimpleKeyBean> notKeyboard = new ArrayList<>();
  private final TabTag tagExit = new TabTag("返回", SymbolKeyboardType.NO_KEY, KeyCommandType.EXIT);

  public static TabManager get() {
    if (null == self) self = new TabManager();
    return self;
  }

  public static void clear() {
    self = new TabManager();
  }

  public static TabTag getTag(int i) {
    return self.tabTags.get(i);
  }

  private TabManager() {
    selected = 0;
    tabTags = new ArrayList<>();
    keyboards = new ArrayList<>();
    keyboard = new ArrayList<>();
  }

  public void addTab(String name, SymbolKeyboardType type, List<SimpleKeyBean> keyBeans) {
    if (name.trim().length() < 1) return;

    if (SymbolKeyboardType.Companion.needKeys(type)) {
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

    if (type == SymbolKeyboardType.SINGLE) {
      addTab(name, type, SimpleKeyDao.Single(string));
    } else if (type == SymbolKeyboardType.NO_KEY) {
      KeyCommandType command = KeyCommandType.Companion.fromString(string);
      tabTags.add(new TabTag(name, type, command));
      keyboards.add(notKeyboard);
    } else addTab(name, type, SimpleKeyDao.SimpleKeyboard(string));
  }

  // 解析config的数据
  public void addTab(String name, SymbolKeyboardType type, Object obj) {
    if (SymbolKeyboardType.Companion.needKeys(type)) {
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
              Map<?, ?> p = (Map<?, ?>) o;
              if (p.containsKey("click")) {
                if (p.containsKey("label"))
                  keys.add(new SimpleKeyBean((String) p.get("click"), (String) p.get("label")));
                else keys.add(new SimpleKeyBean((String) p.get("click")));
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
    if (tabIndex >= tabTags.size()) return keyboard;
    selected = tabIndex;
    return keyboards.get(tabIndex);
  }

  public int getSelected() {
    return selected;
  }

  public TabTag[] getTabCanditates() {
    boolean add_exit = true;
    for (TabTag tag : tabTags) {
      if (tag.command == KeyCommandType.EXIT) {
        add_exit = false;
        break;
      }
    }
    if (add_exit) {
      tabTags.add(tagExit);
      keyboards.add(notKeyboard);
    }
    return tabTags.toArray(new TabTag[0]);
  }
}
