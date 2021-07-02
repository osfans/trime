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

import android.annotation.TargetApi;
import android.app.SearchManager;
import android.content.ComponentName;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.icu.util.Calendar;
import android.icu.util.ULocale;
import android.net.Uri;
import android.os.Build.*;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.SparseArray;
import android.view.KeyEvent;

import com.osfans.trime.settings.PrefMainActivity;

import java.text.FieldPosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 實現打開指定程序、打開{@link PrefMainActivity 輸入法全局設置}對話框等功能 */
public class Function {
  private static String TAG = Function.class.getSimpleName();
  private static SparseArray<String> sApplicationLaunchKeyCategories;

  static {
    sApplicationLaunchKeyCategories = new SparseArray<String>();
    sApplicationLaunchKeyCategories.append(
        KeyEvent.KEYCODE_EXPLORER, "android.intent.category.APP_BROWSER");
    sApplicationLaunchKeyCategories.append(
        KeyEvent.KEYCODE_ENVELOPE, "android.intent.category.APP_EMAIL");
    sApplicationLaunchKeyCategories.append(207, "android.intent.category.APP_CONTACTS");
    sApplicationLaunchKeyCategories.append(208, "android.intent.category.APP_CALENDAR");
    sApplicationLaunchKeyCategories.append(209, "android.intent.category.APP_EMAIL");
    sApplicationLaunchKeyCategories.append(210, "android.intent.category.APP_CALCULATOR");
  }

  @TargetApi(VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
  public static boolean openCategory(Context context, int keyCode) {
    String category = sApplicationLaunchKeyCategories.get(keyCode);
    if (category != null) {
      Intent intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, category);
      intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY);
      try {
        context.startActivity(intent);
      } catch (Exception ex) {
        Log.e(TAG, "Start Activity Exception" + ex);
      }
      return true;
    }
    return false;
  }

  private static void startIntent(Context context, String arg) {
    Intent intent;
    try {
      if (arg.indexOf(':') >= 0) {
        // The argument is a URI.  Fully parse it, and use that result
        // to fill in any data not specified so far.
        intent = Intent.parseUri(arg, Intent.URI_INTENT_SCHEME);
      } else if (arg.indexOf('/') >= 0) {
        // The argument is a component name.  Build an Intent to launch
        // it.
        intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setComponent(ComponentName.unflattenFromString(arg));
      } else {
        // Assume the argument is a package name.
        intent = context.getPackageManager().getLaunchIntentForPackage(arg);
      }
      intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY);
      context.startActivity(intent);
    } catch (Exception ex) {
      Log.e(TAG, "Start Activity Exception" + ex);
    }
  }

  private static void startIntent(Context context, String action, String arg) {
    action = "android.intent.action." + action.toUpperCase(Locale.getDefault());
    try {
      Intent intent = new Intent(action);
      switch (action) {
        case Intent.ACTION_WEB_SEARCH:
        case Intent.ACTION_SEARCH:
          if (arg.startsWith("http")) { //web_search無法直接打開網址
            startIntent(context, arg);
            return;
          }
          intent.putExtra(SearchManager.QUERY, arg);
          break;
        case Intent.ACTION_SEND: //分享文本
          intent.setType("text/plain");
          intent.putExtra(Intent.EXTRA_TEXT, arg);
          break;
        default:
          if (!isEmpty(arg)) intent.setData(Uri.parse(arg));
          break;
      }
      intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY);
      context.startActivity(intent);
    } catch (Exception ex) {
      Log.e(TAG, "Start Activity Exception" + ex);
    }
  }

  public static void showPrefDialog(Context context) {
    Intent intent = new Intent(context, PrefMainActivity.class);
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY);
    context.startActivity(intent);
  }

  private static String getDate(String option) {
    String s = "";
    String locale = "";
    if (option.contains("@")) {
      String[] ss = option.split(" ", 2);
      if (ss.length == 2 && ss[0].contains("@")) {
        locale = ss[0];
        option = ss[1];
      } else if (ss.length == 1) {
        locale = ss[0];
        option = "";
      }
    }
    if (VERSION.SDK_INT >= VERSION_CODES.N && !isEmpty(locale)) {
      ULocale ul = new ULocale(locale);
      Calendar cc = Calendar.getInstance(ul);
      android.icu.text.DateFormat df;
      if (isEmpty(option)) {
        df = android.icu.text.DateFormat.getDateInstance(android.icu.text.DateFormat.LONG, ul);
      } else {
        df = new android.icu.text.SimpleDateFormat(option, ul);
      }
      s = df.format(cc, new StringBuffer(256), new FieldPosition(0)).toString();
    } else {
      s = new SimpleDateFormat(option, Locale.getDefault()).format(new Date()); //時間
    }
    return s;
  }

  private static String getClipboard(Context context) {
    ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
    ClipData primaryClip = clipboard.getPrimaryClip();
    if (primaryClip == null) {
      return "";
    }
    ClipData.Item item = primaryClip.getItemAt(0);
    if (item == null) {
      return "";
    }
    CharSequence pasteData = item.getText();
    if (pasteData != null) {
      return pasteData.toString();
    } else {
      return "";
    }
  }

  public static String handle(Context context, String command, String option) {
    String s = null;
    if (command == null) return s;
    switch (command) {
      case "date":
        s = getDate(option);
        break;
      case "run":
        startIntent(context, option); //啓動程序
        break;
      case "broadcast":
        context.sendBroadcast(new Intent(option)); //廣播
        break;
      case "clipboard":
        s = getClipboard(context);
        break;
      default:
        startIntent(context, command, option); //其他intent
        break;
    }
    return s;
  }

  public static boolean isEmpty(CharSequence s) {
    return (s == null) || (s.length() == 0);
  }

  public static void check() {
    Rime.check(true);
    System.exit(0); //清理內存
  }

  public static void deploy(Context context) {
    Rime.destroy();
    Rime.get(context, true);
    //Trime trime = Trime.getService();
    //if (trime != null) trime.invalidate();
  }

  public static void sync(Context context) {
    Rime.syncUserData(context);
  }

  public static void syncBackground(Context ctx){
    boolean success = Rime.syncUserData(ctx);
    getPref(ctx).edit() //记录同步时间和状态
            .putLong("last_sync_time",new Date().getTime())
            .putBoolean("last_sync_status",success)
            .apply();
  }

  public static String getVersion(Context context) {
    try {
      return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
    } catch (Exception e) {
      return null;
    }
  }

  public static boolean isAppAvailable(Context context, String app) {
    final PackageManager packageManager = context.getPackageManager();
    List<PackageInfo> pinfo = packageManager.getInstalledPackages(0);
    if (pinfo != null) {
      for (int i = 0; i < pinfo.size(); i++) {
        String pn = pinfo.get(i).packageName;
        if (pn.equals(app)) {
          return true;
        }
      }
    }
    return false;
  }

  public static SharedPreferences getPref(Context context) {
    return PreferenceManager.getDefaultSharedPreferences(context);
  }

  public static boolean isDiffVer(Context context) {
    String version = getVersion(context);
    SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
    String pref_ver = pref.getString("version_name", "");
    boolean isDiff = !version.contentEquals(pref_ver);
    if (isDiff) {
      SharedPreferences.Editor edit = pref.edit();
      edit.putString("version_name", version);
      edit.apply();
    }
    return isDiff;
  }
}
