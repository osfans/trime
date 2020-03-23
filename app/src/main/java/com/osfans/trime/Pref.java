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

package com.osfans.trime;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.ocpsoft.prettytime.PrettyTime;

import java.util.Date;

/** 配置輸入法 */
public class Pref extends PreferenceActivity
    implements SharedPreferences.OnSharedPreferenceChangeListener {

  private static String TAG = Pref.class.getSimpleName();
  private ProgressDialog mProgressDialog;
  private Preference mKeySoundVolumePref, mKeyVibrateDurationPref,mKeyVibrateAmplitudePref;

  private String getCommit(String version) {
    String commit;
    if (version.contains("-g")) {
      commit = version.replaceAll("^(.*-g)([0-9a-f]+)(.*)$", "$2");
    } else {
      commit = version.replaceAll("^([^-]*)(-.*)$", "$1");
    }
    return commit;
  }

  private void setVersion(Preference pref, String version) {
    String commit = getCommit(version);
    pref.setSummary(version);
    Intent intent = pref.getIntent();
    intent.setData(Uri.withAppendedPath(intent.getData(), "commits/"+commit));
    pref.setIntent(intent);
  }

  private void setBackgroundSyncSummary(Context context) { //设置后台同步的summary文字
    SwitchPreference sp = ((SwitchPreference)findPreference("pref_sync_bg"));
    if(context==null){ //当前没有 Trime 服务
      if(sp.isChecked()){
        sp.setSummaryOn(R.string.pref_sync_bg_never);
      }else {
        sp.setSummaryOff(R.string.pref_sync_bg_tip);
      }
    }else {
      String summary = context.getString(R.string.pref_sync_bg_tip);

      if (sp.isChecked()) { // 后台同步功能开启
        boolean success = Function.getPref(context).getBoolean("last_sync_status", false); // 上次同步状态
        summary = success ? context.getString(R.string.pref_sync_bg_success) : context.getString(R.string.pref_sync_bg_failure);
        long time = Function.getPref(context).getLong("last_sync_time", 0); // 上次同步时间
        if (time == 0) {
          summary = context.getString(R.string.pref_sync_bg_tip);
        } else {
          summary = String.format(summary, new PrettyTime().format(new Date(time))); //使用PrettyTime包展示相对时间
        }
        sp.setSummaryOn(summary);
      }else {
        sp.setSummaryOff(summary);
      }
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    if (VERSION.SDK_INT >= VERSION_CODES.M) requestPermission();
    SharedPreferences prefs = Function.getPref(this);
    boolean is_dark = prefs.getBoolean("pref_ui", false);
    if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
      setTheme(is_dark ? android.R.style.Theme_Material : android.R.style.Theme_Material_Light);
    } else {
      setTheme(is_dark ? android.R.style.Theme_Holo : android.R.style.Theme_Holo_Light);
    }
    super.onCreate(savedInstanceState);
    addPreferencesFromResource(R.xml.prefs);

    Preference pref;
    String version;

    pref = findPreference("pref_changelog");
    version = Rime.get_trime_version();
    setVersion(pref, version);

    pref = findPreference("pref_librime_ver");
    version = Rime.get_librime_version();
    setVersion(pref, version);

    pref = findPreference("pref_opencc_ver");
    version = Rime.get_opencc_version();
    setVersion(pref, version);

    pref = findPreference("pref_enable");
    if (isEnabled()) getPreferenceScreen().removePreference(pref);
    mProgressDialog = new ProgressDialog(this);
    mProgressDialog.setCancelable(false);
    mKeySoundVolumePref = findPreference("key_sound_volume");
    mKeyVibrateDurationPref = findPreference("key_vibrate_duration");
    mKeyVibrateAmplitudePref = findPreference("key_vibrate_amplitude");
    mKeySoundVolumePref.setEnabled(prefs.getBoolean("key_sound", false));
    mKeyVibrateDurationPref.setEnabled(prefs.getBoolean("key_vibrate", false));
    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        mKeyVibrateAmplitudePref.setEnabled(prefs.getBoolean("key_vibrate", false));
    }
    else {
        mKeyVibrateAmplitudePref.setEnabled(false);
    }
    boolean isQQInstalled = Function.isAppAvailable(this, "com.tencent.mobileqq")
                         || Function.isAppAvailable(this, "com.tencent.mobileqqi")
                         || Function.isAppAvailable(this, "com.tencent.qq.kddi")
                         || Function.isAppAvailable(this, "com.tencent.minihd.qq")
                         || Function.isAppAvailable(this, "com.tencent.qqlite")
                         || Function.isAppAvailable(this, "com.tencent.tim");
    pref = findPreference("pref_trime_qq");
    pref.setSelectable(isQQInstalled);
    pref = findPreference("pref_trime_qq2");
    pref.setSelectable(isQQInstalled);
    pref = findPreference("pref_rime_qq");
    pref.setSelectable(isQQInstalled);
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
    Trime trime = Trime.getService();
    boolean value;
    switch (key) {
      case "key_sound":
        if (trime != null) trime.resetEffect();
        value = prefs.getBoolean(key, false);
        mKeySoundVolumePref.setEnabled(value);
        break;
      case "key_vibrate":
        if (trime != null) trime.resetEffect();
        value = prefs.getBoolean(key, false);
        boolean isapi26andabove = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
        mKeyVibrateDurationPref.setEnabled(value);
        mKeyVibrateAmplitudePref.setEnabled(isapi26andabove && value);
        break;
      case "key_sound_volume":
        if (trime != null) {
          trime.resetEffect();
          trime.soundEffect();
        }
        break;
      case "key_vibrate_duration":
      case "key_vibrate_amplitude":
        if (trime != null) {
          trime.resetEffect();
          trime.vibrateEffect();
        }
        break;
      case "speak_key":
      case "speak_commit":
        if (trime != null) trime.resetEffect();
        break;
      case "longpress_timeout":
      case "repeat_interval":
      case "show_preview":
        if (trime != null) trime.resetKeyboard();
        break;
      case "show_window":
        if (trime != null) trime.resetCandidate();
        break;
      case "inline_preedit":
      case "soft_cursor":
        if (trime != null) trime.loadConfig();
        break;
      case "pref_notification_icon": //通知欄圖標
        value = prefs.getBoolean(key, false);
        if (trime != null) {
          if (value) trime.showStatusIcon(R.drawable.status);
          else trime.hideStatusIcon();
        }
        break;
      case "show_switches": //候選欄顯示狀態
        value = prefs.getBoolean(key, false);
        Rime.setShowSwitches(value);
        break;
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    setBackgroundSyncSummary(Trime.getService());
  }

  @Override
  protected void onPause() {
    super.onPause();
    getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
  }

  private void showLicenseDialog() {
    View licenseView = View.inflate(this, R.layout.licensing, null);
    WebView webView = (WebView) licenseView.findViewById(R.id.license_view);
    webView.setWebViewClient(
        new WebViewClient() {
          @Override
          public boolean shouldOverrideUrlLoading(WebView view, String url) {
            // Disable all links open from this web view.
            return true;
          }
        });
    String licenseUrl = "file:///android_asset/licensing.html";
    webView.loadUrl(licenseUrl);

    new AlertDialog.Builder(this).setTitle(R.string.ime_name).setView(licenseView).show();
  }

  private boolean isEnabled() {
    boolean enabled = false;
    for (InputMethodInfo i :
        ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).getEnabledInputMethodList()) {
      if (getPackageName().contentEquals(i.getPackageName())) {
        enabled = true;
        break;
      }
    }
    return enabled;
  }

  @TargetApi(VERSION_CODES.M)
  private void requestPermission() {
    if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        != PackageManager.PERMISSION_GRANTED) {
      requestPermissions(
          new String[] {
            Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE
          },
          0);
    }
    if (VERSION.SDK_INT >= VERSION_CODES.P) { //僅Android P需要此權限在最上層顯示懸浮窗、對話框
      if (!Settings.canDrawOverlays(this)) {
          Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
          //startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE);
          startActivity(intent);
      }
    }
  }

  private void deployOpencc() {
    boolean b = Config.get().deployOpencc();
  }

  @Override
  public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
    boolean b;
    String key = preference.getKey();
    switch (key) {
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
        new Thread(
                new Runnable() {
                  @Override
                  public void run() {
                    try {
                      Function.deploy();
                    } catch (Exception ex) {
                      Log.e(TAG, "Deploy Exception" + ex);
                    } finally {
                      mProgressDialog.dismiss();
                      System.exit(0); //清理內存
                    }
                  }
                })
            .start();
        return true;
      case "pref_sync": //同步
        mProgressDialog.setMessage(getString(R.string.sync_progress));
        mProgressDialog.show();
        new Thread(
                new Runnable() {
                  @Override
                  public void run() {
                    try {
                      Function.sync();
                    } catch (Exception ex) {
                      Log.e(TAG, "Sync Exception" + ex);
                    } finally {
                      mProgressDialog.dismiss();
                      System.exit(0); //清理內存
                    }
                  }
                })
            .start();
        return true;
      case "pref_input":
      case "pref_sync_bg": //后台同步
        setBackgroundSyncSummary(Trime.getService());
        return true;
      case "pref_reset": //回廠
        new ResetDialog(this).show();
        return true;
      case "pref_licensing": //許可協議
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
