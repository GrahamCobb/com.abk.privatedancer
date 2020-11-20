package com.abk.privatedancer;

import java.io.IOException;
import java.net.URI;

import org.fourthline.cling.UpnpService;
import org.fourthline.cling.android.AndroidUpnpServiceImpl;
import org.fourthline.cling.binding.LocalServiceBindingException;
import org.fourthline.cling.binding.annotations.AnnotationLocalServiceBinder;
import org.fourthline.cling.model.DefaultServiceManager;
import org.fourthline.cling.model.ValidationException;
import org.fourthline.cling.model.meta.DeviceDetails;
import org.fourthline.cling.model.meta.DeviceIdentity;
import org.fourthline.cling.model.meta.Icon;
import org.fourthline.cling.model.meta.LocalDevice;
import org.fourthline.cling.model.meta.LocalService;
import org.fourthline.cling.model.meta.ManufacturerDetails;
import org.fourthline.cling.model.meta.ModelDetails;
import org.fourthline.cling.model.types.UDADeviceType;
import org.fourthline.cling.model.types.UDN;
import org.fourthline.cling.support.avtransport.lastchange.AVTransportLastChangeParser;
import org.fourthline.cling.support.lastchange.LastChange;
import org.fourthline.cling.support.lastchange.LastChangeAwareServiceManager;
import org.fourthline.cling.support.renderingcontrol.lastchange.RenderingControlLastChangeParser;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceManager;
import android.util.Log;

import com.abk.privatedancer.prefs.PrivateDancerPreferenceActivity;
import com.abk.privatedancer.upnp.AndroidAVTransportService;
import com.abk.privatedancer.upnp.AndroidAudioRenderingControlService;
import com.abk.privatedancer.upnp.AndroidConnectionManagerService;

/**
 * Private Dancer UPNP service implementation for MediaRender.
 * 
 * @author kgilmer
 * 
 */
public class PrivateDancerUpnpServiceImpl extends AndroidUpnpServiceImpl implements OnSharedPreferenceChangeListener {

	private static final long LAST_CHANGE_FIRING_INTERVAL_MILLISECONDS = 500;

	private static final int NOTIFICATION_STARTED_ID = 1;

	/**
	 * Log tag
	 */
	public static final String TAG = PrivateDancerUpnpServiceImpl.class.getPackage().getName();
	
	private MediaPlayer mediaPlayer;
	private AudioManager audioManager;
	private UpnpService upnpService;
	private LocalDevice privateDancerDevice;
	private Context context;
	private SharedPreferences prefs;
	
	//Intermittantly cling throws errors saying this property is not set.
	static {
		 System.setProperty("org.xml.sax.driver","org.xmlpull.v1.sax2.Driver");
	}
	
	final LastChange avTransportLastChange = new LastChange(new AVTransportLastChangeParser());
	final LastChange renderingControlLastChange = new LastChange(new RenderingControlLastChangeParser());

	protected LastChangeAwareServiceManager<AndroidAVTransportService> avTransport;
	protected LastChangeAwareServiceManager<AndroidAudioRenderingControlService> renderingControl;

	private Thread lastChangePushThread;

	private WakeLock wakeLock;

	@Override
	public void onCreate() {	
		super.onCreate();
	}
	
	/* (non-Javadoc)
	 * @see android.app.Service#onStart(android.content.Intent, int)
	 */
	@Override
	public void onStart(Intent intent, int startId) {
		context = PrivateDancerUpnpServiceImpl.this.getApplicationContext();

		prefs = PreferenceManager.getDefaultSharedPreferences(this); 
		mediaPlayer = new MediaPlayer();
		audioManager = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);

		AnnotationLocalServiceBinder binder = new AnnotationLocalServiceBinder();
		
		//Control debug log messages
		final boolean debugLogging = false;
		
		 // The AVTransport just passes the calls on to the backend players				
        LocalService<AndroidAVTransportService> avTransportService = binder.read(AndroidAVTransportService.class);
        avTransport =
                new LastChangeAwareServiceManager<AndroidAVTransportService>(
                        avTransportService,
                        new AVTransportLastChangeParser()
                ) {
                    @Override
                    protected AndroidAVTransportService createServiceInstance() throws Exception {
                        return new AndroidAVTransportService(mediaPlayer, avTransportLastChange, debugLogging);
                    }
                };
        avTransportService.setManager(avTransport);

        // The Rendering Control just passes the calls on to the backend players        
        String setVolumeEnabledKey = getText(R.string.clients_set_volume_key).toString();
		final boolean setVolumeEnabled = prefs.getBoolean(setVolumeEnabledKey, true);
        LocalService<AndroidAudioRenderingControlService> renderingControlService = binder.read(AndroidAudioRenderingControlService.class);
        renderingControl =
                new LastChangeAwareServiceManager<AndroidAudioRenderingControlService>(
                        renderingControlService,
                        new RenderingControlLastChangeParser()
                ) {
                    @Override
                    protected AndroidAudioRenderingControlService createServiceInstance() throws Exception {
                        return new AndroidAudioRenderingControlService(renderingControlLastChange, audioManager, setVolumeEnabled, debugLogging);
                    }
                };
        renderingControlService.setManager(renderingControl);		

		// Connection Manager Service
		LocalService connectionManagerService = binder.read(AndroidConnectionManagerService.class);
		DefaultServiceManager connectionManager = new DefaultServiceManager(connectionManagerService) {
			@Override
			protected Object createServiceInstance() throws Exception {
				return new AndroidConnectionManagerService();
			}
		};
		connectionManagerService.setManager(connectionManager);

		upnpService = super.binder.get();

