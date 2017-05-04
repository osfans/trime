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
  List<Map<String,String>> schemas;
  List<Map<String,String>> selected_schemas;
  String[] schemaNames;
  Context mContext;
  IBinder mToken;
  ProgressDialog mProgressDialog;
  AlertDialog mDialog;

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
      Function.deploy();
    }
  }

  private void showProgressDialog() {
    mProgressDialog = new ProgressDialog(mContext);
    mProgressDialog.setMessage(mContext.getString(R.string.schemas_progress));
    mProgressDialog.setCancelable(false);
    if (mToken != null) {
      Window window = mProgressDialog.getWindow();
      WindowManager.LayoutParams lp = window.getAttributes();
      lp.token = mToken;
      lp.type = WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG;
      window.setAttributes(lp);
      window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
    }
    mProgressDialog.show();
  }

  public SchemaDialog(Context context) {
    this(context, null);
  }

  public SchemaDialog(Context context, IBinder token) {
    mContext = context;
    mToken = token;
    showProgressDialog();
    execute();
  }

  private void initSchema() {
    schemas = Rime.get_available_schema_list();
    if (schemas == null || schemas.size() == 0) {
      //不能在線程中使用Toast
      //Toast.makeText(mContext, R.string.no_schemas, Toast.LENGTH_LONG).show();
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

  public void showDialog() {
    AlertDialog.Builder builder = new AlertDialog.Builder(mContext)
      .setTitle(R.string.pref_schemas)
      .setCancelable(true)
      .setPositiveButton(android.R.string.ok, null);
    if (schemas == null || schemas.size() == 0) {
      builder.setMessage(R.string.no_schemas);
    } else {
      builder.setMultiChoiceItems(schemaNames, checkedSchemaItems, new DialogInterface.OnMultiChoiceClickListener() {
        public void onClick(DialogInterface di, int id, boolean isChecked) {
          checkedSchemaItems[id] = isChecked;
        }
      });
      builder.setNegativeButton(android.R.string.cancel, null);
      builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface di, int id) {
          mProgressDialog.setMessage(mContext.getString(R.string.deploy_progress));
          mProgressDialog.show();
          new Thread(new Runnable(){
            @Override
            public void run() {
              try{
                selectSchema();
              }
              catch(Exception e){
              }
              finally{
                mProgressDialog.dismiss();
                System.exit(0); //清理內存
              }
            }
          }).start();
        }
      });
    }
    mDialog = builder.create();
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
  }

  protected String doInBackground(Object... o) {
    initSchema();
    return "ok";
  }

  protected void onProgressUpdate(Object o) {
  }

  protected void onPostExecute(Object o) {
    mProgressDialog.dismiss();
    showDialog();
  }
}
