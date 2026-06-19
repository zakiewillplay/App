package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.*
import com.example.data.AppDatabase
import com.example.data.PageRepository
import com.example.data.WebMonitorWorker
import java.util.concurrent.TimeUnit

class WebMonitorApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var repository: PageRepository
        private set

    override fun onCreate() {
        super.onCreate()
        Log.d("WebMonitorApp", "Initializing Web Monitor Application components...")

        // 1. Initialize DB and Repository
        database = AppDatabase.getDatabase(this)
        repository = PageRepository(database.pageDao(), database.alertDao())

        // 2. Initialize Notification Channel for API 26+ (required to post local alerts)
        createNotificationChannel()

        // 3. Register WorkManager Periodic check
        scheduleWebChecks()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Web Monitor Alerts"
            val descriptionText = "Notifies when monitored webpages have status changes or keyword triggers."
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("web_monitor_alerts", name, importance).apply {
                description = descriptionText
                enableVibration(true)
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun scheduleWebChecks() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Minimum periodic interval is 15 minutes as per Android OS limits
        val periodicWorkRequest = PeriodicWorkRequestBuilder<WebMonitorWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                1, TimeUnit.MINUTES
            )
            .build()

        try {
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "PeriodicWebMonitorJob",
                ExistingPeriodicWorkPolicy.KEEP, // KEEP prevents resetting the schedule on relaunch
                periodicWorkRequest
            )
            Log.d("WebMonitorApp", "Periodic Web Monitor Job has been safely registered.")
        } catch (e: Exception) {
            Log.e("WebMonitorApp", "WorkManager schedule skipped (likely running in a test suite environment)", e)
        }
    }
}
