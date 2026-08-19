package com.codex.moneymate

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class TransactionRange(
    @JvmField val start: String?,
    @JvmField val end: String?,
    @JvmField val label: String,
)

object TransactionScopes {
    private val storageFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    @JvmStatic
    fun range(scope: String, anchorValue: String?, fallbackValue: String): TransactionRange {
        return range(scope, anchorValue, fallbackValue, "es")
    }

    @JvmStatic
    fun range(scope: String, anchorValue: String?, fallbackValue: String, language: String): TransactionRange {
        if (scope == "todo" || scope == "total") {
            val label = when (language) {
                "en" -> "All"
                "pt" -> "Tudo"
                "fr" -> "Tout"
                else -> "Todo"
            }
            return TransactionRange(null, null, label)
        }
        val locale = when (language) {
            "en" -> Locale.US
            "pt" -> Locale("pt", "BR")
            "fr" -> Locale.FRANCE
            else -> Locale("es", "PE")
        }
        val shortFormat = SimpleDateFormat("dd MMM", locale)
        val longFormat = SimpleDateFormat("dd MMM yyyy", locale)
        val anchor = parse(anchorValue ?: fallbackValue)
        val start = anchor.clone() as Calendar
        var end = anchor.clone() as Calendar
        val label = when (scope) {
            "anual" -> {
                start.set(Calendar.MONTH, Calendar.JANUARY)
                start.set(Calendar.DAY_OF_MONTH, 1)
                end.set(Calendar.MONTH, Calendar.DECEMBER)
                end.set(Calendar.DAY_OF_MONTH, 31)
                anchor.get(Calendar.YEAR).toString()
            }
            "semanal" -> {
                val backToMonday = if (start.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                    6
                } else {
                    start.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY
                }
                start.add(Calendar.DATE, -backToMonday)
                end = start.clone() as Calendar
                end.add(Calendar.DATE, 6)
                "${shortFormat.format(start.time)} - ${shortFormat.format(end.time)}"
            }
            "diario" -> longFormat.format(anchor.time)
            else -> {
                start.set(Calendar.DAY_OF_MONTH, 1)
                end.set(Calendar.DAY_OF_MONTH, end.getActualMaximum(Calendar.DAY_OF_MONTH))
                SimpleDateFormat("MMM yyyy", locale).format(anchor.time)
            }
        }
        return TransactionRange(storageFormat.format(start.time), storageFormat.format(end.time), label)
    }

    private fun parse(value: String): Calendar {
        val calendar = Calendar.getInstance()
        runCatching {
            val parts = value.split("-")
            calendar.set(Calendar.YEAR, parts[0].toInt())
            calendar.set(Calendar.MONTH, parts[1].toInt() - 1)
            calendar.set(Calendar.DAY_OF_MONTH, parts.getOrNull(2)?.toInt() ?: 1)
        }
        return calendar
    }
}
