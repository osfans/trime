/*
 * Copyright 2015 osfans
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.osfans.trime;

import android.content.Context;
import android.content.Intent;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.os.Build;
import android.annotation.TargetApi;
import android.content.ActivityNotFoundException;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

/** 實現打開指定程序、打開{@link Pref 輸入法全局設置}對話框等功能 */
public class Function {
  private static String TAG = "Function";
  static SparseArray<String> sApplicationLaunchKeyCategories;
  static {
    sApplicationLaunchKeyCategories = new SparseArray<String>();
    sApplicationLaunchKeyCategories.append(
            KeyEvent.KEYCODE_EXPLORER, "android.intent.category.APP_BROWSER");
    sApplicationLaunchKeyCategories.append(
            KeyEvent.KEYCODE_ENVELOPE, "android.intent.category.APP_EMAIL");
    sApplicationLaunchKeyCategories.append(
            207, "android.intent.category.APP_CONTACTS");
    sApplicationLaunchKeyCategories.append(
            208, "android.intent.category.APP_CALENDAR");
    sApplicationLaunchKeyCategories.append(
            209, "android.intent.category.APP_EMAIL");
    sApplicationLaunchKeyCategories.append(
            210, "android.intent.category.APP_CALCULATOR");
  }

  @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
  public static boolean openCategory(Context context, int keyCode) {
    String category = sApplicationLaunchKeyCategories.get(keyCode);
    if (category != null) {
      Intent intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, category);
      intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY);
      try {
          context.startActivity(intent);
      } catch (ActivityNotFoundException ex) {
      }
      return true;
    }
    return false;
  }

  public static void openApp(Context context, String s) {
    Intent intent = context.getPackageManager().getLaunchIntentForPackage(s);
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY);
    try {
      context.startActivity(intent);
    } catch (ActivityNotFoundException ex) {
    }
  }

  public static void showPrefDialog(Context context) {
    Intent intent = new Intent(context, Pref.class);
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY);
    context.startActivity(intent);
  }

  public static String handle(Context context, String command, String option) {
    String s = null;
    if (command == null) return s;
    switch (command) {
      case "date":
        s = new SimpleDateFormat(option, Locale.getDefault()).format(new Date()); //時間
        break;
      case "run":
        openApp(context, option); //啓動程序
      default:
        break;
    }
    return s;
  }

  public static boolean isEmpty(String s) {
    return (s == null) || (s.length() == 0);
  }

  public static String getString(Map m, String k) {
    if (m.containsKey(k)) {
      Object o = m.get(k);
      if (o != null) return o.toString();
    }
    return "";
  }
}
