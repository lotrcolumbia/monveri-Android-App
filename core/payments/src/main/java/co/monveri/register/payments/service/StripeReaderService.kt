package co.monveri.register.payments.service

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
import androidx.core.content.ContextCompat

/**
 * Foreground service that pins the app's process while a Stripe reader is paired. Required by
 * Android 14+ to keep the Bluetooth link alive when the user backgrounds the app — without this
 * Doze mode kills the radio after a few minutes and the next charge fails with "reader offline".
 *
 * The service does no work itself: its presence in the foreground service list is the entire
 * point. Started by the Reader Settings UI on first connect, stopped on "Forget reader" or sign-out.
 *
 * Service type: `connectedDevice` — matches the Bluetooth peripheral category Android 14
 * introduced. Launching with the wrong type silently fails.
 */
class StripeReaderService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14 — must specify the service type at startForeground time.
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        // STICKY: if the system kills us under memory pressure the OS recreates the service so
        // the Bluetooth link survives across low-memory events.
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        ensureChannel(this)
        // Tap → open the launcher activity (the merged manifest sets up MainActivity as MAIN).
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentIntent = launchIntent?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Reader connected")
            .setContentText("Monveri Register is keeping the card reader online.")
            // Falls back to the app icon (the launcher icon ships in :app via mipmap).
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "stripe_reader"
        private const val CHANNEL_NAME = "Card reader"
        private const val NOTIFICATION_ID = 4_001

        /** Idempotent — start whenever a reader connects. */
        fun start(context: Context) {
            val intent = Intent(context, StripeReaderService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        /** Idempotent — stop on disconnect / forget / sign-out. */
        fun stop(context: Context) {
            val intent = Intent(context, StripeReaderService::class.java)
            context.stopService(intent)
        }

        private fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Persistent notification while a Stripe card reader is paired."
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }
}
