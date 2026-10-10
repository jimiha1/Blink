package com.cflat.blink.util

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Process
import android.provider.Settings
import android.util.Log

/**
 * Detects the current foreground app so whitelisted apps can suppress the rest
 * overlay. Relies on the "Usage access" special permission, which the user must
 * grant in system settings; without it the caller should fail open (keep showing
 * the overlay) rather than silently never reminding.
 */
object ForegroundApp {

    private const val TAG = "ForegroundApp"
    private const val LOOKBACK_MS = 10 * 60 * 1000L

    /** True once the user has granted "Usage access" to this app. */
    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = try {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } catch (e: Exception) {
            Log.w(TAG, "unsafeCheckOpNoThrow failed", e)
            AppOpsManager.MODE_ERRORED
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Deep-links to the system "Usage access" screen for this app. */
    fun openUsageAccessSettings(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "No usage access settings screen", e)
            // Fall back to this app's detail page, which also exposes the toggle.
            try {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = android.net.Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            } catch (e2: Exception) {
                Log.e(TAG, "No app details screen either", e2)
            }
        }
    }

    /** The package currently in the foreground, or null if it cannot be determined. */
    fun currentForegroundPackage(context: Context): String? {
        if (!hasUsageAccess(context)) return null
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val events = usm.queryEvents(now - LOOKBACK_MS, now)
            val event = UsageEvents.Event()
            var latest: String? = null
            var latestTs = 0L
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED &&
                    event.timeStamp >= latestTs
                ) {
                    latest = event.packageName
                    latestTs = event.timeStamp
                }
            }
            latest
        } catch (e: Exception) {
            Log.w(TAG, "queryEvents failed", e)
            null
        }
    }

    /**
     * True when a whitelisted app is in the foreground. Fails open: returns false
     * when usage access is missing or the foreground app is unknown, so the overlay
     * still appears rather than being silently suppressed.
     */
    fun isWhitelistedAppInForeground(context: Context): Boolean {
        val whitelist = PrefsManager.getWhitelist(context)
        if (whitelist.isEmpty()) return false
        val fg = currentForegroundPackage(context) ?: return false
        return whitelist.contains(fg)
    }

    /** Human-readable label for a package, falling back to the package name. */
    fun appLabel(context: Context, packageName: String): String = try {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationLabel(info).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        packageName
    }

    data class AppEntry(
        val packageName: String,
        val label: String,
        val icon: Drawable?
    )

    /** Launchable apps for the whitelist picker, sorted by label. */
    fun getLaunchableApps(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = try {
            pm.queryIntentActivities(intent, 0)
        } catch (e: Exception) {
            Log.w(TAG, "queryIntentActivities failed", e)
            emptyList()
        }
        return resolved
            .asSequence()
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .map { info ->
                AppEntry(
                    packageName = info.packageName,
                    label = pm.getApplicationLabel(info).toString(),
                    icon = try {
                        pm.getApplicationIcon(info)
                    } catch (e: PackageManager.NameNotFoundException) {
                        null
                    }
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }
}
