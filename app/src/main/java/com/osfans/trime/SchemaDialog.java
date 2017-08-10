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
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.IBinder;
import android.view.Window;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

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
    @Override
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
        @Override
        public void onClick(DialogInterface di, int id, boolean isChecked) {
          checkedSchemaItems[id] = isChecked;
        }
      });
      builder.setNegativeButton(android.R.string.cancel, null);
      builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
        @Override
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

  @Override
  protected void onPreExecute() {
  }

  @Override
  protected String doInBackground(Object... o) {
    initSchema();
    return "ok";
  }

  protected void onProgressUpdate(Object o) {
  }

  @Override
  protected void onPostExecute(Object o) {
    mProgressDialog.dismiss();
    showDialog();
  }
}
