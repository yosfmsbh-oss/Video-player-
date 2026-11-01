package com.videoplayer.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * خدمة أمامية تُبقي الصوت يعمل عند الخروج من التطبيق أو إطفاء الشاشة،
 * وتعرض إشعاراً فيه إيقاف مؤقت/تشغيل وإغلاق.
 * المشغّل نفسه يبقى مملوكاً لشاشة المشغّل؛ الخدمة تحمل فقط الإشعار وتحمي العملية من الإنهاء.
 */
class PlaybackService : Service() {

    private var attached: Player? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = refresh()

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        // يجب استدعاء startForeground دائماً بعد startForegroundService وإلا ينهار التطبيق
        ServiceCompat.startForeground(
            this, NOTIF_ID, buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
        val p = player
        if (p == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (attached !== p) {
            runCatching { attached?.removeListener(listener) }
            p.addListener(listener)
            attached = p
        }
        when (intent?.action) {
            ACTION_TOGGLE -> {
                if (p.isPlaying) p.pause() else p.play()
                refresh()
            }
            ACTION_STOP -> {
                p.pause()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        runCatching { attached?.removeListener(listener) }
        attached = null
        super.onDestroy()
    }

    private fun refresh() {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, getString(R.string.bg_channel_name), NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun servicePending(action: String, code: Int): PendingIntent =
        PendingIntent.getService(
            this, code,
            Intent(this, PlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun buildNotification(): Notification {
        val playing = player?.isPlaying == true
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(videoTitle.ifBlank { getString(R.string.app_name) })
            .setContentText(getString(if (playing) R.string.notif_playing else R.string.notif_paused))
            .setContentIntent(open)
            .setOngoing(playing)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                getString(if (playing) R.string.pause else R.string.play),
                servicePending(ACTION_TOGGLE, 1)
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.notif_close),
                servicePending(ACTION_STOP, 2)
            )
            .build()
    }

    companion object {
        private const val ACTION_TOGGLE = "com.videoplayer.app.TOGGLE"
        private const val ACTION_STOP = "com.videoplayer.app.STOP"
        private const val CHANNEL_ID = "playback"
        private const val NOTIF_ID = 1

        @Volatile
        private var player: ExoPlayer? = null

        @Volatile
        private var videoTitle: String = ""

        /** يبدأ الخدمة (آمن للاستدعاء أكثر من مرة). */
        fun start(context: Context, exo: ExoPlayer, title: String) {
            player = exo
            videoTitle = title
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java))
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PlaybackService::class.java))
        }

        /** عند إغلاق المشغّل نهائياً. */
        fun release(context: Context) {
            player = null
            stop(context)
        }
    }
}
