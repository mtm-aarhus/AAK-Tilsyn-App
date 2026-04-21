package com.aak.tilsynsapp

import android.content.Context
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

class AppUpdater(context: Context) {
    private val manager: AppUpdateManager = AppUpdateManagerFactory.create(context)
    private val options = AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()

    fun checkForUpdate(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        manager.appUpdateInfo
            .addOnSuccessListener { info ->
                val updateAvailable = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                if (updateAvailable && info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                    manager.startUpdateFlowForResult(info, launcher, options)
                }
            }
            .addOnFailureListener { e -> Log.w("AppUpdater", "Update check failed", e) }
    }

    fun resumeIfInProgress(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        manager.appUpdateInfo
            .addOnSuccessListener { info ->
                if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                    manager.startUpdateFlowForResult(info, launcher, options)
                }
            }
    }
}
