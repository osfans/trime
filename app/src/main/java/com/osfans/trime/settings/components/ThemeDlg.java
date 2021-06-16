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

package com.osfans.trime.settings.components;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.IBinder;
import android.view.Window;
import android.view.WindowManager;

import com.osfans.trime.Config;
import com.osfans.trime.R;
import com.osfans.trime.Trime;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** 顯示配色方案列表 */
public class ThemeDlg extends AsyncTask {
  private String[] keys;
  private String[] names;
  private int checked;
  private ProgressDialog mProgressDialog;
  private Context mContext;
  private IBinder mToken;

  private void selectTheme() {
    String theme = keys[checked].replace(".yaml", "");
    Config config = Config.get(mContext);
    config.setTheme(theme);
  }

  private void initProgressDialog() {
    mProgressDialog = new ProgressDialog(mContext);
    mProgressDialog.setMessage(mContext.getString(R.string.themes_progress));
    mProgressDialog.setCancelable(false);
    if (mToken != null) {
      Window window = mProgressDialog.getWindow();
      WindowManager.LayoutParams lp = window.getAttributes();
      lp.token = mToken;
      lp.type = Trime.getDialogType();
      window.setAttributes(lp);
      window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
    }
  }

  public ThemeDlg(Context context) {
    this(context, null);
  }

  public ThemeDlg(Context context, IBinder token) {
    mContext = context;
    mToken = token;
    Config config = Config.get(context);
    String theme = config.getTheme() + ".yaml";
    keys = Config.getThemeKeys(context, true);
    if (keys == null) return;
    Arrays.sort(keys);
    checked = Arrays.binarySearch(keys, theme);

    Map<String, String> themeMap = new HashMap<String, String>();
    themeMap.put("tongwenfeng", mContext.getString(R.string.pref_themes_name_tongwenfeng));
    themeMap.put("trime", mContext.getString(R.string.pref_themes_name_trime));
    String[] nameArray = Config.getThemeNames(keys);
    names = new String[nameArray.length];
    for (int i = 0; i < nameArray.length; i++) {
      String themeName = themeMap.get(nameArray[i]);
      if (themeName == null) {
        names[i] = nameArray[i];
      } else {
        names[i] = themeName;
      }
    }

    showDialog();
    initProgressDialog();
  }

  private void showDialog() {
    AlertDialog mDialog =
        new AlertDialog.Builder(mContext)
            .setTitle(R.string.pref_themes)
            .setCancelable(true)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(
                android.R.string.ok,
                new DialogInterface.OnClickListener() {
                  @Override
                  public void onClick(DialogInterface di, int id) {
                    execute();
                  }
                })
            .setSingleChoiceItems(
                names,
                checked,
                new DialogInterface.OnClickListener() {
                  @Override
                  public void onClick(DialogInterface di, int id) {
                    checked = id;
                  }
                })
            .create();
    if (mToken != null) {
      Window window = mDialog.getWindow();
      WindowManager.LayoutParams lp = window.getAttributes();
      lp.token = mToken;
      lp.type = Trime.getDialogType();
      window.setAttributes(lp);
      window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
    }
    mDialog.show();
  }

  @Override
  protected void onPreExecute() {
    mProgressDialog.show();
  }

  @Override
  protected String doInBackground(Object... o) {
    selectTheme();
    return "ok";
  }

  protected void onProgressUpdate(Object o) {}

  @Override
  protected void onPostExecute(Object o) {
    mProgressDialog.dismiss();
    Trime trime = Trime.getService();
    if (trime != null) trime.initKeyboard(); //實時生效
  }
}
