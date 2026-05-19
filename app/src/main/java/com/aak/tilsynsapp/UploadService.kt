package com.aak.tilsynsapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/**
 * Foreground service that handles image uploads for inspections.
 *
 * Lifted out of the UI so uploads survive the user locking the phone or switching apps —
 * a foreground service with a persistent notification is the only reliable way to keep
 * network work running while backgrounded (WorkManager would be heavier and has its own
 * scheduling delays).
 *
 * Each "batch" is a single inspection's images: caller writes the processed JPEGs to the
 * cache dir and passes the file paths via Intent extras. The service uploads them
 * sequentially via [ApiHelper.uploadImage], updating the notification with progress.
 * Multiple batches can be queued — they're processed in order.
 *
 * Retry behaviour (for field workers walking in and out of coverage):
 *  - If the device loses network, the upload suspends until network is back — "free" wait
 *    that doesn't count against the attempt budget.
 *  - If the upload fails while still online, we retry with exponential back-off
 *    (~2 minutes of patient retrying spread across 5 attempts) before giving up.
 *  - Per-file budget: failures on one image don't sink the rest of the batch.
 *
 * User can cancel via the notification's "Annullér" action; remaining files in the
 * current batch are discarded.
 */
class UploadService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var workerJob: Job? = null

    // Each batch = one inspection's set of image files plus a human-readable label.
    private data class Batch(
        val itemId: String,
        val label: String,
        val files: List<String>,
    )

    private val queue = ConcurrentLinkedQueue<Batch>()
    private val totalEnqueued = AtomicInteger(0)
    private val totalCompleted = AtomicInteger(0)
    private val totalFailed = AtomicInteger(0)
    @Volatile private var currentLabel: String = ""
    // Extra single-line status appended to the notification body when relevant —
    // e.g. "Venter på forbindelse…" or "Forsøger igen om 15 s…". null = no overlay.
    @Volatile private var currentStatus: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                Log.d(TAG, "Cancel requested via notification")
                workerJob?.cancel()
                queue.clear()
                postFinalNotification(cancelled = true)
                stopSelfAndForeground()
                return START_NOT_STICKY
            }
            ACTION_ENQUEUE -> {
                val itemId = intent.getStringExtra(EXTRA_ITEM_ID)
                val label = intent.getStringExtra(EXTRA_LABEL) ?: "tilsyn"
                val files = intent.getStringArrayListExtra(EXTRA_FILES) ?: arrayListOf()
                if (itemId.isNullOrBlank() || files.isEmpty()) {
                    Log.w(TAG, "Ignoring empty enqueue (id=$itemId, files=${files.size})")
                    return START_NOT_STICKY
                }
                enqueue(Batch(itemId, label, files))
                ensurePromoted()
                ensureWorkerRunning()
            }
            else -> {
                Log.w(TAG, "Unknown action: ${intent?.action}")
            }
        }
        return START_NOT_STICKY
    }

    private fun enqueue(batch: Batch) {
        queue.add(batch)
        totalEnqueued.addAndGet(batch.files.size)
        currentLabel = batch.label
        updateNotification()
    }

    private fun ensurePromoted() {
        // Move into foreground state with the persistent notification. Safe to call
        // repeatedly; subsequent calls just update the notification.
        ensureChannel(this)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
    }

    private fun ensureWorkerRunning() {
        if (workerJob?.isActive == true) return
        workerJob = serviceScope.launch {
            try {
                while (true) {
                    val batch = queue.poll() ?: break
                    processBatch(batch)
                }
                postFinalNotification(cancelled = false)
            } catch (_: CancellationException) {
                // Cancellation already posted the final notification.
            } finally {
                stopSelfAndForeground()
            }
        }
    }

    private suspend fun processBatch(batch: Batch) {
        for (path in batch.files) {
            currentStatus = null
            updateNotification()
            val file = File(path)
            if (!file.exists()) {
                Log.w(TAG, "Missing file: $path")
                totalFailed.incrementAndGet()
                continue
            }
            val ok = try {
                uploadWithRetry(file, batch.itemId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Upload failed irrecoverably for $path", e)
                false
            }
            if (ok) totalCompleted.incrementAndGet() else totalFailed.incrementAndGet()
            // Always delete the cached file — retries have been exhausted by this point,
            // and the "done" notification at the end summarises any failures.
            file.delete()
            currentStatus = null
        }
    }

    /**
     * Upload one file with network-aware retry.
     *
     * - If the device is offline, we suspend until a network with INTERNET capability is
     *   available again — this doesn't count against the attempt budget, so a user walking
     *   in and out of coverage isn't punished by repeated failures.
     * - If the upload fails *while still online*, we count the attempt and back off
     *   exponentially before trying again.
     * - After the back-off schedule is exhausted we give up on this file. The "done"
     *   notification reports how many failed in total.
     */
    private suspend fun uploadWithRetry(file: File, itemId: String): Boolean {
        // Back-off schedule (seconds) for successive *online* failures.
        // Roughly 2s + 5s + 15s + 30s + 60s ≈ 2 minutes of patient retrying before giving up.
        val backoffSec = intArrayOf(2, 5, 15, 30, 60)
        var attempt = 0

        while (true) {
            // Step 1: Make sure we have network. If we don't, wait — this is "free" and
            // doesn't consume an attempt.
            if (!ApiHelper.hasInternet(applicationContext)) {
                currentStatus = "Venter på forbindelse…"
                updateNotification()
                awaitNetwork()
                currentStatus = null
                updateNotification()
            }

            // Step 2: Try the upload.
            val ok = try {
                ApiHelper.uploadImage(
                    context = applicationContext,
                    id = itemId,
                    imageBytes = file.readBytes(),
                    fileName = file.name,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Upload attempt ${attempt + 1} failed: ${e.message}")
                false
            }
            if (ok) return true

            // Step 3: Decide whether to retry. If we lost network during the upload
            // (very common in the field), just loop back — we'll wait for network
            // again at the top without spending an attempt.
            if (!ApiHelper.hasInternet(applicationContext)) {
                Log.d(TAG, "Lost network mid-upload, will wait and retry without counting")
                continue
            }

            // Online failure (server 5xx, timeout, …). Burn an attempt and back off.
            if (attempt >= backoffSec.size) {
                Log.w(TAG, "Giving up on ${file.name} after ${backoffSec.size} online attempts")
                return false
            }
            val waitSec = backoffSec[attempt]
            attempt++
            currentStatus = "Forsøger igen om $waitSec s… (forsøg ${attempt + 1}/${backoffSec.size + 1})"
            updateNotification()
            delay(waitSec * 1000L)
            currentStatus = null
        }
        @Suppress("UNREACHABLE_CODE")
        return false
    }

    /**
     * Suspends until the device has an active network with INTERNET capability.
     * Unregisters the callback on success or on coroutine cancellation.
     */
    private suspend fun awaitNetwork() {
        val cm = applicationContext.getSystemService(CONNECTIVITY_SERVICE)
            as? ConnectivityManager ?: return  // No ConnectivityManager — just return and let the upload try
        if (ApiHelper.hasInternet(applicationContext)) return

        suspendCancellableCoroutine<Unit> { cont ->
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    // Double-check capability — some "available" networks are captive portals
                    // without real INTERNET. The hasInternet() helper checks NET_CAPABILITY_INTERNET
                    // which is enough for our needs.
                    try { cm.unregisterNetworkCallback(this) } catch (_: Throwable) {}
                    if (cont.isActive) cont.resume(Unit)
                }
            }
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            try {
                cm.registerNetworkCallback(request, callback)
            } catch (e: Throwable) {
                // If registration fails for any reason, resume immediately so we don't deadlock.
                Log.w(TAG, "registerNetworkCallback failed: ${e.message}")
                if (cont.isActive) cont.resume(Unit)
                return@suspendCancellableCoroutine
            }
            cont.invokeOnCancellation {
                try { cm.unregisterNetworkCallback(callback) } catch (_: Throwable) {}
            }
        }
    }

    private fun buildNotification(): android.app.Notification {
        val done = totalCompleted.get() + totalFailed.get()
        val total = totalEnqueued.get().coerceAtLeast(1)
        val title = "Uploader billeder"
        val baseText = "Billede ${(done + 1).coerceAtMost(total)} af $total · ${currentLabel.ifBlank { "tilsyn" }}"
        val statusOverlay = currentStatus
        val text = if (statusOverlay != null) "$baseText\n$statusOverlay" else baseText

        val cancelPi = PendingIntent.getService(
            this,
            REQUEST_CANCEL,
            Intent(this, UploadService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val openPi = PendingIntent.getActivity(
            this,
            REQUEST_OPEN,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(total, done, statusOverlay != null || total == 0)
            .setContentIntent(openPi)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Annullér", cancelPi)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun postFinalNotification(cancelled: Boolean) {
        val completed = totalCompleted.get()
        val failed = totalFailed.get()
        val totalSeen = totalEnqueued.get()
        val unfinished = (totalSeen - completed - failed).coerceAtLeast(0)

        val (title, text) = when {
            cancelled -> "Upload annulleret" to
                "$completed færdige · ${failed + unfinished} ikke uploadet"
            failed > 0 -> "Upload færdig (med fejl)" to
                "$completed af $totalSeen billeder uploadet · $failed gav op efter flere forsøg"
            else -> "Upload færdig" to "$completed billeder uploadet"
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        // Different ID so it doesn't get replaced by the ongoing one we're about to cancel.
        manager.notify(NOTIFICATION_ID_DONE, notification)
    }

    private fun stopSelfAndForeground() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "UploadService"
        const val CHANNEL_ID = "uploads"
        private const val NOTIFICATION_ID = 5001
        private const val NOTIFICATION_ID_DONE = 5002
        private const val REQUEST_CANCEL = 1
        private const val REQUEST_OPEN = 2

        const val ACTION_ENQUEUE = "com.aak.tilsynsapp.action.ENQUEUE_UPLOAD"
        const val ACTION_CANCEL = "com.aak.tilsynsapp.action.CANCEL_UPLOADS"
        const val EXTRA_ITEM_ID = "item_id"
        const val EXTRA_LABEL = "label"
        const val EXTRA_FILES = "files"

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Billed-uploads",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Viser status mens billeder uploades til tilsyn"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }

        /**
         * Helper that callers use to hand a batch of pre-saved JPEGs over to the service.
         * Files are expected to live somewhere readable by this process (cache dir is fine).
         */
        fun enqueueUploads(
            context: Context,
            itemId: String,
            label: String,
            filePaths: List<String>,
        ) {
            if (filePaths.isEmpty()) return
            ensureChannel(context)
            val intent = Intent(context, UploadService::class.java).apply {
                action = ACTION_ENQUEUE
                putExtra(EXTRA_ITEM_ID, itemId)
                putExtra(EXTRA_LABEL, label)
                putStringArrayListExtra(EXTRA_FILES, ArrayList(filePaths))
            }
            // Foreground services started from a foreground app are fine without
            // startForegroundService restrictions — but use it for consistency.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
