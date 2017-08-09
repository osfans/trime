/**
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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;

import java.util.Arrays;

/** 顯示配色方案列表 */
public class ColorDialog {
  String[] colorKeys;
  String[] colorNames;
  int checkedColor;
  AlertDialog dialog;

  public void selectColor() {
    String color = colorKeys[checkedColor];
    Config config = Config.get();
    config.setColor(color);
    Trime trime = Trime.getService();
    if (trime != null) trime.initKeyboard(); //實時生效
  }

  public ColorDialog(Context context) {
    Config config = Config.get(context);
    String colorScheme = config.getColorScheme();
    colorKeys = config.getColorKeys();
    if (colorKeys == null) return;
    Arrays.sort(colorKeys);
    checkedColor = Arrays.binarySearch(colorKeys, colorScheme);
    colorNames = config.getColorNames(colorKeys);
    dialog = new AlertDialog.Builder(context)
      .setTitle(R.string.pref_colors)
      .setCancelable(true)
      .setNegativeButton(android.R.string.cancel, null)
      .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface di, int id) {
          selectColor();
        }
      })
      .setSingleChoiceItems(colorNames, checkedColor, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface di, int id) {
          checkedColor = id;
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
