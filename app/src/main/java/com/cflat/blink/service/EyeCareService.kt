package com.cflat.blink.service

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.cflat.blink.EyeCareApp
import com.cflat.blink.R
import com.cflat.blink.receiver.AlarmReceiver
import com.cflat.blink.ui.MainActivity
import com.cflat.blink.util.ForegroundApp
import com.cflat.blink.util.PrefsManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class EyeCareService : Service() {

    companion object {
        private const val TAG = "EyeCareService"
        private const val NOTIFICATION_ID = 1001
        private const val ALERT_NOTIFICATION_ID = 1002
        private const val ALARM_REQUEST_CODE = 2001
        private const val WATCHDOG_REQUEST_CODE = 2002
        private const val WATCHDOG_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes

        const val ACTION_ALARM_FIRED = "com.cflat.blink.ALARM_FIRED"
        const val ACTION_START_TIMER = "com.cflat.blink.START_TIMER"
        const val ACTION_STOP_TIMER = "com.cflat.blink.STOP_TIMER"
        const val ACTION_WATCHDOG = "com.cflat.blink.WATCHDOG"
        const val ACTION_NEED_EXACT_ALARM_PERMISSION = "com.cflat.blink.NEED_EXACT_ALARM_PERMISSION"
        const val LOCK_RESET_THRESHOLD_MS = 20_000L // reset only if locked > 20s

        private val _remainingMs = MutableStateFlow(20 * 60 * 1000L)
        val remainingMs: StateFlow<Long> = _remainingMs.asStateFlow()

        private val _isResting = MutableStateFlow(false)
        val isResting: StateFlow<Boolean> = _isResting.asStateFlow()

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    }

    private val timerDurationMs: Long
        get() = PrefsManager.getWorkDurationMs(this)

    private val restDurationMs: Long
        get() = PrefsManager.getRestDurationMs(this)

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var countdownJob: Job? = null
    private var isScreenOn = true
    private var screenOffElapsed: Long = 0L
    private var remainingMsAtScreenOff: Long = 0L

    // Persisted so we can resume after OS-killed service restart
    private var timerStartElapsed: Long
        get() = getSharedPreferences("svc_state", Context.MODE_PRIVATE)
            .getLong("timer_start_elapsed", 0L)
        set(value) = getSharedPreferences("svc_state", Context.MODE_PRIVATE)
            .edit().putLong("timer_start_elapsed", value).apply()

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    screenOffElapsed = SystemClock.elapsedRealtime()
                    remainingMsAtScreenOff = _remainingMs.value
                    Log.d(TAG, "Screen OFF - pausing timer (${remainingMsAtScreenOff}ms remaining)")
                    cancelAlarm()
                    stopCountdown()
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    val lockedForMs = SystemClock.elapsedRealtime() - screenOffElapsed
                    Log.d(TAG, "Screen ON - was locked for ${lockedForMs}ms")
                    if (lockedForMs >= LOCK_RESET_THRESHOLD_MS) {
                        Log.d(TAG, "Locked > 20s — starting fresh cycle")
                        startFreshCycle()
                    } else {
                        Log.d(TAG, "Locked < 20s — resuming from ${remainingMsAtScreenOff}ms")
                        resumeCycle(remainingMsAtScreenOff)
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildServiceNotification(getString(R.string.notif_starting)))
        registerScreenReceiver()
        scheduleWatchdog()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Always call startForeground first to satisfy Android 14 FGS requirement
        if (getSystemService(android.app.NotificationManager::class.java)
                .activeNotifications.none { it.id == NOTIFICATION_ID }) {
            startForeground(NOTIFICATION_ID, buildServiceNotification(getString(R.string.notif_resuming)))
        }

        when (intent?.action) {
            ACTION_ALARM_FIRED -> handleAlarmFired()
            ACTION_WATCHDOG -> {
                Log.d(TAG, "Watchdog fired - service is alive, rescheduling")
                scheduleWatchdog()
            }
            ACTION_STOP_TIMER -> {
                timerStartElapsed = 0L
                cancelWatchdog()
                stopSelf()
                return START_NOT_STICKY
            }
            null -> {
                // OS restarted the service after killing it (START_STICKY).
                // Only resume if we have a valid prior start time; otherwise start fresh.
                if (timerStartElapsed > 0L) {
                    val elapsed = SystemClock.elapsedRealtime() - timerStartElapsed
                    val remaining = timerDurationMs - elapsed
                    if (remaining > 0) {
                        Log.d(TAG, "Service restarted by OS - resuming existing countdown")
                        _isRunning.value = true
                        startForeground(NOTIFICATION_ID, buildServiceNotification(getString(R.string.notif_resuming)))
                        _remainingMs.value = remaining
                        scheduleRestAlarm(remaining) // re-arm: the original alarm died with the process
                        startCountdown()
                    } else {
                        Log.d(TAG, "Service restarted by OS - countdown already elapsed, starting fresh")
                        _isRunning.value = true
                        startForeground(NOTIFICATION_ID, buildServiceNotification(getString(R.string.notif_starting)))
                        startFreshCycle()
                    }
                } else {
                    Log.d(TAG, "Service restarted by OS with no prior state - starting fresh")
                    _isRunning.value = true
                    startForeground(NOTIFICATION_ID, buildServiceNotification(getString(R.string.notif_starting)))
                    startFreshCycle()
                }
            }
            else -> {
                // Explicit start from user or boot receiver
                Log.d(TAG, "Service started explicitly")
                startForeground(NOTIFICATION_ID, buildServiceNotification(getString(R.string.notif_starting)))
                _isRunning.value = true
                startFreshCycle()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        _isRunning.value = false
        _isResting.value = false
        cancelAlarm()
        stopCountdown()
        unregisterReceiver(screenReceiver)
        serviceScope.cancel()
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenReceiver, filter)
    }

    private fun startFreshCycle() {
        _isResting.value = false
        _remainingMs.value = timerDurationMs
        timerStartElapsed = 0L // will be set properly inside scheduleExactAlarm
        scheduleExactAlarm()
        startCountdown()
    }

    private fun resumeCycle(remainingMs: Long) {
        if (remainingMs <= 0) {
            startFreshCycle()
            return
        }
        _isResting.value = false
        _remainingMs.value = remainingMs
        timerStartElapsed = SystemClock.elapsedRealtime() - (timerDurationMs - remainingMs)
        scheduleRestAlarm(remainingMs)
        startCountdown()
    }

    private fun scheduleExactAlarm() {
        timerStartElapsed = SystemClock.elapsedRealtime()
        scheduleRestAlarm(timerDurationMs)
    }

    /**
     * Schedules the rest alarm. Prefers setAlarmClock() — a user-visible alarm the
     * system treats as high priority and does not defer — because OEM power managers
     * (e.g. Honor's PowerGenie) freeze a backgrounded app and push a plain
     * setExactAndAllowWhileIdle() alarm days into the future, so the rest overlay
     * never appeared. Falls back to an inexact alarm when the exact-alarm permission
     * is missing.
     */
    private fun scheduleRestAlarm(remainingMs: Long) {
        val alarmManager = getSystemService(AlarmManager::class.java)
        val pendingIntent = restAlarmPendingIntent()

        if (alarmManager.canScheduleExactAlarms()) {
            val showIntent = PendingIntent.getActivity(
                this,
                ALARM_REQUEST_CODE,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(System.currentTimeMillis() + remainingMs, showIntent),
                pendingIntent
            )
            Log.d(TAG, "AlarmClock scheduled for ${remainingMs / 1000}s from now")
        } else {
            Log.w(TAG, "Cannot schedule exact alarms - permission not granted. Sending broadcast.")
            sendBroadcast(Intent(ACTION_NEED_EXACT_ALARM_PERMISSION))
            alarmManager.set(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + remainingMs,
                pendingIntent
            )
            Log.w(TAG, "Inexact alarm scheduled (exact alarm permission missing)")
        }
    }

    private fun restAlarmPendingIntent(): PendingIntent {
        val intent = Intent(this, AlarmReceiver::class.java).apply { action = ACTION_ALARM_FIRED }
        return PendingIntent.getBroadcast(
            this,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun cancelAlarm() {
        getSystemService(AlarmManager::class.java).cancel(restAlarmPendingIntent())
    }

    private fun scheduleWatchdog() {
        val alarmManager = getSystemService(AlarmManager::class.java)
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            action = ACTION_WATCHDOG
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            WATCHDOG_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = SystemClock.elapsedRealtime() + WATCHDOG_INTERVAL_MS
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            triggerAt,
            pendingIntent
        )
    }

    private fun cancelWatchdog() {
        val alarmManager = getSystemService(AlarmManager::class.java)
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            action = ACTION_WATCHDOG
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            WATCHDOG_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun startCountdown() {
        stopCountdown()
        countdownJob = serviceScope.launch {
            while (isActive) {
                val elapsed = SystemClock.elapsedRealtime() - timerStartElapsed
                val remaining = (timerDurationMs - elapsed).coerceAtLeast(0)
                _remainingMs.value = remaining
                // Re-read alertMode every tick so settings changes take effect immediately
                if (PrefsManager.getAlertMode(this@EyeCareService) != "overlay") {
                    updateNotification(formatTime(remaining))
                }
                if (remaining <= 0) break
                delay(1000)
            }
        }
    }

    private fun stopCountdown() {
        countdownJob?.cancel()
        countdownJob = null
    }

    private fun handleAlarmFired() {
        Log.d(TAG, "ALARM FIRED - Time to rest eyes!")
        _isResting.value = true
        _remainingMs.value = 0
        stopCountdown()

        val alertMode = PrefsManager.getAlertMode(this)
        // A whitelisted app in the foreground suppresses the full-screen overlay,
        // but a notification still fires so the user is not left with nothing.
        val suppressed = ForegroundApp.isWhitelistedAppInForeground(this)
        if (suppressed) {
            Log.d(TAG, "Whitelisted app in foreground - skipping overlay, notification only")
        }
        if (alertMode != "overlay" || suppressed) {
            showAlertNotification()
        }
        if (!suppressed && alertMode != "notification" && android.provider.Settings.canDrawOverlays(this)) {
            RestOverlayService.start(this)
        }

        updateNotification(getString(R.string.notif_resting, restDurationMs / 1000))

        serviceScope.launch {
            delay(restDurationMs)
            dismissAlertNotification()
            if (isScreenOn) {
                startFreshCycle()
            }
        }
    }

    private fun showAlertNotification() {
        val notification = NotificationCompat.Builder(this, EyeCareApp.CHANNEL_ALERT)
            .setContentTitle(getString(R.string.notif_alert_title))
            .setContentText(getString(R.string.notif_alert_text))
            .setSmallIcon(R.drawable.ic_eye)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .build()

        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.notify(ALERT_NOTIFICATION_ID, notification)
    }

    private fun dismissAlertNotification() {
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.cancel(ALERT_NOTIFICATION_ID)
    }

    private fun buildServiceNotification(text: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, EyeCareApp.CHANNEL_SERVICE)
            .setContentTitle(getString(R.string.notif_service_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_eye)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildServiceNotification(text))
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return getString(R.string.notif_remaining, minutes, seconds)
    }
}
