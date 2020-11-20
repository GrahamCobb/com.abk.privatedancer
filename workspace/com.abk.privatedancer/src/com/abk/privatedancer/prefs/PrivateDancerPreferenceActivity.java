package com.abk.privatedancer.prefs;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

import com.abk.privatedancer.PrivateDancerUpnpServiceImpl;
import com.abk.privatedancer.R;

/**
 * Preferences for PD
 * @author kgilmer
 * 
 */
public class PrivateDancerPreferenceActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {
	private SharedPreferences prefs;
	private volatile boolean serviceStarted = false;
	/**
	 * Logging
	 */
	public static final String TAG = "com.abk.privatedancer";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		addPreferencesFromResource(R.xml.prefs);

		prefs = PreferenceManager.getDefaultSharedPreferences(this); //
		prefs.registerOnSharedPreferenceChangeListener(this);
		
		String enabledKey = getApplicationContext().getString(R.string.enable_service_key);
		if (prefs.getBoolean(enabledKey, false) && !serviceStarted) {
			(new Thread() {
				@Override
				public void run() {
					startService(new Intent(getApplicationContext(), PrivateDancerUpnpServiceImpl.class));
					serviceStarted  = true;
				}
			}).start();					
		}
	}
	
	@Override
	protected void onDestroy() {
		if (prefs != null)
			prefs.unregisterOnSharedPreferenceChangeListener(this);
		super.onDestroy();
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {	
		//If the 'Enable Service' option was set, start the service.  If not, service will disable itself but set the flag to false
		if (key.equals(getApplicationContext().getString(R.string.enable_service_key))) {
			if (prefs.getBoolean(key, false) && !serviceStarted) {
				startService(new Intent(getApplicationContext(), PrivateDancerUpnpServiceImpl.class));
				serviceStarted = true;
			} else if (!prefs.getBoolean(key, false)) {
				//The service will shut itself down when it gets the pref update event.
				serviceStarted = false;
			}
		}
	}

}
