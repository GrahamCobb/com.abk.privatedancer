package com.abk.privatedancer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

/**
 * Broadcast Reciever that will start the Private Dancer service if configured to do so.
 * 
 * @author kgilmer
 *
 */
public class BootCompletedBroadcastReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		String key = context.getString(R.string.start_service_on_boot_key);
		
		if (prefs.getBoolean(key, true)) {		
			context.startService(new Intent(context, PrivateDancerUpnpServiceImpl.class));
		}
	}
}
