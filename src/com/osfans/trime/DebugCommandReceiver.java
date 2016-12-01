package com.osfans.trime;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 
 * @author boboIqiqi
 * 
 * 用于接收，使用PC编辑Yaml文件时，通过adb命令发送的广播命令
 * 
 * 发送命令如下：
 *  adb shell am broadcast -a com.osfans.trime.deploy
 */
public class DebugCommandReceiver extends BroadcastReceiver {
	
	private static final String TAG = "DebugCommandReceiver";
	public static final String COMMAND_DEPLOY = "com.osfans.trime.deploy";
	
	
	@Override
	public void onReceive(Context context, Intent intent) {
		String command = intent.getAction();
		
		Log.d(TAG,"Receive Command = " + command);
		
		//防止为空，虽然很少,但是可能会出现
		//http://stackoverflow.com/questions/15048883/intent-getaction-is-returning-null
		if(command == null) return;
		
		switch(command){
		case COMMAND_DEPLOY:
			Function.deploy();
			break;
		}
	}

}
