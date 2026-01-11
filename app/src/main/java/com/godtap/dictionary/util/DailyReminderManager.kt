package com.godtap.dictionary.util

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.godtap.dictionary.MainActivity
import com.godtap.dictionary.R
import java.util.Calendar

/**
 * Manages daily reminder notifications to encourage users to study their target language
 */
class DailyReminderManager(private val context: Context) {
    
    companion object {
        private const val TAG = "DailyReminderManager"
        private const val CHANNEL_ID = "daily_reminders"
        private const val NOTIFICATION_ID = 1001
        private const val REQUEST_CODE = 1002
        
        private val REMINDER_MESSAGES = listOf(
            "📚 Time to practice your %s!",
            "🌟 Daily study time! Learn some %s today",
            "💡 Keep your %s skills sharp with TapDictionary",
            "✨ Don't forget to study %s today!",
            "🎯 Your daily %s practice awaits!",
            "📖 Expand your %s vocabulary today",
            "🚀 Level up your %s with TapDictionary!"
        )
    }
    
    private val sharedPrefs = context.getSharedPreferences("dictionary_prefs", Context.MODE_PRIVATE)
    
    /**
     * Enable daily reminders at specified hour (24-hour format)
     */
    fun enableReminders(hourOfDay: Int = 9, minute: Int = 0) {
        Log.d(TAG, "Enabling daily reminders at $hourOfDay:$minute")
        
        // Create notification channel
        createNotificationChannel()
        
        // Schedule the alarm
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, DailyReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Set the alarm to start at the specified time
        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            
            // If the time has already passed today, schedule for tomorrow
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        
        // Schedule repeating alarm
        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
        
        // Save preference
        sharedPrefs.edit().putBoolean("daily_reminders", true).apply()
        sharedPrefs.edit().putInt("reminder_hour", hourOfDay).apply()
        sharedPrefs.edit().putInt("reminder_minute", minute).apply()
        
        Log.d(TAG, "Daily reminders scheduled for ${calendar.time}")
    }
    
    /**
     * Disable daily reminders
     */
    fun disableReminders() {
        Log.d(TAG, "Disabling daily reminders")
        
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, DailyReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        alarmManager.cancel(pendingIntent)
        
        // Save preference
        sharedPrefs.edit().putBoolean("daily_reminders", false).apply()
        
        Log.d(TAG, "Daily reminders disabled")
    }
    
    /**
     * Check if reminders are enabled
     */
    fun areRemindersEnabled(): Boolean {
        return sharedPrefs.getBoolean("daily_reminders", false)
    }
    
    /**
     * Create notification channel for Android O and above
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Daily Reminders"
            val descriptionText = "Notifications to remind you to study your target language"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Show the actual notification
     */
    fun showReminderNotification() {
        Log.d(TAG, "Showing reminder notification")
        
        // Get target language from dictionary manager
        val targetLanguage = try {
            val dictionaryManager = com.godtap.dictionary.manager.DictionaryManager(context)
            kotlinx.coroutines.runBlocking {
                dictionaryManager.getActiveDictionary()?.targetLanguage ?: "English"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get target language", e)
            "your target language"
        }
        
        val languageName = getLanguageDisplayName(targetLanguage)
        
        // Pick a random message
        val message = REMINDER_MESSAGES.random().format(languageName)
        
        // Create intent to open the app
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Build the notification
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_dictionary) // Use dictionary icon for notifications
            .setContentTitle("TapDictionary")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        // Show the notification
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
        
        Log.d(TAG, "Reminder notification shown: $message")
    }
    
    private fun getLanguageDisplayName(code: String): String {
        return when (code) {
            "ja" -> "Japanese"
            "en" -> "English"
            "es" -> "Spanish"
            "ko" -> "Korean"
            "zh" -> "Chinese"
            "fr" -> "French"
            "de" -> "German"
            else -> code
        }
    }
}

/**
 * BroadcastReceiver that handles the daily reminder alarm
 */
class DailyReminderReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("DailyReminderReceiver", "Received daily reminder broadcast")
        
        // Show the notification
        val reminderManager = DailyReminderManager(context)
        reminderManager.showReminderNotification()
    }
}
