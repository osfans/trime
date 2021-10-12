package com.osfans.trime.ime.symbol;

import androidx.annotation.NonNull;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

public class SimpleKeyDao {

  @NonNull
  public static List<SimpleKeyBean> SimpleKeyboard(@NonNull String string) {
    String[] strings = string.split("\n+");
    List<SimpleKeyBean> list = new ArrayList<>();
    for (String str : strings) {
      if (str.length() < 1) continue;
      SimpleKeyBean keyBean = new SimpleKeyBean(str);
      list.add(keyBean);
    }
    return list;
  }

  @NonNull
  public static List<SimpleKeyBean> Single(@NonNull String string) {
    List<SimpleKeyBean> list = new ArrayList<>();

    char h = 0;

    for (int i = 0; i < string.length(); i++) {
      char c = string.charAt(i);
      if (c >= '\uD800' && c <= '\udbff') h = c;
      else if (c >= '\udc00' && c <= '\udfff')
        list.add(new SimpleKeyBean(String.valueOf(new char[] {h, c})));
      else list.add(new SimpleKeyBean(Character.toString(c)));
    }
    return list;
  }

  @NonNull
  public static List<SimpleKeyBean> getSymbolKeyHistory(String path) {
    List<SimpleKeyBean> list = new ArrayList<>();

    ObjectInputStream ois = null;
    try {
      ois = new ObjectInputStream(new FileInputStream(path));
      Object object = ois.readObject();
      if (object != null) {
        if (object instanceof ArrayList<?>) {
          for (Object o : (List<?>) object) {
            list.add((SimpleKeyBean) o);
          }
        }
      }

    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      try {
        if (ois != null) {
          ois.close();
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    return list;
  }

  public static void saveSymbolKeyHistory(String path, @NonNull List<SimpleKeyBean> list) {
    ObjectOutputStream fos = null;
    List<String> text_list = new ArrayList<>();
    List<SimpleKeyBean> cache = new ArrayList<>();
    for (SimpleKeyBean bean : list) {
      String text = bean.getText();
      if (!text_list.contains(text)) {
        text_list.add(text);
        cache.add(bean);
        if (cache.size() > 180) break;
      }
    }

    try {
      File file = new File(path);
      fos = new ObjectOutputStream(new FileOutputStream(file));
      fos.writeObject(cache);
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      try {
        if (fos != null) {
          fos.close();
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }
}
