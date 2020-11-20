package com.abk.privatedancer.upnp;

import java.net.URI;

import org.fourthline.cling.model.ModelUtil;
import org.fourthline.cling.model.types.UnsignedIntegerFourBytes;
import org.fourthline.cling.support.avtransport.AVTransportErrorCode;
import org.fourthline.cling.support.avtransport.AVTransportException;
import org.fourthline.cling.support.avtransport.AbstractAVTransportService;
import org.fourthline.cling.support.avtransport.lastchange.AVTransportVariable;
import org.fourthline.cling.support.lastchange.LastChange;
import org.fourthline.cling.support.model.DeviceCapabilities;
import org.fourthline.cling.support.model.MediaInfo;
import org.fourthline.cling.support.model.PlayMode;
import org.fourthline.cling.support.model.PositionInfo;
import org.fourthline.cling.support.model.StorageMedium;
import org.fourthline.cling.support.model.TransportAction;
import org.fourthline.cling.support.model.TransportInfo;
import org.fourthline.cling.support.model.TransportSettings;
import org.fourthline.cling.support.model.TransportState;
import org.fourthline.cling.support.model.TransportStatus;

import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.util.Log;

import com.abk.privatedancer.PrivateDancerUpnpServiceImpl;
import com.abk.privatedancer.prefs.PrivateDancerPreferenceActivity;

/**
 * The bridge between UPNP and the Android media player.
 * 
 * @author kgilmer
 *
 */
public class AndroidAVTransportService extends AbstractAVTransportService implements OnErrorListener, OnCompletionListener, OnPreparedListener {	
	/**
	 * Does not support storage mediums.
	 */
	private static final StorageMedium [] SUPPORTED_STORAGE_MEDIUMS = {
		StorageMedium.NETWORK
	};
	
	private static final TransportAction [] PLAY_STATE_TRANSPORT_ACTIONS = {
		TransportAction.Pause,
		TransportAction.Seek,
		TransportAction.Stop
	};
	
	private static final TransportAction [] PAUSE_STATE_TRANSPORT_ACTIONS = {
		TransportAction.Play,
		TransportAction.Seek,
		TransportAction.Stop
	};
	
	private static final TransportAction [] STOP_STATE_TRANSPORT_ACTIONS = {
		TransportAction.Play
	};

	private static final TransportInfo TRANSPORT_INFO_STOPPED_ERROR = new TransportInfo(TransportState.STOPPED, TransportStatus.ERROR_OCCURED);

	private static final TransportInfo TRANSPORT_INFO_PAUSED = new TransportInfo(TransportState.PAUSED_PLAYBACK, TransportStatus.OK);

	private static final TransportInfo TRANSPORT_INFO_PLAYING = new TransportInfo(TransportState.PLAYING, TransportStatus.OK);

	private static final TransportInfo TRANSPORT_INFO_STOPPED = new TransportInfo(TransportState.NO_MEDIA_PRESENT, TransportStatus.OK);

	private static final String PLAY_MODE_NORMAL = "NORMAL";

	private static final long STREAM_IO_WAIT_MILLIS = 500;
	private static final long STREAM_IO_MAX_RETRIES = 60;
	
	private final MediaPlayer mediaPlayer;
	private final UnsignedIntegerFourBytes instanceId;
	private boolean playerErrorOccurred = false;
	private PositionInfo currentTrackPositionInfo;
	private String currentURI;
	private long startTime;
	private boolean playerPaused = false;
	private String currentMetadata;
	private final LastChange lastChange;
	private volatile boolean prepared = false;
	private volatile TransportInfo currentTransportInfo = new TransportInfo();

	private final boolean DEBUG_LOG;

	public AndroidAVTransportService(MediaPlayer mediaPlayer, LastChange lastChange, boolean debugLog) {
		super(lastChange);
		this.mediaPlayer = mediaPlayer;
		this.lastChange = lastChange;
		this.DEBUG_LOG = debugLog;
		mediaPlayer.setOnErrorListener(this);
		mediaPlayer.setOnCompletionListener(this);
		mediaPlayer.setOnPreparedListener(this);
		mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
		instanceId = getDefaultInstanceID();
	}
	
