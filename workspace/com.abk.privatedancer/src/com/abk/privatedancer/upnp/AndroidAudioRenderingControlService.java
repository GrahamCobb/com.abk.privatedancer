package com.abk.privatedancer.upnp;

import org.fourthline.cling.model.types.UnsignedIntegerFourBytes;
import org.fourthline.cling.model.types.UnsignedIntegerTwoBytes;
import org.fourthline.cling.support.lastchange.LastChange;
import org.fourthline.cling.support.model.Channel;
import org.fourthline.cling.support.renderingcontrol.AbstractAudioRenderingControl;
import org.fourthline.cling.support.renderingcontrol.RenderingControlException;

import android.media.AudioManager;
import android.util.Log;

import com.abk.privatedancer.prefs.PrivateDancerPreferenceActivity;

/**
 * Rendering Control Service for Android
 * @author kgilmer
 *
 */
public class AndroidAudioRenderingControlService extends AbstractAudioRenderingControl {
	private final AudioManager audioManager;
	private final UnsignedIntegerFourBytes instanceId;
	private boolean playerMute;
	private final int maxVolume;
	private final boolean setVolumeEnabled;
	private final boolean debugLog;

	public AndroidAudioRenderingControlService(LastChange renderingControlLastChange, AudioManager audioManager, boolean setVolumeEnabled, boolean debugLog) {
		super(renderingControlLastChange);
		this.audioManager = audioManager;
		this.debugLog = debugLog;
		this.instanceId = super.getDefaultInstanceID();
		this.playerMute = false;
		this.maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
		this.setVolumeEnabled = setVolumeEnabled;
	}

	@Override
	public UnsignedIntegerFourBytes[] getCurrentInstanceIds() {
		if (debugLog)
			Log.d(PrivateDancerPreferenceActivity.TAG, "AndroidAudioRenderingControlService.getCurrentInstanceIds");
		
		return new UnsignedIntegerFourBytes[] { instanceId };
	}

	@Override
	public boolean getMute(UnsignedIntegerFourBytes instanceId, String channelName) throws RenderingControlException {
		if (debugLog)
			Log.d(PrivateDancerPreferenceActivity.TAG, "AndroidAudioRenderingControlService.getMute: " + channelName);
		return playerMute;
	}

	@Override
	public void setMute(UnsignedIntegerFourBytes instanceId, String channelName, boolean desiredMute) throws RenderingControlException {
		if (debugLog)
			Log.d(PrivateDancerPreferenceActivity.TAG, "AndroidAudioRenderingControlService.setMute " + channelName);

		playerMute = true;
		audioManager.setStreamMute(AudioManager.STREAM_MUSIC, true);
	}

	@Override
	public UnsignedIntegerTwoBytes getVolume(UnsignedIntegerFourBytes instanceId, String channelName) throws RenderingControlException {
		if (debugLog)
			Log.d(PrivateDancerPreferenceActivity.TAG, "AndroidAudioRenderingControlService.getVolume " + channelName);

		long adjustedVol = toNormalizedVolume(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC), maxVolume);
		
		if (debugLog)
			Log.d(PrivateDancerPreferenceActivity.TAG, "AndroidAudioRenderingControlService.getVolume: " + adjustedVol + ", max: " + maxVolume);
		
		return new UnsignedIntegerTwoBytes(adjustedVol);
	}

	@Override
	public void setVolume(UnsignedIntegerFourBytes instanceId, String channelName, UnsignedIntegerTwoBytes desiredVolume) throws RenderingControlException {
		if (debugLog)
			Log.d(PrivateDancerPreferenceActivity.TAG, "AndroidAudioRenderingControlService.setVolume " + channelName);
		
		if (setVolumeEnabled == false)
			return;

		long targetVolume = desiredVolume.getValue().intValue();
		long currentVolume = toNormalizedVolume(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC), maxVolume);
		
		int direction = AudioManager.ADJUST_SAME;
		if (targetVolume > currentVolume)
			direction = AudioManager.ADJUST_RAISE;
		else if (targetVolume < currentVolume)
			direction = AudioManager.ADJUST_LOWER;
		
		int maxAdjustments = 100;

		while (direction != AudioManager.ADJUST_SAME && maxAdjustments > 0) {
			audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0);
			maxAdjustments--;
			
			currentVolume = toNormalizedVolume(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC), maxVolume);
			
			if (direction == AudioManager.ADJUST_LOWER && currentVolume <= targetVolume)
				break;
			else if (direction == AudioManager.ADJUST_RAISE && currentVolume >= targetVolume)
				break;
			
			if (debugLog) {
				Log.d(PrivateDancerPreferenceActivity.TAG, "current: " + currentVolume + "  desired: " + desiredVolume);								
				Log.d(PrivateDancerPreferenceActivity.TAG, "AndroidAudioRenderingControlService.setVolume: " + direction);
			}
		};		
	}

	@Override
	protected Channel[] getCurrentChannels() {
		if (debugLog)
			Log.d(PrivateDancerPreferenceActivity.TAG, "AndroidAudioRenderingControlService.getCurrentChannels");
		return new Channel[] { Channel.Master };
	}

	/**
	 * @param rawVolume
	 * @return value of 0 - 100
	 */
	private static long toNormalizedVolume(int rawVolume, int maxVolume) {
		return Math.round((rawVolume / (double) maxVolume) * 100);
	}
	
	/**
	 * @param normalizedVolume
	 * @param maxVolume
	 * @return
	 */
	private static long toNativeVolume(long normalizedVolume, int maxVolume) {
		return Math.round((normalizedVolume / (double) 100) * maxVolume);
	}

}
