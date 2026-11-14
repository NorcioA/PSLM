package com.example.samplee.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeUtils {

    fun format(millis: Long): String =
        SimpleDateFormat("MMM dd, yyyy  hh:mm a", Locale.getDefault()).format(Date(millis))

    fun formatDate(millis: Long): String =
        SimpleDateFormat("EEE, MMM dd, yyyy", Locale.getDefault()).format(Date(millis))

    fun formatTime(millis: Long): String =
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(millis))

    fun ymd(millis: Long): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(millis))

    /** 1500 minutes -> "1 day 1 hr", 125 minutes -> "2 hr 5 min". */
    fun formatDuration(millis: Long): String {
        val totalMinutes = (millis / 60_000L).coerceAtLeast(0)
        val days = totalMinutes / (60 * 24)
        val hours = (totalMinutes / 60) % 24
        val minutes = totalMinutes % 60
        val parts = mutableListOf<String>()
        if (days > 0) parts.add("$days day" + if (days > 1) "s" else "")
        if (hours > 0) parts.add("$hours hr")
        if (minutes > 0 || parts.isEmpty()) parts.add("$minutes min")
        return parts.joinToString(" ")
    }

    /** Ticking clock text: "02:15:09", or "1d 03:25:10" when more than a day away. */
    fun formatCountdown(millis: Long): String {
        val total = (millis / 1000L).coerceAtLeast(0)
        val d = total / 86_400
        val h = (total % 86_400) / 3_600
        val m = (total % 3_600) / 60
        val s = total % 60
        return if (d > 0) String.format(Locale.US, "%dd %02d:%02d:%02d", d, h, m, s)
        else String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
    }
}
