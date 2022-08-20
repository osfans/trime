package com.osfans.trime.ime.symbol;

import androidx.annotation.NonNull;
import com.osfans.trime.core.Rime;
import com.osfans.trime.ime.enums.KeyCommandType;
import com.osfans.trime.ime.enums.SymbolKeyboardType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TabManager {
  private int selected;
  private final List<SimpleKeyBean> keyboardData;
  private final List<SimpleKeyBean> tabSwitchData;
  private final ArrayList<TabTag> tabTags;
  private int tabSwitchPosition = 0;
  private final List<List<SimpleKeyBean>> keyboards;
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
      if (SymbolKeyboardType.Companion.hasKey(tag.type))
        tabSwitchData.add(new SimpleKeyBean(tag.text));
      else tabSwitchData.add(new SimpleKeyBean(""));
    }
    return tabSwitchData;
  }

  public static void clear() {
    self = new TabManager();
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
    selected = 0;
    tabTags = new ArrayList<>();
    keyboards = new ArrayList<>();
    keyboardData = new ArrayList<>();
    tabSwitchData = new ArrayList<>();
  }

  public void addTab(@NonNull String name, SymbolKeyboardType type, List<SimpleKeyBean> keyBeans) {
    if (name.trim().length() < 1) return;

    if (SymbolKeyboardType.Companion.hasKeys(type)) {
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
                for (Map.Entry<String, String> entry : p.entrySet()) {
                  if (Rime.hasSymbols(entry.getValue()))
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
