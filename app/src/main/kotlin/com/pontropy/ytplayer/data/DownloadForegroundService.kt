package com.pontropy.ytplayer.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.pontropy.ytplayer.MainActivity
import com.pontropy.ytplayer.YTPlayerApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class DownloadForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observerJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as YTPlayerApplication
        if (intent?.action == ACTION_CANCEL) {
            app.downloadController.cancelAllActive()
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }

        val initialNotification = buildNotification(
            title = "YT Downloader: загрузка",
            content = "Подготовка очереди...",
            progressPercent = null,
        )
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            initialNotification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )

        if (observerJob?.isActive != true) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            var lastNotifyAtMs = 0L
            observerJob = serviceScope.launch {
                combine(
                    app.downloadController.progress,
                    app.downloadController.queue,
                ) { progress, queue -> progress to queue }
                    .collect { (progress, queue) ->
                        val activeOrPending = queue.count {
                            it.status == QueueItemStatus.PENDING || it.status == QueueItemStatus.DOWNLOADING
                        }
                        if (!progress.isActive && activeOrPending == 0) {
                            stopForegroundCompat()
                            stopSelf()
                            return@collect
                        }

                        val nowMs = System.currentTimeMillis()
                        if (nowMs - lastNotifyAtMs < 350L) return@collect
                        lastNotifyAtMs = nowMs

                        val title = progress.activeItemTitle.ifBlank { "Загрузка медиа" }
                        val body = if (activeOrPending > 1) {
                            "${progress.statusText} (осталось: $activeOrPending)"
                        } else {
                            progress.statusText
                        }
                        nm.notify(
                            NOTIFICATION_ID,
                            buildNotification(
                                title = title,
                                content = body,
                                progressPercent = progress.progressPercent,
                            ),
                        )
                    }
            }
        }
        return START_NOT_STICKY
    }

    private fun stopForegroundCompat() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        observerJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Загрузки видео и музыки",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Прогресс фонового скачивания через yt-dlp"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(
        title: String,
        content: String,
        progressPercent: Float?,
    ): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPending = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val cancelIntent = Intent(this, DownloadForegroundService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPending = PendingIntent.getService(
            this,
            1,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val pctInt = progressPercent?.toInt()?.coerceIn(0, 100) ?: 0
        val indeterminate = progressPercent == null

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(content)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(openPending)
            .setProgress(100, pctInt, indeterminate)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Отмена",
                cancelPending,
            )
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "yt_player_downloads"
        private const val NOTIFICATION_ID = 4102
        private const val ACTION_CANCEL = "com.pontropy.ytplayer.ACTION_CANCEL_DOWNLOADS"

        fun ensureStarted(context: Context) {
            runCatching {
                val intent = Intent(context, DownloadForegroundService::class.java)
                ContextCompat.startForegroundService(context, intent)
            }
        }
    }
}