	@Override
	public UnsignedIntegerFourBytes[] getCurrentInstanceIds() {
		if (DEBUG_LOG)
			Log.d(PrivateDancerPreferenceActivity.TAG, "AndroidAVTransportService.getCurrentInstanceIds");
		
		return (new UnsignedIntegerFourBytes [] {instanceId});
	}

	@Override
	protected TransportAction[] getCurrentTransportActions(UnsignedIntegerFourBytes instance) throws Exception {
		if (DEBUG_LOG)
			Log.d(PrivateDancerPreferenceActivity.TAG, "AndroidAVTransportService.getCurrentTransportActions");
		
		if (!instance.equals(instanceId))
			throw new AVTransportException(AVTransportErrorCode.INVALID_INSTANCE_ID);
		
		/*
		 * TODO: Not fully sure on the correct mapping of the Android media
		 * player state machine to the UPNP equivalent.
		 */
		
		if (mediaPlayer.isPlaying() && playerPaused) {
			if (DEBUG_LOG)
				Log.d(PrivateDancerPreferenceActivity.TAG, "AndroidAVTransportService.getCurrentTransportActions: PAUSE_STATE_TRANSPORT_ACTIONS");
			return PAUSE_STATE_TRANSPORT_ACTIONS;
		} else if (mediaPlayer.isPlaying() && !playerPaused) {
			if (DEBUG_LOG)
				Log.d(PrivateDancerPreferenceActivity.TAG, "AndroidAVTransportService.getCurrentTransportActions: PLAY_STATE_TRANSPORT_ACTIONS");
			return PLAY_STATE_TRANSPORT_ACTIONS;
		} else {
			if (DEBUG_LOG)
				Log.d(PrivateDancerPreferenceActivity.TAG, "AndroidAVTransportService.getCurrentTransportActions: STOP_STATE_TRANSPORT_ACTIONS");
			return STOP_STATE_TRANSPORT_ACTIONS;
		}		
	}

	@Override
	public DeviceCapabilities getDeviceCapabilities(UnsignedIntegerFourBytes instance) throws AVTransportException {
		if (DEBUG_LOG)
			Log.d(PrivateDancerPreferenceActivity.TAG, "AndroidAVTransportService.getDeviceCapabilities");
		
		if (!instance.equals(instanceId))
			throw new AVTransportException(AVTransportErrorCode.INVALID_INSTANCE_ID);
		
		return new DeviceCapabilities(SUPPORTED_STORAGE_MEDIUMS);		
	}

	@Override
	public MediaInfo getMediaInfo(UnsignedIntegerFourBytes instance) throws AVTransportException {
		if (DEBUG_LOG)
			Log.d(PrivateDancerPreferenceActivity.TAG, "AndroidAVTransportService.getMediaInfo");
		
		if (!instance.equals(instanceId))
			throw new AVTransportException(AVTransportErrorCode.INVALID_INSTANCE_ID);
		
		if (currentURI == null || currentMetadata == null)
			return new MediaInfo();
		
		return new MediaInfo(currentURI, currentMetadata);
	}

	@Override
	public PositionInfo getPositionInfo(UnsignedIntegerFourBytes instance) throws AVTransportException {
		if (DEBUG_LOG)
			Log.d(PrivateDancerPreferenceActivity.TAG, "AndroidAVTransportService.getPositionInfo()");
		
		if (!instance.equals(instanceId))
			throw new AVTransportException(AVTransportErrorCode.INVALID_INSTANCE_ID);
		
		if (!mediaPlayer.isPlaying() && !playerPaused) {
			return new PositionInfo();
		}
		
		if (currentTrackPositionInfo == null) {
			currentTrackPositionInfo = new PositionInfo(
					1,
					ModelUtil.toTimeString(mediaPlayer.getDuration() / 1000),
					currentURI,
					ModelUtil.toTimeString((System.currentTimeMillis() - startTime) / 1000),
					ModelUtil.toTimeString(System.currentTimeMillis() / 1000));
		} else { 
			currentTrackPositionInfo = new PositionInfo(
					currentTrackPositionInfo, 
					ModelUtil.toTimeString(mediaPlayer.getCurrentPosition() / 1000),
					ModelUtil.toTimeString(mediaPlayer.getCurrentPosition() / 1000));
		}
		
		return currentTrackPositionInfo;
	}
	
