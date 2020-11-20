---
layout: post
title: Private Dancer
version: 1.1
category: basic
appname: com.abk.privatedancer
summary: A UPnP/DLNA Media Renderer for Android
icon: /images/com.abk.privatedancer/ic_market_512.png
imageurl: /images/com.abk.privatedancer/screenshot_prefs.png
imageurl2: /images/com.abk.privatedancer/screenshot_notification.png
imageurl3: /images/com.abk.privatedancer/screenshot_allshare.png
imageurl4: /images/com.abk.privatedancer/screenshot_wallpaper.png
--- 

It is designed to be used on a device attached to speakers and power.  Unlike most [UPnP](http://www.upnp.org/) Android applications, Private Dancer is designed for always-on (headless) use.  It contains no flashy UI and its only purpose is to provide a rock-solid, dependable wireless audio service.

## What is a UPnP/DLNA Media Renderer?
The name is awkward but Private Dancer essentially provides wireless speakers for your network.  It relies on the UPnP* protocol to communicate with other devices on the network.  Many devices support this protocol and there are many Android apps available that can be used to send audio to Private Dancer.

## What is meant by *Headless*?
While Private Dancer can be used on any Android device, it sports features that make it ideal for a tablet or unused Android phone that can be connected to speakers and power.  The service can run on boot and aims to be always available, and generally once setup does not require direct user interaction.

## Operation
Install the application and run it. Select startup options and enable the service.  Once enabled, a notification appears and the UPnP/DLNA service is available on the network.  Connect to the service via a supported UPnP client, such as [Samsung's AllShare](http://www.samsung.com/global/allshare/pcsw/) media application to play music from your phone or other UPnP device.
When the control point application allows for selection of 'player' or 'renderer', select Private Dancer.  From there, the audio should begin playing from the device that Private Dancer is running on. 

## A Note on Compatibility
Since UPnP relies on networking features that may not be available on all home networks, it's best to test the application once purchased.  If there is a problem using Private Dancer in a particular environment, it is easy initially to cancel the purchase.

## Features

- UPnP/DLNA Media Renderer
- Configurable to start automatically on boot (survives loss of power)
- Configurable to prevent device from entering sleep mode (always on)
- Configurable to allow or prevent clients from changing the volume
- Configurable the name of the service

## Tested Clients

- 2Player v1.1.07 (Android)
- BubbleUPnP 1.3.1.2 (Android)
- UPnPlay 0.0.62 (Android)
- Samsung AllShare 2.6.114 (Android)

## Recent Changes (Version 1.1)

- Fixed compatibility issues with 2Player 
- General enhancements to reliability
- More robust error handling and recovery
- Removed debug logging messages

Private Dancer uses the excellent [Cling library](http://teleal.org/projects/cling/) for UPnP protocol support.  Cling is LGPL licensed, and more information is available at http://teleal.org/projects/cling.
