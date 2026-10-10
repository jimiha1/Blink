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
        applyStoredLanguage()
        createNotificationChannels()
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
            getString(R.string.notif_channel_service_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notif_channel_service_desc)
            setShowBadge(false)
        }

        val alertChannel = NotificationChannel(
            CHANNEL_ALERT,
            getString(R.string.notif_channel_alert_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.notif_channel_alert_desc)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500)
            setBypassDnd(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }

        manager.createNotificationChannels(listOf(serviceChannel, alertChannel))
    }
}
