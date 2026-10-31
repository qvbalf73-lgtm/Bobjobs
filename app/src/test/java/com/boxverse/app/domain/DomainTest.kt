package com.boxverse.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

class BlindBoxEngineTest {
    private val pool = listOf(
        PoolFigure(1, Rarity.R), PoolFigure(2, Rarity.R), PoolFigure(3, Rarity.R), PoolFigure(4, Rarity.R),
        PoolFigure(5, Rarity.SR), PoolFigure(6, Rarity.SR),
        PoolFigure(7, Rarity.SSR),
        PoolFigure(8, Rarity.UR),
    )

    @Test
    fun defaultRatesAreValidAndSumTo100() {
        assertEquals(RateConfig.TOTAL, RateConfig.DEFAULT.total)
        assertTrue(BlindBoxEngine.validate(RateConfig.DEFAULT, pool).isEmpty())
    }

    @Test
    fun wrongTotalIsRejected() {
        val errors = BlindBoxEngine.validate(RateConfig(8000, 1000, 100, 50), pool)
        assertTrue(errors.any { it.contains("100%") })
    }

    @Test
    fun tierWithoutFiguresMustBeZero() {
        val noUr = pool.filter { it.rarity != Rarity.UR }
        assertFalse(BlindBoxEngine.validate(RateConfig.DEFAULT, noUr).isEmpty())
        assertTrue(BlindBoxEngine.validate(RateConfig(8000, 1700, 300, 0), noUr).isEmpty())
    }

    @Test
    fun figureProbabilitiesSumToOne() {
        val sum = pool.sumOf { BlindBoxEngine.figureProbability(it, RateConfig.DEFAULT, pool) }
        assertEquals(1.0, sum, 1e-9)
        assertEquals(0.2, BlindBoxEngine.figureProbability(pool[0], RateConfig.DEFAULT, pool), 1e-9)
        assertEquals(0.005, BlindBoxEngine.figureProbability(pool[7], RateConfig.DEFAULT, pool), 1e-9)
    }

    @Test
    fun drawDistributionMatchesConfiguredRates() {
        val random = Random(42)
        val n = 200_000
        val results = BlindBoxEngine.drawMany(n, pool, RateConfig.DEFAULT, random)
        val rRate = results.count { it.rarity == Rarity.R }.toDouble() / n
        val ssrRate = results.count { it.rarity == Rarity.SSR }.toDouble() / n
        assertEquals(0.80, rRate, 0.01)
        assertEquals(0.025, ssrRate, 0.003)
    }

    @Test
    fun percentParsingAndFormatting() {
        assertEquals(250, RateConfig.parsePercent("2.5"))
        assertEquals("2.5%", RateConfig.percentText(250))
        assertEquals("80%", RateConfig.percentText(8000))
        assertEquals("0.05%", RateConfig.percentText(5))
        assertNull(RateConfig.parsePercent("abc"))
    }
}

class StatsCalculatorTest {
    private fun h(
        id: Long, figure: Long, series: Long, qty: Int, price: Long,
        extra: Long = 0, est: Long? = null, status: ItemStatus = ItemStatus.OWNED,
        currency: String = "CNY", rarity: Rarity = Rarity.R, brand: String = "A", day: Long = 0,
    ) = HoldingRecord(id, figure, "F$figure", series, "S$series", brand, rarity, qty, price, extra, currency, est, status, day)

    @Test
    fun ownedQuantityIgnoresSoldAndPreorder() {
        val holdings = listOf(
            h(1, 10, 1, 2, 5900), h(2, 10, 1, 1, 5900),
            h(3, 11, 1, 1, 5900, status = ItemStatus.PREORDER),
            h(4, 12, 1, 0, 5900, status = ItemStatus.SOLD),
        )
        val owned = StatsCalculator.ownedQuantityByFigure(holdings)
        assertEquals(3, owned[10])
        assertNull(owned[11])
        assertNull(owned[12])
    }

    @Test
    fun seriesProgressAndCompletion() {
        val figures = listOf(
            FigureRef(1, 1, Rarity.R), FigureRef(2, 1, Rarity.R), FigureRef(3, 1, Rarity.SR), FigureRef(4, 1, Rarity.SSR),
        )
        val p = StatsCalculator.seriesProgress(figures, setOf(1, 2, 3))[1]!!
        assertEquals(3, p.owned)
        assertEquals(4, p.total)
        assertEquals(75, p.percent)
        assertTrue(p.isComplete)
        assertFalse(p.isFullComplete)
    }

