/*
 * Copyright 2016 osfans
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.osfans.trime;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Context;
import android.util.Log;

import java.util.Arrays;

/** 顯示配色方案列表 */
public class ThemeDlg {
  String[] keys;
  String[] names;
  int checked;
  AlertDialog dialog;

  public void selectTheme() {
    String theme = keys[checked].replace(".yaml","");
    Config config = Config.get();
    config.setTheme(theme);
    Trime trime = Trime.getService();
    if (trime != null) trime.initKeyboard(); //實時生效
  }

  public ThemeDlg(Context context) {
    Config config = Config.get(context);
    String theme = config.getTheme()+".yaml";
    keys = config.getThemeKeys();
    if (keys == null) return;
    Arrays.sort(keys);
    checked = Arrays.binarySearch(keys, theme);
    names = config.getThemeNames(keys);
    dialog = new AlertDialog.Builder(context)
      .setTitle(R.string.pref_themes)
      .setCancelable(true)
      .setNegativeButton(android.R.string.cancel, null)
      .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface di, int id) {
          selectTheme();
        }
      })
      .setSingleChoiceItems(names, checked, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface di, int id) {
          checked = id;
        }
      }).create();
  }

  public AlertDialog getDialog() {
    return dialog;
  }

  public void show() {
    if (dialog != null) dialog.show();
  }
}