		try {
			privateDancerDevice = createDevice(avTransportService, renderingControlService, connectionManagerService);

			upnpService.getRegistry().addDevice(privateDancerDevice);
			
			// Listen for preference changes that might affect the service.			
			prefs.registerOnSharedPreferenceChangeListener(this);
			
			// Generate notification to let user know service is running.
			NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			int iconId = R.drawable.ic_stat;
			CharSequence notificationText = getText(R.string.notification_started_message);
			long when = System.currentTimeMillis();
			
			Notification notification = new Notification(iconId, notificationText, when);
			notification.flags |= Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR;
			
			Context context = getApplicationContext();
			CharSequence contentTitle = getText(R.string.notification_started_title);
			CharSequence contentText = getText(R.string.notification_started_action_text);
			Intent notificationIntent = new Intent(this, PrivateDancerPreferenceActivity.class);
			PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

			notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);			
			//notificationManager.notify(NOTIFICATION_STARTED_ID, notification);
			
			startForeground(NOTIFICATION_STARTED_ID, notification);
			
			String wakeLockKey = getText(R.string.prevent_sleep_key).toString();
			//By default, set wake lock if it has not been specifically set in preferences.
			if (prefs.getBoolean(wakeLockKey, true)) {
				Log.i(PrivateDancerPreferenceActivity.TAG, "Setting partial wake lock.");
				PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
				wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getText(R.string.app_name).toString());
				
				if (wakeLock != null)
					wakeLock.acquire();
				else 
					Log.e(PrivateDancerPreferenceActivity.TAG, "Failed to add wake lock.");
			}		
		} catch (Exception e) {
			Log.e(PrivateDancerPreferenceActivity.TAG, "Failed to add device to registry.", e);
		}	
		
		startLastChangePushThread();
	}
	
	 // The backend player instances will fill the LastChange whenever something happens with
    // whatever event messages are appropriate. This loop will periodically flush these changes
    // to subscribers of the LastChange state variable of each service.
    protected void startLastChangePushThread() {
        // TODO: We should only run this if we actually have event subscribers
    	lastChangePushThread = new Thread() {
            @Override
            public void run() {
                try {
                    while (!Thread.interrupted()) {
                    	try {
	                        // These operations will NOT block and wait for network responses
                    		Thread.sleep(LAST_CHANGE_FIRING_INTERVAL_MILLISECONDS);
	                        avTransport.fireLastChange();
	                        renderingControl.fireLastChange();
	                        
                    	} catch (RuntimeException e) {
                    		Log.w(PrivateDancerPreferenceActivity.TAG, "Warning runtime exception thrown in change push: " + e.getMessage());
                    	}
                    }
                } catch (InterruptedException ex) {
                	return;
                }
            }
        };
        
        lastChangePushThread.start();
    }

	@Override
	public void onDestroy() {	
		try {		
			if (lastChangePushThread != null)
				lastChangePushThread.interrupt();
	
			if (prefs != null)
				prefs.unregisterOnSharedPreferenceChangeListener(this);
	
			if (upnpService != null && privateDancerDevice != null)
				upnpService.getRegistry().removeDevice(privateDancerDevice);
	
			// TODO: validate everything is being cleaned up.
			mediaPlayer.release();
			
			//NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			//notificationManager.cancel(NOTIFICATION_STARTED_ID);
			stopForeground(true);
		} finally {
			if (wakeLock != null)
				wakeLock.release();
			super.onDestroy();
		}				
	}

	/**
	 * Create PD device based on set of UPNP services. Populates UPNP device
	 * metadata.
	 * 
	 * @param services
	 * @return
	 * @throws ValidationException
	 * @throws LocalServiceBindingException
	 * @throws IOException
	 */
	LocalDevice createDevice(LocalService<?>... services) throws ValidationException, LocalServiceBindingException, IOException {
		LocalDevice device = new LocalDevice(
				new DeviceIdentity(
						UDN.uniqueSystemIdentifier(context.getString(R.string.upnp_system_identifier))), 
						new UDADeviceType("MediaRenderer", 1), 
						new DeviceDetails(getUPnPServiceName(), 
								new ManufacturerDetails(
										getText(R.string.uda_manufacturer_name).toString(), 
										getText(R.string.uda_manufacturer_url).toString()), 
								new ModelDetails(
										getText(R.string.uda_model_name).toString(), 
										getText(R.string.uda_model_description).toString(), 
										"1", 
										getText(R.string.uda_model_url).toString())), 
						new Icon[] {createDefaultDeviceIcon()}, services);

		return device;
	}
	
	/**
	 * Service name may be defined by preference setting.  If not use app default.
	 * @return
	 */
	private String getUPnPServiceName() {
		if (prefs == null)		
			return getText(R.string.uda_device_friendlyname).toString();
		
		String key = getText(R.string.player_name_key).toString();
		return prefs.getString(key, getText(R.string.uda_device_friendlyname).toString());		
	}

	/**
	 * @return Icon of UPnP service
	 */
	protected Icon createDefaultDeviceIcon() {
        try {
            return new Icon(
                    "image/png",
                    48, 48, 8,
                    URI.create("icon.png"),
                    getResources().openRawResource(R.drawable.upnp_icon)                    
            );
        } catch (IOException ex) {
        	Log.e(PrivateDancerPreferenceActivity.TAG, "Could not load UPNP service icon.", ex);
        }
        return null;
    }

	@Override
	public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {	
		if (key.equals(context.getString(R.string.enable_service_key)) && !prefs.getBoolean(key, true)) {
			// Service should be stopped. Assuming service is started since we
			// are receiving the notification.
			stopSelf();
		}
	}

}
