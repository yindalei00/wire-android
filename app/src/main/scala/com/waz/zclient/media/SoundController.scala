/**
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
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
package com.waz.zclient.media

import android.content.{Context, SharedPreferences}
import android.media.AudioManager
import android.net.Uri
import android.os.Vibrator
import android.text.TextUtils
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.{error, verbose}
import com.waz.media.manager.MediaManager
import com.waz.service.ZMessaging
import com.waz.utils.events.{EventContext, Signal}
import com.waz.zclient.controllers.userpreferences.UserPreferencesController
import com.waz.zclient.utils.ContextUtils._
import com.waz.zclient.utils.RingtoneUtils.{getUriForRawId, isDefaultValue}
import com.waz.zclient.{R, _}

//TODO Dean - would be nice to change these unit methods to listeners on signals from the classes that could trigger sounds.
//For that, however, we would need more signals in the app, and hence more scala classes...
class SoundController(implicit inj: Injector, cxt: Context) extends Injectable {

  private implicit val ev = EventContext.Implicits.global

  private val zms = inject[Signal[ZMessaging]]
  private val audioManager = Option(inject[AudioManager])
  private val vibrator = Option(inject[Vibrator])

  private val mediaManager = zms.map(_.mediamanager.mediaManager)

  private var _mediaManager = Option.empty[MediaManager]
  mediaManager(_mediaManager = _)

  setCustomSoundUrisFromPreferences(cxt.getSharedPreferences(UserPreferencesController.USER_PREFS_TAG, Context.MODE_PRIVATE))

  def setIncomingRingTonePlaying(play: Boolean) = {
    setMediaPlaying(R.raw.ringing_from_them, play)
    setVibrating(R.array.ringing_from_them, play, loop = true)
  }

  //no vibration needed here
  //TODO - there seems to be a race condition somewhere, where this method is called while isVideo is incorrect
  //This leads to the case where one of the media files starts playing, and we never receive the stop for it. Always ensuring
  //that both files stops is a fix for the symptom, but not the root cause - which could be affecting other things...
  def setOutgoingRingTonePlaying(play: Boolean, isVideo: Boolean = false) =
    if (play) {
      setMediaPlaying(if (isVideo) R.raw.ringing_from_me_video else R.raw.ringing_from_me, play = true)
    } else {
      setMediaPlaying(R.raw.ringing_from_me_video, play = false)
      setMediaPlaying(R.raw.ringing_from_me, play = false)
    }

  def playCallEstablishedSound() = {
    setMediaPlaying(R.raw.ready_to_talk)
    setVibrating(R.array.ready_to_talk)
  }

  def playCallEndedSound() = {
    setMediaPlaying(R.raw.talk_later)
    setVibrating(R.array.talk_later)
  }

  def playCallDroppedSound() = {
    setMediaPlaying(R.raw.call_drop)
    setVibrating(R.array.call_dropped)
  }

  def playAlert() = {
    setMediaPlaying(R.raw.alert)
    setVibrating(R.array.alert)
  }

  def shortVibrate() =
    setVibrating(R.array.alert)

  def playMessageIncomingSound(firstMessage: Boolean) = {
    setMediaPlaying(if (firstMessage) R.raw.first_message else R.raw.new_message)
    setVibrating(R.array.new_message)
  }

  def playPingFromThem() = {
    setMediaPlaying(R.raw.ping_from_them)
    setVibrating(R.array.ping_from_them)
  }

  //no vibration needed
  def playPingFromMe() =
    setMediaPlaying(R.raw.ping_from_me)

  def playCameraShutterSound() = {
    setMediaPlaying(R.raw.camera)
    setVibrating(R.array.camera)
  }

  /**
    * @param play For looping patterns, this parameter will tell to stop vibrating if they have previously been started
    */
  private def setVibrating(patternId: Int, play: Boolean = true, loop: Boolean = false): Unit = {
    (audioManager, vibrator) match {
      case (Some(am), Some(vib)) if play && am.getRingerMode != AudioManager.RINGER_MODE_SILENT && isVibrationEnabled =>
        vib.cancel() // cancel any current vibrations
        vib.vibrate(getIntArray(patternId).map(_.toLong), if (loop) 0 else -1)
      case (_, Some(vib)) => vib.cancel()
      case _ =>
    }
  }

  /**
    * @param play For media that play for a long time (or continuously??) this parameter will stop them
    */
  private def setMediaPlaying(resourceId: Int, play: Boolean = true) = _mediaManager.foreach { mm =>
    val resName = getResEntryName(resourceId)
    verbose(s"setMediaPlaying: $resName, play: $play")
    if (play) mm.playMedia(resName) else mm.stopMedia(resName)
  }

  /**
    * Takes a saved "URL" from the apps shared preferences, and uses that to set the different sounds in the app.
    * There are several "groups" of sounds, each with their own uri. There is then also a given "mainId" for each group,
    * which gets set first, and is then used to determine if the uri points to the "default" sound file.
    *
    * Then for the other ids related to that group, they are all set to either the default, or whatever new uri is specified
    */
  def setCustomSoundUrisFromPreferences(preferences: SharedPreferences) = {
    Seq(
      (R.string.pref_options_ringtones_ringtone_key, R.raw.ringing_from_them, Seq(R.raw.ringing_from_me, R.raw.ringing_from_me_video, R.raw.ringing_from_them_incall)),
      (R.string.pref_options_ringtones_text_key,     R.raw.ping_from_them,    Seq(R.raw.ping_from_me)),
      (R.string.pref_options_ringtones_ping_key,     R.raw.new_message,       Seq(R.raw.first_message, R.raw.new_message_gcm))
    ).map { case (uriPrefKey, mainId, otherIds) => (Option(preferences.getString(getString(uriPrefKey), null)), mainId, otherIds) }.foreach {
      case (Some(uri), mainId, otherIds) =>
        val isDefault = isDefaultValue(cxt, uri, R.raw.ringing_from_them)
        setCustomSoundUri(mainId, uri)
        otherIds.foreach(id => setCustomSoundUri(id, if (isDefault) getUriForRawId(cxt, id).toString else uri))
      case _ =>
    }
  }

  private def setCustomSoundUri(resourceId: Int, uri: String) = {
    try {
      _mediaManager.foreach { mm =>
        if (TextUtils.isEmpty(uri)) mm.unregisterMedia(getResEntryName(resourceId))
        else mm.registerMediaFileUrl(getResEntryName(resourceId), Uri.parse(uri))
      }
    }
    catch {
      case e: Exception => error(s"Could not set custom uri: $uri", e)
    }
  }

  private def isVibrationEnabled: Boolean = {
    val preferences = cxt.getSharedPreferences(UserPreferencesController.USER_PREFS_TAG, Context.MODE_PRIVATE)
    preferences.getBoolean(getString(R.string.pref_options_vibration_key), true)
  }
}
