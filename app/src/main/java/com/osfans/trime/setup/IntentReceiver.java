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

package com.osfans.trime.setup;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.osfans.trime.Rime;
import com.osfans.trime.util.RimeUtils;
import timber.log.Timber;

/** 接收 Intent 廣播事件 */
public class IntentReceiver extends BroadcastReceiver {
  private static final String TAG = "IntentReceiver";
  private static final String COMMAND_DEPLOY = "com.osfans.trime.deploy";
  private static final String COMMAND_SYNC = "com.osfans.trime.sync";

  @Override
  public void onReceive(@NonNull Context context, @NonNull Intent intent) {
    final @Nullable String command = intent.getAction();

    Timber.d("Receive Command = %s", command);
    // 防止为空，虽然很少,但是可能会出现
    // http://stackoverflow.com/questions/15048883/intent-getaction-is-returning-null
    if (command == null) return;

    switch (command) {
      case COMMAND_DEPLOY:
        RimeUtils.INSTANCE.deploy(context);
        System.exit(0);
        break;
      case COMMAND_SYNC:
        RimeUtils.INSTANCE.sync(context);
        break;
      case Intent.ACTION_SHUTDOWN:
        Rime.destroy();
        break;
      default:
        break;
    }
  }

  public void registerReceiver(@NonNull Context context) {
    context.registerReceiver(this, new IntentFilter(COMMAND_DEPLOY));
    context.registerReceiver(this, new IntentFilter(COMMAND_SYNC));
    context.registerReceiver(this, new IntentFilter(Intent.ACTION_SHUTDOWN));
  }

  public void unregisterReceiver(@NonNull Context context) {
    context.unregisterReceiver(this);
  }
}
