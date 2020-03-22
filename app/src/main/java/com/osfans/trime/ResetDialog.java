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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.widget.Toast;

/** 顯示輸入法內置數據列表，並回廠選中的數據 */
class ResetDialog {
  /** 內置數據列表 */
  private String[] items;
  /** 列表勾選狀態 */
  private boolean[] checked;
  /** 回廠對話框 */
  private AlertDialog dialog;

  private Context context;

  /** 回廠選中的數據 */
  private void select() {
    if (items == null) return;
    boolean ret = true;
    int n = items.length;
    for (int i = 0; i < n; i++) {
      if (checked[i]) {
        ret = Config.get(context).copyFileOrDir(context, items[i], true);
      }
    }
    Toast.makeText(
            context, ret ? R.string.reset_success : R.string.reset_failure, Toast.LENGTH_SHORT)
        .show();
  }

  public ResetDialog(Context context) {
    this.context = context;
    items = Config.list(context, "rime");
    if (items == null) return;
    checked = new boolean[items.length];
    dialog =
        new AlertDialog.Builder(context)
            .setTitle(R.string.pref_reset)
            .setCancelable(true)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(
                android.R.string.ok,
                new DialogInterface.OnClickListener() {
                  @Override
                  public void onClick(DialogInterface di, int id) {
                    select();
                  }
                })
            .setMultiChoiceItems(
                items,
                checked,
                new DialogInterface.OnMultiChoiceClickListener() {
                  @Override
                  public void onClick(DialogInterface di, int id, boolean isChecked) {
                    checked[id] = isChecked;
                  }
                })
            .create();
  }

  /**
   * 獲得回廠對話框
   *
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
