package com.hasanbucak.harcamatakip.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import com.hasanbucak.harcamatakip.service.YkbNotificationListenerService

object SystemPermissionHelper {

    private const val PREFS_NAME = "hb_app_prefs"
    private const val KEY_DISCLOSURE_ACCEPTED = "prominent_disclosure_accepted"

    fun isNotificationListenerEnabled(context: Context): Boolean {
        val cn = ComponentName(context, YkbNotificationListenerService::class.java)
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return flat.split(':').any { ComponentName.unflattenFromString(it) == cn }
    }

    fun hasAcceptedProminentDisclosure(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_DISCLOSURE_ACCEPTED, false)
    }

    fun setProminentDisclosureAccepted(context: Context, accepted: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DISCLOSURE_ACCEPTED, accepted)
            .apply()
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun buildNotificationListenerSettingsIntent(): Intent {
        return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun buildAppDetailsSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