	 /**
     * @return
     */
    synchronized protected TransportAction[] getCurrentTransportActions() {
        TransportState state = currentTransportInfo.getCurrentTransportState();
        TransportAction[] actions;

        switch (state) {
            case STOPPED:
                actions = new TransportAction[]{
                        TransportAction.Play
                };
                break;
            case PLAYING:
                actions = new TransportAction[]{
                        TransportAction.Stop,
                        TransportAction.Pause,
                        TransportAction.Seek
                };
                break;
            case PAUSED_PLAYBACK:
                actions = new TransportAction[]{
                        TransportAction.Stop,
                        TransportAction.Pause,
                        TransportAction.Seek,
                        TransportAction.Play
                };
                break;
            default:
                actions = null;
        }
        return actions;
    }

	@Override
	public TransportInfo getTransportInfo(UnsignedIntegerFourBytes instance) throws AVTransportException {
		if (!instance.equals(instanceId))
			throw new AVTransportException(AVTransportErrorCode.INVALID_INSTANCE_ID);
		
		return currentTransportInfo;	
	}

	@Override
	public TransportSettings getTransportSettings(UnsignedIntegerFourBytes instance) throws AVTransportException {
		if (DEBUG_LOG)
			Log.d(PrivateDancerPreferenceActivity.TAG, "AndroidAVTransportService.getTransportSettings");
		
		if (!instance.equals(instanceId))
			throw new AVTransportException(AVTransportErrorCode.INVALID_INSTANCE_ID);
		
		return new TransportSettings(PlayMode.NORMAL);
	}

	@Override
	public void next(UnsignedIntegerFourBytes instance) throws AVTransportException {
		if (DEBUG_LOG)
			Log.d(PrivateDancerPreferenceActivity.TAG, "AndroidAVTransportService.next");
		
		if (!instance.equals(instanceId))
			throw new AVTransportException(AVTransportErrorCode.INVALID_INSTANCE_ID);
		
		Log.e(PrivateDancerPreferenceActivity.TAG, "Unimplemented control: next");		
	}

	@Override
	public void pause(UnsignedIntegerFourBytes instance) throws AVTransportException {
		if (DEBUG_LOG)
			Log.d(PrivateDancerPreferenceActivity.TAG, "AndroidAVTransportService.pause");
		
		if (!instance.equals(instanceId))
			throw new AVTransportException(AVTransportErrorCode.INVALID_INSTANCE_ID);
		
		if (playerPaused) {
			Log.w(PrivateDancerPreferenceActivity.TAG, "AndroidAVTransportService.pause: ignoring pause command, already paused.");
			return;
		}
		
		mediaPlayer.pause();
		transportStateChanged(TransportState.PAUSED_PLAYBACK);
		playerPaused = true;
	}

	@Override
	synchronized public void play(UnsignedIntegerFourBytes instance, String arg1) throws AVTransportException {
		if (!instance.equals(instanceId))
			throw new AVTransportException(AVTransportErrorCode.INVALID_INSTANCE_ID);
		
		int i = 0;
		
		while (i < STREAM_IO_MAX_RETRIES) {
			i++;
			try {
				if (prepared || playerPaused) {
					mediaPlayer.start();
					if (!playerPaused)
						startTime = System.currentTimeMillis();
					
					prepared = false;
					playerPaused = false;
					transportStateChanged(TransportState.PLAYING);
					return;
				} 
				
				if (DEBUG_LOG)
					Log.i(PrivateDancerUpnpServiceImpl.TAG, "Waiting for player to load stream from network... " + i);
				Thread.sleep(STREAM_IO_WAIT_MILLIS);
			} catch (InterruptedException e) {
				Log.i(PrivateDancerUpnpServiceImpl.TAG, "Interrupted");
				return;
			}			
		}
		
		Log.e(PrivateDancerUpnpServiceImpl.TAG, "Failed to play media.");								
	}
	