    @Test
    fun costValueAndProfit() {
        val conv = CurrencyConverter.identity("CNY")
        val holdings = listOf(
            h(1, 1, 1, 2, 6900, extra = 1000, est = 9000),
            h(2, 2, 1, 1, 5900),
        )
        assertEquals(6900 * 2 + 1000 + 5900L, StatsCalculator.totalCost(holdings, conv).total)
        // 估值：有估价 9000×2，无估价按成本 5900
        assertEquals(9000 * 2 + 5900L, StatsCalculator.estimatedValue(holdings, conv).total)
        assertEquals(9000 * 2 + 5900L - (6900 * 2 + 1000 + 5900L), StatsCalculator.unrealizedProfit(holdings, conv).total)
        assertEquals(1, StatsCalculator.itemsWithoutEstimate(holdings))
    }

    @Test
    fun multiCurrencyIsNotConvertedWithoutRate() {
        val holdings = listOf(h(1, 1, 1, 1, 1000, currency = "CNY"), h(2, 2, 1, 1, 2000, currency = "SGD"))
        val noRate = StatsCalculator.totalCost(holdings, CurrencyConverter("CNY", emptyMap()))
        assertEquals(1000L, noRate.total)
        assertEquals(2000L, noRate.unconverted["SGD"])
        val withRate = StatsCalculator.totalCost(holdings, CurrencyConverter("CNY", mapOf("SGD" to 5.0)))
        assertEquals(11000L, withRate.total)
        assertFalse(withRate.hasUnconverted)
    }

    @Test
    fun sellComputesBasisAndProfit() {
        val r = StatsCalculator.sell(quantity = 4, unitPrice = 6000, extraCost = 800, sellQuantity = 1, sellUnitPrice = 9000, fee = 300)
        assertEquals(6200L, r.costBasis)
        assertEquals(9000 - 300 - 6200L, r.profit)
        assertEquals(3, r.remainingQuantity)
        assertEquals(600L, r.remainingExtraCost)
        val tx = TxRecord(TxType.SELL, 1, 9000, 300, r.costBasis, "CNY", 0)
        assertEquals(2500L, StatsCalculator.realizedProfit(listOf(tx), CurrencyConverter.identity()).total)
    }

    @Test
    fun monthlySpendingBuckets() {
        val today = LocalDate.of(2026, 10, 15)
        val txs = listOf(
            TxRecord(TxType.BUY, 2, 5000, 500, 0, "CNY", LocalDate.of(2026, 10, 1).toEpochDay()),
            TxRecord(TxType.BUY, 1, 3000, 0, 0, "CNY", LocalDate.of(2026, 8, 20).toEpochDay()),
            TxRecord(TxType.SELL, 1, 9999, 0, 0, "CNY", LocalDate.of(2026, 10, 2).toEpochDay()),
        )
        val months = StatsCalculator.monthlySpending(txs, CurrencyConverter.identity(), today, 3)
        assertEquals(listOf(3000L, 0L, 10500L), months.map { it.amount })
    }

    @Test
    fun checkInStreak() {
        assertEquals(3, StatsCalculator.checkInStreak(setOf(10, 9, 8, 6), 10))
        assertEquals(3, StatsCalculator.checkInStreak(setOf(9, 8, 7), 10))
        assertEquals(0, StatsCalculator.checkInStreak(setOf(5), 10))
    }
}

class ProgressionTest {
    @Test
    fun levelCurve() {
        assertEquals(1, LevelSystem.levelFor(0))
        assertEquals(2, LevelSystem.levelFor(50))
        assertEquals(5, LevelSystem.levelFor(LevelSystem.xpForLevel(5)))
        assertEquals(4, LevelSystem.levelFor(LevelSystem.xpForLevel(5) - 1))
        assertEquals(50, LevelSystem.levelFor(Long.MAX_VALUE / 2))
        assertEquals("盒宇宙传奇", LevelSystem.title(50))
        assertEquals("隐藏款猎人", LevelSystem.title(20))
    }

    @Test
    fun achievementsUnlockOnce() {
        val stats = CollectorStats(totalQuantity = 1, distinctFigures = 1, virtualOpenings = 1)
        val reached = Achievements.newlyReached(stats, emptySet()).map { it.id }
        assertTrue("first_item" in reached && "open_1" in reached)
        assertTrue(Achievements.newlyReached(stats, reached.toSet()).isEmpty())
    }

    @Test
    fun moneyFormatting() {
        assertEquals("¥1,234.50", Money.format(123450, "CNY"))
        assertEquals("+S$12.00", Money.format(1200, "SGD", showSign = true))
        assertEquals(1250L, Money.parse("12.5"))
        assertEquals("12.5", Money.toInput(1250))
        assertNull(Money.parse("-3"))
    }
}
