package com.gulabastro.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ChandraAshtamWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel("chandra_ashtam", "Chandrama Astam", NotificationManager.IMPORTANCE_DEFAULT)
        nm.createNotificationChannel(channel)
        
        val builder = NotificationCompat.Builder(applicationContext, "chandra_ashtam")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Gulab Astro")
            .setContentText("Check today's Chandrama Astam status in your Kundli.")
            .setAutoCancel(true)
            
        nm.notify(2409, builder.build())
        return Result.success()
    }
}