	synchronized protected void transportStateChanged(TransportState newState) {
        TransportState currentTransportState = currentTransportInfo.getCurrentTransportState();
        if (DEBUG_LOG)
        	Log.d(PrivateDancerUpnpServiceImpl.TAG, "Current state is: " + currentTransportState + ", changing to new state: " + newState);
        
        currentTransportInfo = new TransportInfo(newState);

        getAvTransportLastChange().setEventedValue(
                instanceId,
                new AVTransportVariable.TransportState(newState),
                new AVTransportVariable.CurrentTransportActions(getCurrentTransportActions())
        );
    }

	@Override
	public void previous(UnsignedIntegerFourBytes instance) throws AVTransportException {
		if (DEBUG_LOG)
			Log.d(PrivateDancerPreferenceActivity.TAG, "AndroidAVTransportService.previous");
		
		if (!instance.equals(instanceId))
			throw new AVTransportException(AVTransportErrorCode.INVALID_INSTANCE_ID);
		
		Log.e(PrivateDancerPreferenceActivity.TAG, "Unimplemented control: previous");
	}

	@Override
	public void record(UnsignedIntegerFourBytes instance) throws AVTransportException {
		if (DEBUG_LOG)
			Log.d(PrivateDancerPreferenceActivity.TAG, "AndroidAVTransportService.record");
		
		if (!instance.equals(instanceId))
			throw new AVTransportException(AVTransportErrorCode.INVALID_INSTANCE_ID);
		
		Log.e(PrivateDancerPreferenceActivity.TAG, "Unimplemented control: setNextAVTransportURI");
	}
	
	 public LastChange getAvTransportLastChange() {
	        return lastChange;
	    }

	@Override
	public void seek(UnsignedIntegerFourBytes instance, String timeUnit, String timeValue) throws AVTransportException {
		if (DEBUG_LOG)
			Log.d(PrivateDancerPreferenceActivity.TAG, "AndroidAVTransportService.seek( " + timeUnit + ", " + timeValue + ")");
		
		if (!instance.equals(instanceId))
			throw new AVTransportException(AVTransportErrorCode.INVALID_INSTANCE_ID);
		
		if (!mediaPlayer.isPlaying() && !playerPaused) {
			Log.w(PrivateDancerPreferenceActivity.TAG, "AndroidAVTransportService.seek: Ignoring seek command, not playing.");
			return;
		}

		if (timeUnit.equals("REL_TIME")) {
			long millis = ModelUtil.fromTimeString(timeValue) * 1000;
			mediaPlayer.seekTo((int) millis);
			return;
		}
		
		Log.e(PrivateDancerPreferenceActivity.TAG, "AndroidAVTransportService.seek: Unsupported time unit.");
	}

	@Override
	public void setAVTransportURI(UnsignedIntegerFourBytes instance, String mediaURI, String metadata) throws AVTransportException {
		if (DEBUG_LOG)
			Log.d(PrivateDancerPreferenceActivity.TAG, "AndroidAVTransportService.setAVTransportURI(" + mediaURI + ", " + metadata + " )");
		
		if (!instance.equals(instanceId))
			throw new AVTransportException(AVTransportErrorCode.INVALID_INSTANCE_ID);
		
		try {
			if (playerErrorOccurred) {
				resetState();
				playerErrorOccurred = false;
			} else if (mediaPlayer.isPlaying()) {
				stop(instance);
			} else {
				mediaPlayer.reset();
			}			
			Log.d(PrivateDancerPreferenceActivity.TAG, "Setting new media source: " + mediaURI);
			
			prepared = false;
			mediaPlayer.setDataSource(mediaURI);
			mediaPlayer.prepareAsync();
			currentURI = mediaURI;
			currentMetadata = metadata;
			
			URI uri = new URI(mediaURI);
			getAvTransportLastChange().setEventedValue(
			    instanceId,
			    new AVTransportVariable.AVTransportURI(uri),
			    new AVTransportVariable.CurrentTrackURI(uri));
			
			transportStateChanged(TransportState.STOPPED);
			 
		} catch (Exception e) {
			playerErrorOccurred = true;
			Log.e(PrivateDancerPreferenceActivity.TAG, "Error occurred while setting transport.", e);
			throw new AVTransportException(1, e.getMessage());
		} 
	}

