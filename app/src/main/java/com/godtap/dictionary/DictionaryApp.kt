package com.godtap.dictionary

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class DictionaryApp : Application() {
    
    companion object {
        const val NOTIFICATION_CHANNEL_ID = "dictionary_service_channel"
        lateinit var instance: DictionaryApp
            private set
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}
