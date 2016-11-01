/*
 * Copyright 2015 osfans
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

import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Context;
import android.util.Log;
import android.widget.Toast;
import android.os.AsyncTask;
import android.os.IBinder;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.Comparator;

/** 顯示輸入法方案列表 */
public class SchemaDialog extends AsyncTask{
  boolean[] checkedSchemaItems;
  String[] schemaItems;
  AlertDialog.Builder builder;
  AlertDialog alertDialog;
  List<Map<String,String>> schemas;
  List<Map<String,String>> selected_schemas;
  String[] schemaNames;
  Context mContext;
  IBinder mToken;
  private ProgressDialog mDialog;

  public class SortByName implements Comparator<Map<String,String>>{
    public int compare(Map<String,String> m1, Map<String,String> m2) {
      String s1 = m1.get("schema_id");
      String s2 = m2.get("schema_id");
      return s1.compareTo(s2);
    }
  }

  private void selectSchema() {
    List<String> checkedIds = new ArrayList<String>();
    int i = 0;
    for (boolean b: checkedSchemaItems) {
      if (b) checkedIds.add(schemaItems[i]);
      i++;
    }
    int n = checkedIds.size();
    if (n > 0) {
      String[] schema_id_list = new String[n];
      checkedIds.toArray(schema_id_list);
      Rime.select_schemas(schema_id_list);
      Pref.deploy();
    }
  }

  public SchemaDialog(Context context) {
    mContext = context;
    builder = new AlertDialog.Builder(mContext)
      .setTitle(R.string.pref_schemas)
      .setCancelable(true)
      .setNegativeButton(android.R.string.cancel, null)
      .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface di, int id) {
          selectSchema();
        }
      });
    mDialog = new ProgressDialog(mContext);
    mDialog.setMessage(mContext.getString(R.string.schemas_progress));
    mDialog.setCancelable(false);
  }

  public SchemaDialog(Context context, IBinder token) {
    this(context);
    if (token != null) {
      mToken = token;
      Window window = mDialog.getWindow();
      WindowManager.LayoutParams lp = window.getAttributes();
      lp.token = mToken;
      lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
      window.setAttributes(lp);
      window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
    }
  }

  private void initSchema() {
    schemas = Rime.get_available_schema_list();
    if (schemas == null || schemas.size() == 0) {
      Toast.makeText(mContext, R.string.no_schemas, Toast.LENGTH_LONG).show();
      return;
    }
    Collections.sort(schemas, new SortByName());
    selected_schemas = Rime.get_selected_schema_list();
    List<String> selected_Ids = new ArrayList<String>();
    int n = schemas.size();
    schemaNames = new String[n];
    String schema_id;
    checkedSchemaItems = new boolean[n];
    schemaItems = new String[n];
    int i = 0;
    if (selected_schemas.size() > 0) {
      for (Map<String,String> m: selected_schemas) {
        selected_Ids.add(m.get("schema_id"));
      }
    }
    for (Map<String,String> m: schemas) {
      schemaNames[i] = m.get("name");
      schema_id = m.get("schema_id");
      schemaItems[i] = schema_id;
      checkedSchemaItems[i] = selected_Ids.contains(schema_id);
      i++;
    }
  }

  public AlertDialog getDialog() {
    builder.setMultiChoiceItems(schemaNames, checkedSchemaItems, new DialogInterface.OnMultiChoiceClickListener() {
        public void onClick(DialogInterface di, int id, boolean isChecked) {
          checkedSchemaItems[id] = isChecked;
        }
    });
    alertDialog = builder.create();
    if (mToken != null) {
      Window window = alertDialog.getWindow();
      WindowManager.LayoutParams lp = window.getAttributes();
      lp.token = mToken;
      lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
      window.setAttributes(lp);
      window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
    }
    return alertDialog;
  }

  protected void onPreExecute() {
    mDialog.show();
  }

 protected String doInBackground(Object... o) {
   initSchema();
   return "ok";
 }

 protected void onProgressUpdate(Object o) {
 }

 protected void onPostExecute(Object o) {
   getDialog().show();
   mDialog.dismiss();
 }

}