	@Override
	public void setNextAVTransportURI(UnsignedIntegerFourBytes instance, String arg1, String arg2) throws AVTransportException {
		if (DEBUG_LOG)
			Log.d(PrivateDancerPreferenceActivity.TAG, "AndroidAVTransportService.setNextAVTransportURI");
		
		if (!instance.equals(instanceId))
			throw new AVTransportException(AVTransportErrorCode.INVALID_INSTANCE_ID);
		
		Log.e(PrivateDancerPreferenceActivity.TAG, "Unimplemented control: setNextAVTransportURI");
		//throw new AVTransportException(3, );
	}

	@Override
	public void setPlayMode(UnsignedIntegerFourBytes instance, String arg1) throws AVTransportException {
		if (DEBUG_LOG)
			Log.d(PrivateDancerPreferenceActivity.TAG, "AndroidAVTransportService.setPlayMode");
		
		if (!instance.equals(instanceId))
			throw new AVTransportException(AVTransportErrorCode.INVALID_INSTANCE_ID);
		
		if (arg1.equals(PLAY_MODE_NORMAL))
			return;
		
		Log.e(PrivateDancerPreferenceActivity.TAG, "Unimplemented control: setPlayMode " + arg1);		
	}

	@Override
	public void setRecordQualityMode(UnsignedIntegerFourBytes instance, String arg1) throws AVTransportException {
		Log.d(PrivateDancerPreferenceActivity.TAG, "AndroidAVTransportService.setRecordQualityMode");
		if (!instance.equals(instanceId))
			throw new AVTransportException(AVTransportErrorCode.INVALID_INSTANCE_ID);
		
		Log.e(PrivateDancerPreferenceActivity.TAG, "Unimplemented control: setRecordQualityMode");
	}

	@Override
	public void stop(UnsignedIntegerFourBytes instance) throws AVTransportException {
		if (DEBUG_LOG)
			Log.d(PrivateDancerPreferenceActivity.TAG, "AndroidAVTransportService.stop");
		
		if (!instance.equals(instanceId))
			throw new AVTransportException(AVTransportErrorCode.INVALID_INSTANCE_ID);
		
		if (mediaPlayer.isPlaying()) {
			mediaPlayer.stop();
			transportStateChanged(TransportState.STOPPED);
			resetState();
		} else {
			resetState();
			Log.d(PrivateDancerPreferenceActivity.TAG, "Ignoring stop from client, currently not in play state.");	
		}
	}

	@Override
	public boolean onError(MediaPlayer arg0, int arg1, int arg2) {
		playerErrorOccurred = true;
		Log.e(PrivateDancerPreferenceActivity.TAG, "Error event triggered from MediaPlayer: " + arg1 + ", " + arg2);

		resetState();
		return true;
	}
	
	private void resetState() {
		if (DEBUG_LOG)
			Log.d(PrivateDancerPreferenceActivity.TAG, "Resetting player state.");
		currentURI = null;
		currentMetadata = null;
		currentTrackPositionInfo = null;
		mediaPlayer.reset();
		prepared = false;
		startTime = 0;
		playerPaused = false;
	}	

	@Override
	public void onCompletion(MediaPlayer arg0) {
		if (DEBUG_LOG)
			Log.d(PrivateDancerPreferenceActivity.TAG, "Recieved completion event.");
		
		transportStateChanged(TransportState.STOPPED);
		resetState();		
	}

	@Override
	public void onPrepared(MediaPlayer arg0) {
		if (DEBUG_LOG)
			Log.d(PrivateDancerPreferenceActivity.TAG, "Recieved prepared event.");
		
		prepared = true;
	}
}
