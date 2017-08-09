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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.widget.Toast;

/** 顯示輸入法內置數據列表，並回廠選中的數據 */
public class ResetDialog {
  /** 內置數據列表 */
  String[] items;
  /** 列表勾選狀態 */
  boolean[] checked;
  /** 回廠對話框 */
  AlertDialog dialog;
  Context context;

  /** 回廠選中的數據 */
  private void select() {
    if (items == null) return;
    boolean ret = true;
    int n = items.length;
    for (int i = 0; i < n; i++) {
      if (checked[i]) {
        ret = Config.copyFileOrDir(context, "rime/" + items[i], true);
      }
    }
    Toast.makeText(context, ret ? R.string.reset_success : R.string.reset_failure, Toast.LENGTH_SHORT).show();
  }

  public ResetDialog(Context context) {
    this.context = context;
    items = Config.list(context, "rime");
    if (items == null) return;
    checked = new boolean[items.length];
    dialog = new AlertDialog.Builder(context)
      .setTitle(R.string.pref_reset)
      .setCancelable(true)
      .setNegativeButton(android.R.string.cancel, null)
      .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface di, int id) {
          select();
        }
      })
      .setMultiChoiceItems(items, checked, new DialogInterface.OnMultiChoiceClickListener() {
        public void onClick(DialogInterface di, int id, boolean isChecked) {
          checked[id] = isChecked;
        }
      })
      .create();
  }

  /**
   * 獲得回廠對話框
   * @return 回廠對話框對象
   */
  public AlertDialog getDialog() {
    return dialog;
  }

  /** 彈出對話框 */
  public void show() {
    if (dialog != null) dialog.show();
  }
}
