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
import android.widget.Toast;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.Comparator;

public class SchemaDialog {
  boolean[] checkedSchemaItems;
  String[] schemaItems;
  AlertDialog dialog;

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
    List<Map<String,String>> schemas = Rime.get_available_schema_list();
    if (schemas == null || schemas.size() == 0) {
      Toast.makeText(context, R.string.no_schemas, Toast.LENGTH_LONG).show();
      return;
    }
    Collections.sort(schemas, new SortByName());
    List<Map<String,String>> selected_schemas = Rime.get_selected_schema_list();
    List<String> selected_Ids = new ArrayList<String>();
    int n = schemas.size();
    String[] schemaNames = new String[n];
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
    dialog = new AlertDialog.Builder(context)
      .setTitle(R.string.pref_schemas)
      .setCancelable(true)
      .setNegativeButton(android.R.string.cancel, null)
      .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface di, int id) {
          selectSchema();
        }
      })
      .setMultiChoiceItems(schemaNames, checkedSchemaItems, new DialogInterface.OnMultiChoiceClickListener() {
        public void onClick(DialogInterface di, int id, boolean isChecked) {
          checkedSchemaItems[id] = isChecked;
        }
      })
      .create();
  }

  public AlertDialog getDialog() {
    return dialog;
  }

  public void show() {
    if (dialog != null) dialog.show();
  }
}
