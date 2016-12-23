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
import android.content.Intent;
import android.provider.Settings;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodInfo;
import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build.VERSION_CODES;
import android.os.Build.VERSION;
import android.annotation.TargetApi;

/** 配置輸入法 */
public class Pref extends PreferenceActivity {

  private final String licenseUrl = "file:///android_asset/licensing.html";
  private ProgressDialog mProgressDialog;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    if (VERSION.SDK_INT >= VERSION_CODES.M) requestPermission();
    boolean is_dark = Function.getPref(this).getBoolean("pref_ui", false);
    if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
      setTheme(is_dark ? 0x01030224 : 0x01030237);
    } else {
      setTheme(is_dark ? android.R.style.Theme_Holo : android.R.style.Theme_Holo_Light);
    }
    super.onCreate(savedInstanceState);
    addPreferencesFromResource(R.xml.prefs);

    Preference pref = findPreference("pref_librime_ver");
    pref.setSummary(Rime.get_librime_version());
    pref = findPreference("pref_opencc_ver");
    pref.setSummary(Rime.get_opencc_version());
    pref = findPreference("pref_ver");
    pref.setSummary(Function.getVersion(this));
    pref = findPreference("pref_enable");
    if (isEnabled()) getPreferenceScreen().removePreference(pref);
    mProgressDialog = new ProgressDialog(this);
    mProgressDialog.setCancelable(false);
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

  public boolean isEnabled() {
    boolean enabled = false;
    for(InputMethodInfo i: ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).getEnabledInputMethodList()) {
      if(getPackageName().contentEquals(i.getPackageName())) {
        enabled = true;
        break;
      }
    }
    return enabled;
  }

  @TargetApi(VERSION_CODES.M)
  public void requestPermission() {
    if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
      requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, 0);
    }
  }

  public void deployOpencc() {
    boolean b = Config.deployOpencc();
  }

  @Override
  public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
    boolean b;
    switch (preference.getKey()) {
      case "pref_enable": //啓用
        if (!isEnabled()) startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
        return true;
      case "pref_select": //切換
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker();
        return true;
      case "pref_themes": //主題
        new ThemeDlg(this);
        return true;
      case "pref_colors": //配色
        new ColorDialog(this).show();
        return true;
      case "pref_schemas": //方案
        new SchemaDialog(this);
        return true;
      case "pref_maintenance": //維護
        Function.check();
        return true;
      case "pref_deploy_opencc": //部署OpenCC
        deployOpencc();
        return true;
      case "pref_deploy": //部署
        mProgressDialog.setMessage(getString(R.string.deploy_progress));
        mProgressDialog.show();
        new Thread(new Runnable(){
          @Override
          public void run() {
            try{
              Function.deploy();
            }
            catch(Exception e){
            }
            finally{
              mProgressDialog.dismiss();
              System.exit(0); //清理內存
            }
          }
        }).start();
        return true;
      case "pref_sync": //同步
        mProgressDialog.setMessage(getString(R.string.sync_progress));
        mProgressDialog.show();
        new Thread(new Runnable(){
          @Override
          public void run() {
            try{
              Function.sync();
            }
            catch(Exception e){
            }
            finally{
              mProgressDialog.dismiss();
              System.exit(0); //清理內存
            }
          }
        }).start();
        return true;
      case "pref_reset": //回廠
        new ResetDialog(this).show();
        return true;
      case "pref_licensing": //資訊
        showLicenseDialog();
        return true;
      case "pref_ui": //色調
        finish();
        Function.showPrefDialog(this);
        return true;
    }
    return false;
  }
}
