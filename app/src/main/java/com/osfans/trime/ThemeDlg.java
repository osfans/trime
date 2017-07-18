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
import android.os.AsyncTask;
import android.os.IBinder;
import android.app.ProgressDialog;
import android.view.Window;
import android.view.WindowManager;

import java.util.Arrays;

/** 顯示配色方案列表 */
public class ThemeDlg extends AsyncTask{
  String[] keys;
  String[] names;
  int checked;
  AlertDialog mDialog;
  ProgressDialog mProgressDialog;
  Context mContext;
  IBinder mToken;

  public void selectTheme() {
    String theme = keys[checked].replace(".yaml","");
    Config config = Config.get();
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
      lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
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
    String theme = config.getTheme()+".yaml";
    keys = config.getThemeKeys();
    if (keys == null) return;
    Arrays.sort(keys);
    checked = Arrays.binarySearch(keys, theme);
    names = config.getThemeNames(keys);
    showDialog();
    initProgressDialog();
  }

  public void showDialog() {
    mDialog = new AlertDialog.Builder(mContext)
      .setTitle(R.string.pref_themes)
      .setCancelable(true)
      .setNegativeButton(android.R.string.cancel, null)
      .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface di, int id) {
          execute();
        }
      })
      .setSingleChoiceItems(names, checked, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface di, int id) {
          checked = id;
        }
      }).create();
    if (mToken != null) {
      Window window = mDialog.getWindow();
      WindowManager.LayoutParams lp = window.getAttributes();
      lp.token = mToken;
      lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
      window.setAttributes(lp);
      window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
    }
    mDialog.show();
  }

  protected void onPreExecute() {
    mProgressDialog.show();
  }

  protected String doInBackground(Object... o) {
    selectTheme();
    return "ok";
  }

  protected void onProgressUpdate(Object o) {
  }

  protected void onPostExecute(Object o) {
    mProgressDialog.dismiss();
    Trime trime = Trime.getService();
    if (trime != null) trime.initKeyboard(); //實時生效
  }
}
