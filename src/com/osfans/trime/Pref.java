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
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.ListPreference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.util.Log;
import android.widget.Toast;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Manages IME preferences. 
 */
public class Pref extends PreferenceActivity {

  private final String licenseUrl = "file:///android_asset/licensing.html";

  boolean[] checkedSchemaItems;
  String[] schemaItems;
  DialogInterface.OnClickListener selectSchemasListener = new DialogInterface.OnClickListener() {
    public void onClick(DialogInterface di, int id) {
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
        deploy();
      }
      di.dismiss();
    }
  };
  DialogInterface.OnMultiChoiceClickListener checkSchemasListener = new DialogInterface.OnMultiChoiceClickListener() {
    public void onClick(DialogInterface di, int id, boolean isChecked) {
      checkedSchemaItems[id] = isChecked;
    }
  };

  public String getVersion() {
    try {
      return this.getPackageManager().getPackageInfo(this.getPackageName(), 0).versionName;
    } catch (Exception e) {
      return null;
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    addPreferencesFromResource(R.xml.prefs);

    Preference pref = findPreference("pref_librime_ver");
    pref.setSummary(Rime.get_version());
    pref = findPreference("pref_opencc_ver");
    pref.setSummary(Rime.get_opencc_version());
    pref = findPreference("pref_ver");
    pref.setSummary(getVersion());
  }

  private void showLicenseDialog() {
    View licenseView = View.inflate(this, R.layout.licensing, null);
    WebView webView = (WebView) licenseView.findViewById(R.id.license_view);
    webView.setWebViewClient(new WebViewClient() {
      @Override
      public boolean shouldOverrideUrlLoading(WebView view, String url) {
        // Disable all links open from this web view.
        return true;
      }
    });
    webView.loadUrl(licenseUrl);

    new AlertDialog.Builder(this)
      .setTitle(R.string.ime_name)
      .setView(licenseView)
      .show();
  }

  private void selectSchemas() {
    List schemas = Rime.get_available_schema_list();
    if (schemas == null || schemas.size() == 0) return;
    List selected_schemas = Rime.get_selected_schema_list();
    List<String> selected_Ids = new ArrayList<String>();
    int n = schemas.size();
    String[] schemaNames = new String[n];
    String schema_id;
    checkedSchemaItems = new boolean[n];
    schemaItems = new String[n];
    int i = 0;
    if (selected_schemas.size() > 0) {
      for (Object o: selected_schemas) {
        Map<String, String> m = (Map<String, String>)o;
        selected_Ids.add(m.get("schema_id"));
      }
    }
    for (Object o: schemas) {
      Map<String, String> m = (Map<String, String>)o;
      schemaNames[i] = m.get("name");
      schema_id = m.get("schema_id");
      schemaItems[i] = schema_id;
      checkedSchemaItems[i] = selected_Ids.contains(schema_id);
      i++;
    }
    new AlertDialog.Builder(this)
      .setTitle(R.string.pref_schemas)
      .setCancelable(true)
      .setNegativeButton(android.R.string.cancel, null)
      .setPositiveButton(android.R.string.ok, selectSchemasListener)
      .setMultiChoiceItems(schemaNames, checkedSchemaItems, checkSchemasListener)
      .show();
  }

  public void deploy() {
    Rime.getRime().finalize1();
    Rime.getRime().init(true);
    Trime trime = Trime.getService();
    if (trime != null) trime.invalidate();
  }

  @Override
  public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
    boolean b;
    switch (preference.getKey()) {
      case "pref_schemas": //方案
        selectSchemas();
        return true;
      case "pref_maintenance": //維護
        Rime.getRime().check(true);
        return true;
      case "pref_deploy": //部署
        deploy();
        return true;
      case "pref_sync": //同步
        b = Rime.getRime().sync_user_data();
        Rime.getRime().finalize1();
        Rime.getRime().init(false);
        Toast.makeText(this, b ? R.string.sync_success : R.string.sync_failure, Toast.LENGTH_SHORT).show();
        return true;
      case "pref_reset": //回廠
        b = Config.copyFile(this, "rime/trime.yaml", true);
        Toast.makeText(this, b ? R.string.reset_success : R.string.reset_failure, Toast.LENGTH_SHORT).show();
        return true;
      case "pref_licensing": //資訊
        showLicenseDialog();
        return true;
    }
    return false;
  }
}
