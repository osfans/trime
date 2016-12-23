/*
 * Copyright 2016 osfans
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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** 接收Intent廣播事件 */
public class IntentReceiver extends BroadcastReceiver {
  private static final String TAG = "IntentReceiver";
  public static final String COMMAND_DEPLOY = "com.osfans.trime.deploy";
  public static final String COMMAND_SYNC = "com.osfans.trime.sync";

  @Override
  public void onReceive(Context ctx, Intent intent) {

    String command = intent.getAction();
    
    Log.d(TAG,"Receive Command = " + command);
    //防止为空，虽然很少,但是可能会出现
    //http://stackoverflow.com/questions/15048883/intent-getaction-is-returning-null
    if(command == null) return;

    switch(command){
    case COMMAND_DEPLOY:
      Function.deploy();
      break;
    default:
      if (command.contentEquals(intent.ACTION_BOOT_COMPLETED)) Rime.get();
      else if (command.contentEquals(intent.ACTION_SHUTDOWN)) Rime.destroy();
      break;
    }
  }
}
