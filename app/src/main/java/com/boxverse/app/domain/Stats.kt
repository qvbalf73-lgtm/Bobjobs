package com.boxverse.app.domain

import java.time.LocalDate
import java.time.YearMonth

/** 一条实物收藏记录（统计用的纯数据，不含虚拟藏品） */
data class HoldingRecord(
    val itemId: Long,
    val figureId: Long,
    val figureName: String,
    val seriesId: Long,
    val seriesName: String,
    val brandName: String,
    val rarity: Rarity,
    val quantity: Int,
    val unitPrice: Long,
    val extraCost: Long,
    val currency: String,
    val estimatedUnitValue: Long?,
    val status: ItemStatus,
    val acquiredEpochDay: Long,
    val isDemo: Boolean = false,
) {
    /** 当前这条记录的总成本 = 单价 × 数量 + 其他成本 */
    val totalCost: Long get() = unitPrice * quantity + extraCost

    /** 单件成本（含分摊的其他成本） */
    val unitCost: Long get() = if (quantity > 0) unitPrice + extraCost / quantity else unitPrice

    /** 估算总价值：有手动估价用估价，否则按成本计（不虚构市场价） */
    val estimatedTotal: Long get() = (estimatedUnitValue ?: unitCost) * quantity
}

data class FigureRef(val id: Long, val seriesId: Long, val rarity: Rarity)

data class TxRecord(
    val type: TxType,
    val quantity: Int,
    val unitPrice: Long,
    val fee: Long,
    val costBasis: Long,
    val currency: String,
    val epochDay: Long,
)

data class SeriesProgress(
    val seriesId: Long,
    val owned: Int,
    val total: Int,
    val regularOwned: Int,
    val regularTotal: Int,
    val hiddenOwned: Int,
    val hiddenTotal: Int,
) {
    val percent: Int get() = if (total == 0) 0 else (owned * 100 / total)
    val fraction: Float get() = if (total == 0) 0f else owned.toFloat() / total

    /** 集齐全部常规款即视为完成系列 */
    val isComplete: Boolean get() = regularTotal > 0 && regularOwned == regularTotal

    /** 含隐藏款全收集 */
    val isFullComplete: Boolean get() = total > 0 && owned == total
}

data class NamedAmount(val name: String, val amount: Long)
data class NamedCount(val name: String, val count: Int)
data class MonthAmount(val month: YearMonth, val amount: Long) {
    val label: String get() = "${month.monthValue}月"
}

data class SellResult(
    val costBasis: Long,
    val proceeds: Long,
    val profit: Long,
    val remainingQuantity: Int,
    val remainingExtraCost: Long,
)

object StatsCalculator {

    /** 每个款式当前持有的实物数量（仅统计"已入手/运输中"） */
    fun ownedQuantityByFigure(holdings: List<HoldingRecord>): Map<Long, Int> =
        holdings.filter { it.status.isHolding && it.quantity > 0 }
            .groupBy { it.figureId }
            .mapValues { (_, list) -> list.sumOf { it.quantity } }

    fun seriesProgress(figures: List<FigureRef>, ownedFigureIds: Set<Long>): Map<Long, SeriesProgress> =
        figures.groupBy { it.seriesId }.mapValues { (seriesId, list) ->
            val regular = list.filter { !it.rarity.isHidden }
            val hidden = list.filter { it.rarity.isHidden }
            SeriesProgress(
                seriesId = seriesId,
                owned = list.count { it.id in ownedFigureIds },
                total = list.size,
                regularOwned = regular.count { it.id in ownedFigureIds },
                regularTotal = regular.size,
                hiddenOwned = hidden.count { it.id in ownedFigureIds },
                hiddenTotal = hidden.size,
            )
        }

    /** 收藏总成本：除"已售出"外所有记录 */
    fun totalCost(holdings: List<HoldingRecord>, converter: CurrencyConverter): MoneySummary =
        MoneySummary.of(holdings.filter { it.status != ItemStatus.SOLD }.map { it.currency to it.totalCost }, converter)

    /** 当前持有部分的成本（用于计算未实现盈亏） */
    fun holdingCost(holdings: List<HoldingRecord>, converter: CurrencyConverter): MoneySummary =
        MoneySummary.of(holdings.filter { it.status.isHolding }.map { it.currency to it.totalCost }, converter)

