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

/**
 * Manages IME preferences. 
 */
public class PrefActivity extends PreferenceActivity {

  private final String licenseUrl = "file:///android_asset/licensing.html";

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

    Preference pref = findPreference("pref_librimever");
    pref.setSummary(Rime.get_version());
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

  @Override
  public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
    String key = preference.getKey();
    if (key.contentEquals("pref_maintenance")) { //維護
      Rime.getRime().check(true);
      return true;
    } else if (key.contentEquals("pref_deploy")) { //部署
      Rime.getRime().finalize1();
      Rime.getRime().init(true);
      return true;
    } else if (key.contentEquals("pref_sync")) { //同步
      Rime.getRime().sync_user_data();
      Rime.getRime().finalize1();
      Rime.getRime().init(false);
      return true;
    } else if (key.contentEquals("pref_licensing")) { //資訊
      showLicenseDialog();
      return true;
    }
    return false;
  }
}
