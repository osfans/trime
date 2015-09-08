/*
 * Copyright 2010 Google Inc.
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
    if (trime != null) trime.reset();
  }

  public ColorDialog(Context context) {
    Config config = Config.get(context);
    String colorScheme = config.getString("color_scheme");
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