    /** 估算价值：只统计当前持有的实物 */
    fun estimatedValue(holdings: List<HoldingRecord>, converter: CurrencyConverter): MoneySummary =
        MoneySummary.of(holdings.filter { it.status.isHolding }.map { it.currency to it.estimatedTotal }, converter)

    fun unrealizedProfit(holdings: List<HoldingRecord>, converter: CurrencyConverter): MoneySummary =
        estimatedValue(holdings, converter) - holdingCost(holdings, converter)

    /** 已实现盈亏 = 售出所得 - 手续费 - 售出部分的成本 */
    fun realizedProfit(txs: List<TxRecord>, converter: CurrencyConverter): MoneySummary =
        MoneySummary.of(
            txs.filter { it.type == TxType.SELL }.map { it.currency to (it.unitPrice * it.quantity - it.fee - it.costBasis) },
            converter,
        )

    /** 持有但未填写估价的记录数 */
    fun itemsWithoutEstimate(holdings: List<HoldingRecord>): Int =
        holdings.count { it.status.isHolding && it.estimatedUnitValue == null }

    fun brandDistribution(holdings: List<HoldingRecord>, converter: CurrencyConverter): List<NamedAmount> =
        holdings.filter { it.status != ItemStatus.SOLD }
            .groupBy { it.brandName }
            .mapNotNull { (brand, list) ->
                val amount = list.sumOf { converter.convert(it.totalCost, it.currency) ?: 0L }
                if (amount > 0) NamedAmount(brand, amount) else null
            }
            .sortedByDescending { it.amount }

    fun seriesQuantityDistribution(holdings: List<HoldingRecord>): List<NamedCount> =
        holdings.filter { it.status.isHolding }
            .groupBy { it.seriesName }
            .map { (name, list) -> NamedCount(name, list.sumOf { it.quantity }) }
            .filter { it.count > 0 }
            .sortedByDescending { it.count }

    /** 最近 [months] 个月每月购买支出（来自购入交易记录） */
    fun monthlySpending(
        txs: List<TxRecord>,
        converter: CurrencyConverter,
        today: LocalDate,
        months: Int = 12,
    ): List<MonthAmount> {
        val current = YearMonth.from(today)
        val range = (months - 1 downTo 0).map { current.minusMonths(it.toLong()) }
        val byMonth = txs.filter { it.type == TxType.BUY }.groupBy { YearMonth.from(LocalDate.ofEpochDay(it.epochDay)) }
        return range.map { ym ->
            val amount = byMonth[ym].orEmpty().sumOf { converter.convert(it.unitPrice * it.quantity + it.fee, it.currency) ?: 0L }
            MonthAmount(ym, amount)
        }
    }

    /** 最昂贵的收藏品（按单件成本，换算为基准币后排序；无法换算的排在最后） */
    fun topExpensive(holdings: List<HoldingRecord>, converter: CurrencyConverter, limit: Int = 10): List<HoldingRecord> =
        holdings.filter { it.status.isHolding && it.quantity > 0 }
            .sortedByDescending { converter.convert(it.unitCost, it.currency) ?: -1L }
            .take(limit)

    /** 售出计算：按单件成本（含分摊其他成本）计算售出部分成本 */
    fun sell(
        quantity: Int,
        unitPrice: Long,
        extraCost: Long,
        sellQuantity: Int,
        sellUnitPrice: Long,
        fee: Long,
    ): SellResult {
        require(sellQuantity in 1..quantity) { "sell quantity out of range" }
        val extraShare = extraCost * sellQuantity / quantity
        val basis = unitPrice * sellQuantity + extraShare
        val proceeds = sellUnitPrice * sellQuantity
        return SellResult(
            costBasis = basis,
            proceeds = proceeds,
            profit = proceeds - fee - basis,
            remainingQuantity = quantity - sellQuantity,
            remainingExtraCost = extraCost - extraShare,
        )
    }

    /** 连续签到天数（今天未签到时从昨天开始往前数） */
    fun checkInStreak(days: Set<Long>, today: Long): Int {
        var d = if (today in days) today else today - 1
        var streak = 0
        while (d in days) {
            streak++
            d--
        }
        return streak
    }
}
