package com.gulabastro.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gulabastro.app.astro.AstroEngine
import com.gulabastro.app.astro.BirthData
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ChandraAshtamWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("GulabAstroPrefs", Context.MODE_PRIVATE)
        val lat = prefs.getString("user_lat", null)?.toDoubleOrNull()
        val lon = prefs.getString("user_lon", null)?.toDoubleOrNull()
        val birthIso = prefs.getString("user_dob", null)

        // Agar user ka profile data saved nahi hai toh skip karein
        if (lat == null || lon == null || birthIso == null) {
            return Result.success()
        }

        val birthTime = runCatching { LocalDateTime.parse(birthIso) }.getOrNull() ?: return Result.success()
        val birthData = BirthData(
            name = prefs.getString("user_name", "User") ?: "User",
            localDateTime = birthTime,
            latitude = lat,
            longitude = lon,
            zoneId = ZoneId.systemDefault()
        )

        // Actual planetary calculation
        val chartResult = AstroEngine.calculate(birthData)
        val now = ZonedDateTime.now()
        val isAshtam = AstroEngine.chandraAshtam(chartResult, now)

        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel("chandra_ashtam", "Chandrama Ashtam", NotificationManager.IMPORTANCE_DEFAULT)
        nm.createNotificationChannel(channel)

        val message = if (isAshtam) {
            "सावधान: आज आपकी कुंडली के अनुसार चंद्र अष्टम चल रहा है। नए कार्यों में सतर्कता बरतें।"
        } else {
            "आज चंद्र अष्टम नहीं है। आपका दिन सामान्य और शुभ रहेगा।"
        }

        val notification = NotificationCompat.Builder(applicationContext, "chandra_ashtam")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Gulab Astro Alert")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .build()

        nm.notify(2409, notification)
        return Result.success()
    }
}
