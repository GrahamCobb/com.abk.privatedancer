/*
 * Copyright (C) 2011 4th Line GmbH, Switzerland
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 2 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.abk.privatedancer.upnp;

import org.fourthline.cling.support.connectionmanager.ConnectionManagerService;
import org.fourthline.cling.support.model.ProtocolInfo;
import org.seamless.util.MimeType;

import android.util.Log;

import com.abk.privatedancer.PrivateDancerUpnpServiceImpl;

/**
 * @author Christian Bauer
 */
public class AndroidConnectionManagerService extends ConnectionManagerService {
	//TODO: define this based on runtime android version.
	private static final String [] ANDROID_SUPPORTED_AUDIO_MIMETYPES = {
			"audio/mpeg",
			"audio/mp4",
			"audio/3gpp",
			"audio/midi",
			"audio/vorbis",
			"audio/x-wav",
			"audio/vnd.wave",
			"audio/ogg"};
		
		 /**
		 * 
		 */
	public AndroidConnectionManagerService() {
		      
			 for (String mimeType : ANDROID_SUPPORTED_AUDIO_MIMETYPES) {
		            try {
		                MimeType mt = MimeType.valueOf(mimeType);
		                sinkProtocolInfo.add(new ProtocolInfo(mt));
		            } catch (IllegalArgumentException ex) {
		                Log.w(PrivateDancerUpnpServiceImpl.TAG, "Ignoring invalid MimeType: " + mimeType);
		            }
		        }		      
		    }

}
