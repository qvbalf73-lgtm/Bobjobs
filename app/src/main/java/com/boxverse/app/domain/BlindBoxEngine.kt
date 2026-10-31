package com.boxverse.app.domain

import kotlin.random.Random

/** 参与抽取的款式 */
data class PoolFigure(val id: Long, val rarity: Rarity)

/**
 * 系列的稀有度概率配置，单位为"万分之一"（basis point），四档之和必须等于 10000（=100%）。
 * 同一档位内的各个款式等概率。
 */
data class RateConfig(val r: Int, val sr: Int, val ssr: Int, val ur: Int) {
    val total: Int get() = r + sr + ssr + ur

    fun of(rarity: Rarity): Int = when (rarity) {
        Rarity.R -> r
        Rarity.SR -> sr
        Rarity.SSR -> ssr
        Rarity.UR -> ur
    }

    fun with(rarity: Rarity, value: Int): RateConfig = when (rarity) {
        Rarity.R -> copy(r = value)
        Rarity.SR -> copy(sr = value)
        Rarity.SSR -> copy(ssr = value)
        Rarity.UR -> copy(ur = value)
    }

    companion object {
        const val TOTAL = 10000

        /** 本 App 虚构系列的默认设定概率（非任何真实品牌的官方概率） */
        val DEFAULT = RateConfig(r = 8000, sr = 1700, ssr = 250, ur = 50)

        fun percentText(bp: Int): String {
            val whole = bp / 100
            val frac = bp % 100
            return if (frac == 0) "$whole%" else "$whole.${frac.toString().padStart(2, '0').trimEnd('0')}%"
        }

        /** "2.5" -> 250；非法返回 null */
        fun parsePercent(text: String): Int? {
            val d = text.trim().toDoubleOrNull() ?: return null
            if (d < 0 || d > 100) return null
            return Math.round(d * 100).toInt()
        }
    }
}

object BlindBoxEngine {

    /** 校验概率配置，返回错误列表（为空表示可用） */
    fun validate(rates: RateConfig, pool: List<PoolFigure>): List<String> {
        val errors = mutableListOf<String>()
        if (pool.isEmpty()) errors += "该系列还没有任何款式，无法开盒"
        if (Rarity.entries.any { rates.of(it) < 0 }) errors += "概率不能为负数"
        if (rates.total != RateConfig.TOTAL) {
            errors += "四档概率之和必须为 100%（当前为 ${RateConfig.percentText(rates.total)}）"
        }
        for (rarity in Rarity.entries) {
            val count = pool.count { it.rarity == rarity }
            if (rates.of(rarity) > 0 && count == 0) {
                errors += "${rarity.fullLabel} 档没有款式，其概率必须设为 0%"
            }
        }
        return errors
    }

    /**
     * 实际生效的档位概率（0~1）。配置合法时与设定值完全一致；
     * 若配置不合法（例如某档没有款式），则只在有款式的档位间按比例归一化，保证总和为 1。
     */
    fun effectiveTierRates(rates: RateConfig, pool: List<PoolFigure>): Map<Rarity, Double> {
        val tiers = Rarity.entries.filter { r -> pool.any { it.rarity == r } }
        if (tiers.isEmpty()) return emptyMap()
        val sum = tiers.sumOf { rates.of(it).coerceAtLeast(0) }
        return if (sum <= 0) {
            // 所有档位概率都为 0 时，退化为全部款式等概率
            tiers.associateWith { t -> pool.count { it.rarity == t }.toDouble() / pool.size }
        } else {
            tiers.associateWith { rates.of(it).coerceAtLeast(0).toDouble() / sum }
        }
    }

    /** 单个款式被抽中的概率（0~1） */
    fun figureProbability(figure: PoolFigure, rates: RateConfig, pool: List<PoolFigure>): Double {
        val tier = effectiveTierRates(rates, pool)[figure.rarity] ?: return 0.0
        val count = pool.count { it.rarity == figure.rarity }
        return if (count == 0) 0.0 else tier / count
    }

    fun draw(pool: List<PoolFigure>, rates: RateConfig, random: Random = Random.Default): PoolFigure {
        require(pool.isNotEmpty()) { "pool is empty" }
        val tiers = effectiveTierRates(rates, pool)
        var roll = random.nextDouble()
        var chosen: Rarity = tiers.keys.last()
        for ((rarity, p) in tiers.entries.sortedBy { it.key.order }) {
            if (roll < p) {
                chosen = rarity
                break
            }
            roll -= p
        }
        val candidates = pool.filter { it.rarity == chosen }
        return candidates[random.nextInt(candidates.size)]
    }

    fun drawMany(count: Int, pool: List<PoolFigure>, rates: RateConfig, random: Random = Random.Default): List<PoolFigure> =
        List(count) { draw(pool, rates, random) }
}
