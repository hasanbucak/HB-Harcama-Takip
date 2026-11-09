package com.hasanbucak.harcamatakip.util

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ListenerHealthSnapshot(
    val lastConnectedAt: Long = 0L,
    val lastDisconnectedAt: Long = 0L,
    val lastBankEventAt: Long = 0L,
    val lastBankEventSource: String = ""
)

object ListenerHealthTracker {

    private const val PREFS_NAME = "hb_listener_health"
    private const val KEY_CONNECTED_AT = "last_connected_at"
    private const val KEY_DISCONNECTED_AT = "last_disconnected_at"
    private const val KEY_BANK_EVENT_AT = "last_bank_event_at"
    private const val KEY_BANK_EVENT_SOURCE = "last_bank_event_source"

    private val _state = MutableStateFlow(ListenerHealthSnapshot())
    val state: StateFlow<ListenerHealthSnapshot> = _state.asStateFlow()

    fun load(context: Context): ListenerHealthSnapshot {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val snap = ListenerHealthSnapshot(
            lastConnectedAt = prefs.getLong(KEY_CONNECTED_AT, 0L),
            lastDisconnectedAt = prefs.getLong(KEY_DISCONNECTED_AT, 0L),
            lastBankEventAt = prefs.getLong(KEY_BANK_EVENT_AT, 0L),
            lastBankEventSource = prefs.getString(KEY_BANK_EVENT_SOURCE, "").orEmpty()
        )
        _state.value = snap
        return snap
    }

    fun recordConnected(context: Context, now: Long = System.currentTimeMillis()) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_CONNECTED_AT, now).apply()
        load(context)
    }

    fun recordDisconnected(context: Context, now: Long = System.currentTimeMillis()) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(KEY_DISCONNECTED_AT, now).apply()
        load(context)
    }

    fun recordBankEvent(context: Context, source: String, now: Long = System.currentTimeMillis()) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong(KEY_BANK_EVENT_AT, now)
            .putString(KEY_BANK_EVENT_SOURCE, source)
            .apply()
        load(context)
    }
}
