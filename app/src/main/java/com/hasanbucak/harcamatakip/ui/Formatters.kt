package com.hasanbucak.harcamatakip.ui

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Formatters {

    private val trSymbols = DecimalFormatSymbols(Locale("tr", "TR")).apply {
        groupingSeparator = '.'
        decimalSeparator = ','
    }

    fun formatMoney(amount: Double, currency: String = "TL"): String {
        val df = DecimalFormat("#,##0.00", trSymbols)
        return "${df.format(amount)} $currency"
    }

    fun formatDayHeader(timestamp: Long): String {
        val fmt = SimpleDateFormat("dd MMMM yyyy, EEEE", Locale("tr", "TR"))
        return fmt.format(Date(timestamp))
    }

    fun formatTime(timestamp: Long): String {
        val fmt = SimpleDateFormat("HH:mm", Locale("tr", "TR"))
        return fmt.format(Date(timestamp))
    }

    fun formatDateTime(timestamp: Long): String {
        val fmt = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale("tr", "TR"))
        return fmt.format(Date(timestamp))
    }
}
