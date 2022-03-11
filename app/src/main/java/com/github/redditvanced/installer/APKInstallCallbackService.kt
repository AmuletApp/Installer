package com.github.redditvanced.installer

import android.app.Service
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.IBinder
import android.util.Log

class APKInstallCallbackService : Service() {
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        when (val statusCode = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                Log.i("Installer", "Requesting user confirmation for installation")
                val confirmationIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirmationIntent!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(confirmationIntent)
            }
            PackageInstaller.STATUS_SUCCESS ->
                Log.i("Installer", "Installation succeeded")
            else ->
                Log.i("Installer", "Installation failed: error code $statusCode")
        }
        stopSelf()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }
}
