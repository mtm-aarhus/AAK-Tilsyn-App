package com.aak.tilsynsapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TilsynMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Log.d(TAG, "FCM token refreshed")
        CoroutineScope(Dispatchers.IO).launch {
            ApiHelper.registerFcmToken(applicationContext, token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val itemId = data["item_id"] ?: return
        val caseNumber = data["case_number"] ?: "?"
        val createdBy = data["created_by_initials"] ?: "?"

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_ITEM_ID, itemId)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            itemId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        ensureChannel(this)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Ny indmelding $caseNumber")
            .setContentText("Oprettet af $createdBy")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(itemId.hashCode(), notification)
    }

    companion object {
        const val CHANNEL_ID = "indmeldinger"
        const val EXTRA_OPEN_ITEM_ID = "open_item_id"
        private const val TAG = "TilsynFcm"

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Indmeldinger",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Notifikation når en kollega opretter et nyt indmeldt tilsyn"
            }
            manager.createNotificationChannel(channel)
        }
    }
}
