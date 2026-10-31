package com.boxverse.app.domain

import kotlin.math.abs
import kotlin.math.roundToLong

/** 支持记录的币种。金额统一以"分"（1/100 单位）的 Long 存储，避免浮点误差。 */
enum class Currency(val code: String, val label: String, val symbol: String) {
    CNY("CNY", "人民币", "¥"),
    SGD("SGD", "新加坡元", "S$"),
    MYR("MYR", "马来西亚令吉", "RM"),
    USD("USD", "美元", "US$"),
    HKD("HKD", "港币", "HK$");

    companion object {
        fun of(code: String?): Currency = entries.firstOrNull { it.code == code } ?: CNY
    }
}

object Money {
    /** 把用户输入的"12.5"解析为 1250 分；无法解析返回 null */
    fun parse(text: String): Long? {
        val t = text.trim().replace(",", "")
        if (t.isEmpty()) return null
        val d = t.toDoubleOrNull() ?: return null
        if (d < 0 || d.isNaN() || d.isInfinite()) return null
        return (d * 100).roundToLong()
    }

    /** 分 -> 输入框文本，如 1250 -> "12.5" */
    fun toInput(cents: Long?): String {
        if (cents == null) return ""
        val whole = cents / 100
        val frac = abs(cents % 100)
        return when {
            frac == 0L -> whole.toString()
            frac % 10 == 0L -> "$whole.${frac / 10}"
            else -> "$whole.${frac.toString().padStart(2, '0')}"
        }
    }

    /** 格式化金额：¥1,234.50 */
    fun format(cents: Long, currencyCode: String, showSign: Boolean = false): String {
        val c = Currency.of(currencyCode)
        val sign = when {
            cents < 0 -> "-"
            showSign && cents > 0 -> "+"
            else -> ""
        }
        val v = abs(cents)
        val whole = v / 100
        val frac = v % 100
        val wholeStr = whole.toString().reversed().chunked(3).joinToString(",").reversed()
        return "$sign${c.symbol}$wholeStr.${frac.toString().padStart(2, '0')}"
    }

    /** 紧凑格式：整数金额不显示小数，过万显示"万" */
    fun formatCompact(cents: Long, currencyCode: String): String {
        val c = Currency.of(currencyCode)
        val sign = if (cents < 0) "-" else ""
        val v = abs(cents)
        return if (v >= 1_000_000_00L) {
            val wan = v / 100.0 / 10000.0
            "$sign${c.symbol}${"%.1f".format(wan)}万"
        } else if (v % 100 == 0L) {
            val whole = (v / 100).toString().reversed().chunked(3).joinToString(",").reversed()
            "$sign${c.symbol}$whole"
        } else format(cents, currencyCode)
    }
}

/**
 * 币种换算器。App 不联网获取汇率，[rates] 完全来自用户手动填写：
 * rates["SGD"] = 5.3 表示 1 SGD = 5.3 个基准币。
 * 未填写汇率的币种不会被换算，而是单独列出，避免伪造汇率。
 */
class CurrencyConverter(val baseCurrency: String, private val rates: Map<String, Double>) {
    fun canConvert(from: String): Boolean = from == baseCurrency || (rates[from] ?: 0.0) > 0.0

    fun convert(amount: Long, from: String): Long? {
        if (from == baseCurrency) return amount
        val rate = rates[from] ?: return null
        if (rate <= 0.0) return null
        return (amount * rate).roundToLong()
    }

    fun rateOf(code: String): Double? = if (code == baseCurrency) 1.0 else rates[code]?.takeIf { it > 0 }

    companion object {
        fun identity(base: String = "CNY") = CurrencyConverter(base, emptyMap())
    }
}

/** 换算后的金额汇总：已换算为基准币的总额 + 无汇率无法换算的部分（按币种列出） */
data class MoneySummary(
    val baseCurrency: String,
    val total: Long,
    val unconverted: Map<String, Long> = emptyMap(),
) {
    val hasUnconverted: Boolean get() = unconverted.values.any { it != 0L }

    operator fun minus(other: MoneySummary): MoneySummary {
        val keys = unconverted.keys + other.unconverted.keys
        val un = keys.associateWith { (unconverted[it] ?: 0L) - (other.unconverted[it] ?: 0L) }
            .filterValues { it != 0L }
        return MoneySummary(baseCurrency, total - other.total, un)
    }

    fun unconvertedText(): String =
        unconverted.filterValues { it != 0L }.entries.joinToString("、") { Money.format(it.value, it.key) }

    companion object {
        fun of(amounts: List<Pair<String, Long>>, converter: CurrencyConverter): MoneySummary {
            var total = 0L
            val un = mutableMapOf<String, Long>()
            for ((cur, amt) in amounts) {
                val c = converter.convert(amt, cur)
                if (c != null) total += c else un[cur] = (un[cur] ?: 0L) + amt
            }
            return MoneySummary(converter.baseCurrency, total, un.filterValues { it != 0L })
        }
    }
}
