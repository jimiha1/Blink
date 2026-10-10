package com.cflat.blink

import android.app.Application
import android.app.LocaleManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.os.LocaleList
import com.cflat.blink.util.PrefsManager

class EyeCareApp : Application() {

    companion object {
        const val CHANNEL_SERVICE = "eye_care_service"
        const val CHANNEL_ALERT = "eye_care_alert"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        applyStoredLanguage()
    }

    private fun applyStoredLanguage() {
        val tag = when (PrefsManager.getAppLanguage(this)) {
            "zh" -> "zh-CN"
            "en" -> "en"
            else -> ""            // 跟随系统
        }
        val lm = getSystemService(LocaleManager::class.java) ?: return
        lm.applicationLocales =
            if (tag.isEmpty()) LocaleList.getEmptyLocaleList()
            else LocaleList.forLanguageTags(tag)
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE,
            "Timer Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Persistent timer notification"
            setShowBadge(false)
        }

        val alertChannel = NotificationChannel(
            CHANNEL_ALERT,
            "Blink Alert",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "20-20-20 rule reminder"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500)
            setBypassDnd(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }

        manager.createNotificationChannels(listOf(serviceChannel, alertChannel))
    }
}
