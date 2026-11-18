package com.quran.labs.androidquran.ui.library

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.data.Constants
import com.quran.labs.androidquran.util.NotificationChannelUtil
import com.quran.mobile.feature.library.audio.PoemPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps a poem's recitation playing when its screen is left, with a notification (as the
 * mushaf's recitation has) to pause or resume it, go to the verse before or after, or stop it.
 * It runs while a recitation is being played or is paused.
 */
class PoemPlaybackService : Service() {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private lateinit var playback: PoemPlayback
  private lateinit var session: MediaSessionCompat
  private var foreground = false

  private val noisyReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      // (headphones unplugged)
      if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) playback.pause()
    }
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    playback = PoemPlayback.get(this)
    NotificationChannelUtil.setupNotificationChannel(
      getSystemService(NOTIFICATION_SERVICE) as NotificationManager,
      Constants.AUDIO_CHANNEL,
      getString(R.string.notification_channel_audio)
    )
    session = MediaSessionCompat(this, "PoemMediaSession").apply {
      setCallback(object : MediaSessionCompat.Callback() {
        override fun onPlay() { if (!playback.state.value.isPlaying) playback.toggle() }
        override fun onPause() = playback.pause()
        override fun onSkipToNext() = playback.nextVerse()
        override fun onSkipToPrevious() = playback.previousVerse()
        override fun onStop() = playback.stop()
      })
      isActive = true
    }
    ContextCompat.registerReceiver(
      this, noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED
    )
    scope.launch { playback.state.collect(::update) }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_TOGGLE -> playback.toggle()
      ACTION_NEXT -> playback.nextVerse()
      ACTION_PREVIOUS -> playback.previousVerse()
      ACTION_STOP -> playback.stop()
    }
    update(playback.state.value, starting = true)
    return START_NOT_STICKY
  }

  /** the app is closed (swiped away from the recent apps): the recitation ends with it */
  override fun onTaskRemoved(rootIntent: Intent?) {
    playback.stop()
    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    foreground = false
    stopSelf()
    super.onTaskRemoved(rootIntent)
  }

  override fun onDestroy() {
    scope.cancel()
    unregisterReceiver(noisyReceiver)
    session.isActive = false
    session.release()
    super.onDestroy()
  }

  private fun update(state: PoemPlayer.State, starting: Boolean = false) {
    val active = state.track != null && !state.failed
    if (active || starting) {
      // (a service started to be in the foreground must show its notification, even to stop at once)
      show(notification(state))
    }
    if (!active) {
      ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
      foreground = false
      stopSelf()
      return
    }
    session.setPlaybackState(
      PlaybackStateCompat.Builder()
        .setActions(
          PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_STOP
        )
        .setState(
          if (state.isPlaying || state.isPreparing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
          PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
          1f
        )
        .build()
    )
    session.setMetadata(
      MediaMetadataCompat.Builder()
        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title())
        .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, reciter())
        .build()
    )
  }

  private fun show(notification: Notification) {
    try {
      val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
      ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
      foreground = true
    } catch (e: IllegalStateException) {
      // (not allowed to come to the foreground from where it was started: the recitation goes
      // on while the app is open)
    }
  }

  private fun arabic() = resources.configuration.locales.get(0)?.language == "ar"
  private fun title() = playback.poem?.title(arabic()) ?: getString(R.string.app_name)
  private fun reciter() = playback.recitation?.reciter(arabic()).orEmpty()

  private fun notification(state: PoemPlayer.State): Notification {
    val playing = state.isPlaying || state.isPreparing
    val open = PendingIntent.getActivity(
      this, 0,
      playback.poem?.let { PoemActivity.intent(this, it.id) } ?: Intent(this, PoemActivity::class.java),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val verse = state.verse?.let {
      getString(com.quran.mobile.feature.library.R.string.library_verse_number, it)
    }
    val builder = NotificationCompat.Builder(this, Constants.AUDIO_CHANNEL)
      .setSmallIcon(R.drawable.ic_notification)
      .setColor(ContextCompat.getColor(this, R.color.audio_notification_color))
      .setContentTitle(title())
      .setContentText(listOfNotNull(reciter().takeIf { it.isNotEmpty() }, verse).joinToString(" · "))
      .setContentIntent(open)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .setOngoing(playing)
      .setOnlyAlertOnce(true)
      .setShowWhen(false)
      .setDeleteIntent(action(ACTION_STOP, 4))
      .addAction(R.drawable.ic_previous, getString(R.string.previous), action(ACTION_PREVIOUS, 1))
    if (playing) {
      builder.addAction(R.drawable.ic_pause, getString(R.string.pause), action(ACTION_TOGGLE, 2))
    } else {
      builder.addAction(
        com.quran.labs.androidquran.common.toolbar.R.drawable.ic_play, getString(R.string.play), action(ACTION_TOGGLE, 2)
      )
    }
    return builder
      .addAction(R.drawable.ic_next, getString(R.string.next), action(ACTION_NEXT, 3))
      .addAction(R.drawable.ic_stop, getString(R.string.stop), action(ACTION_STOP, 4))
      .setStyle(
        androidx.media.app.NotificationCompat.MediaStyle()
          .setShowActionsInCompactView(0, 1, 2)
          .setMediaSession(session.sessionToken)
      )
      .build()
  }

  private fun action(action: String, code: Int): PendingIntent =
    PendingIntent.getService(
      this, code, Intent(this, PoemPlaybackService::class.java).setAction(action),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

  companion object {
    private const val NOTIFICATION_ID = 0x504f454d // "POEM"
    private const val ACTION_TOGGLE = "com.quran.labs.androidquran.poem.TOGGLE"
    private const val ACTION_NEXT = "com.quran.labs.androidquran.poem.NEXT"
    private const val ACTION_PREVIOUS = "com.quran.labs.androidquran.poem.PREVIOUS"
    private const val ACTION_STOP = "com.quran.labs.androidquran.poem.STOP"

    /** called when a recitation starts (from the app, on screen) */
    fun start(context: Context) {
      try {
        ContextCompat.startForegroundService(context, Intent(context, PoemPlaybackService::class.java))
      } catch (e: IllegalStateException) {
        // (not allowed from the background: the recitation goes on while the app is open)
      }
    }
  }
}
