package com.boxverse.app.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.boxverse.app.data.db.HoldingRow
import com.boxverse.app.data.prefs.AppSettings
import com.boxverse.app.data.repo.BoxRepository
import com.boxverse.app.data.repo.toRecord
import com.boxverse.app.domain.Currency
import com.boxverse.app.domain.HoldingRecord
import com.boxverse.app.domain.ItemStatus
import com.boxverse.app.domain.Money
import com.boxverse.app.domain.MoneySummary
import com.boxverse.app.domain.MonthAmount
import com.boxverse.app.domain.NamedAmount
import com.boxverse.app.domain.NamedCount
import com.boxverse.app.domain.Rarity
import com.boxverse.app.domain.StatsCalculator
import com.boxverse.app.ui.components.BarList
import com.boxverse.app.ui.components.BoxCard
import com.boxverse.app.ui.components.ChartSlice
import com.boxverse.app.ui.components.ColumnChart
import com.boxverse.app.ui.components.DonutChart
import com.boxverse.app.ui.components.EmptyState
import com.boxverse.app.ui.components.FigureArt
import com.boxverse.app.ui.components.InfoNote
import com.boxverse.app.ui.components.KeyValueRow
import com.boxverse.app.ui.components.LoadingState
import com.boxverse.app.ui.components.RarityFrame
import com.boxverse.app.ui.components.SubScreen
import com.boxverse.app.ui.components.TonalAction
import com.boxverse.app.ui.navigation.AppNav
import com.boxverse.app.ui.navigation.Routes
import com.boxverse.app.ui.navigation.boxViewModel
import com.boxverse.app.ui.theme.LocalBoxColors
import com.boxverse.app.util.Dates
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class StatsData(
    val settings: AppSettings,
    val holdings: List<HoldingRow>,
    val cost: MoneySummary,
    val value: MoneySummary,
    val unrealized: MoneySummary,
    val realized: MoneySummary,
    val noEstimate: Int,
    val brands: List<NamedAmount>,
    val series: List<NamedCount>,
    val monthly: List<MonthAmount>,
    val top: List<HoldingRecord>,
    val rawByCurrency: Map<String, Long>,
    val holdingQty: Int,
)

class StatsViewModel(repo: BoxRepository) : ViewModel() {
    val data: StateFlow<StatsData?> = combine(repo.holdingRows, repo.transactions, repo.settings.settings) { holdings, txs, settings ->
        val conv = settings.converter
        val records = holdings.map { it.toRecord() }
        val txRecords = txs.map { it.toRecord() }
        StatsData(
            settings = settings,
            holdings = holdings,
            cost = StatsCalculator.totalCost(records, conv),
            value = StatsCalculator.estimatedValue(records, conv),
            unrealized = StatsCalculator.unrealizedProfit(records, conv),
            realized = StatsCalculator.realizedProfit(txRecords, conv),
            noEstimate = StatsCalculator.itemsWithoutEstimate(records),
            brands = StatsCalculator.brandDistribution(records, conv),
            series = StatsCalculator.seriesQuantityDistribution(records),
            monthly = StatsCalculator.monthlySpending(txRecords, conv, LocalDate.now()),
            top = StatsCalculator.topExpensive(records, conv),
            rawByCurrency = records.filter { it.status != ItemStatus.SOLD }.groupBy { it.currency }.mapValues { (_, l) -> l.sumOf { it.totalCost } },
            holdingQty = records.filter { it.status.isHolding }.sumOf { it.quantity },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

@Composable
fun StatsScreen(nav: AppNav) {
    val vm = boxViewModel { StatsViewModel(it.repo) }
    val data by vm.data.collectAsStateWithLifecycle()
    SubScreen(title = "收藏价值与统计", onBack = nav::back) { padding ->
        val d = data
        when {
            d == null -> LoadingState(Modifier.padding(padding))
            d.holdings.isEmpty() -> EmptyState(Icons.Rounded.BarChart, "还没有可统计的数据", "添加实物收藏后，这里会展示成本、估值、盈亏与分布图表。", Modifier.padding(padding), "添加收藏") { nav.itemEdit() }
            else -> StatsContent(d, nav, Modifier.padding(padding))
        }
    }
}

@Composable
private fun StatsContent(d: StatsData, nav: AppNav, modifier: Modifier) {
    val base = d.settings.baseCurrency
    val extra = LocalBoxColors.current
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            BoxCard(Modifier.fillMaxWidth(), brush = Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer))) {
                Text("统计基准币：${Currency.of(base).label}（${base}）", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(10.dp))
                Row {
                    BigFigure("收藏总成本", Money.format(d.cost.total, base), Modifier.weight(1f))
                    BigFigure("估算价值", Money.format(d.value.total, base), Modifier.weight(1f), extra.gold)
                }
                Spacer(Modifier.height(10.dp))
                Row {
                    BigFigure("未实现估算盈亏", Money.format(d.unrealized.total, base, showSign = true), Modifier.weight(1f), if (d.unrealized.total >= 0) extra.success else extra.danger)
                    BigFigure("已实现出售盈亏", Money.format(d.realized.total, base, showSign = true), Modifier.weight(1f), if (d.realized.total >= 0) extra.success else extra.danger)
                }
                Spacer(Modifier.height(10.dp))
                Text("当前持有 ${d.holdingQty} 件实物（虚拟开盒藏品不计入）", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        item {
            val notes = buildList {
                add("估算价值来自你手动填写的估价；未填写估价的 ${d.noEstimate} 条记录按购入成本计算，不代表实时市场价格。")
                if (d.cost.hasUnconverted || d.value.hasUnconverted) {
                    add("以下金额因未设置汇率而未计入上方合计：${d.cost.unconvertedText()}。可在「设置 › 币种与汇率」中手动填写汇率。")
                }
            }
            InfoNote(notes.joinToString("\n"), icon = Icons.Rounded.CurrencyExchange)
        }
        if (d.rawByCurrency.size > 1 || d.rawByCurrency.keys.any { it != base }) {
            item {
                BoxCard(Modifier.fillMaxWidth()) {
                    Text("按币种原始成本（未换算）", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    d.rawByCurrency.forEach { (cur, amt) ->
                        val rate = d.settings.converter.rateOf(cur)
                        KeyValueRow(
                            "${Currency.of(cur).label}（$cur）" + if (cur != base) (rate?.let { " · 汇率 1:$it" } ?: " · 未设汇率") else " · 基准币",
                            Money.format(amt, cur),
                        )
                    }
                    TonalAction("设置汇率", Icons.Rounded.CurrencyExchange, Modifier.fillMaxWidth()) { nav.go(Routes.SETTINGS) }
                }
            }
        }
        item {
            BoxCard(Modifier.fillMaxWidth()) {
                Text("每月购买支出（近 12 个月）", style = MaterialTheme.typography.titleMedium)
                Text("合计 ${Money.format(d.monthly.sumOf { it.amount }, base)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                ColumnChart(d.monthly.map { it.amount / 100f }, d.monthly.map { it.label }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                val thisMonth = d.monthly.lastOrNull()
                if (thisMonth != null) Text("本月：${Money.format(thisMonth.amount, base)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (d.brands.isNotEmpty()) {
            item {
                BoxCard(Modifier.fillMaxWidth()) {
                    Text("各品牌收藏金额分布", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    DonutChart(
                        d.brands.map { ChartSlice(it.name, it.amount.toFloat(), Money.format(it.amount, base)) },
                        Modifier.fillMaxWidth(),
                        centerTitle = "${d.brands.size} 个品牌",
                        centerValue = Money.formatCompact(d.brands.sumOf { it.amount }, base),
                    )
                    Spacer(Modifier.height(8.dp))
                    d.brands.forEach { KeyValueRow(it.name, Money.format(it.amount, base)) }
                }
            }
        }
        if (d.series.isNotEmpty()) {
            item {
                BoxCard(Modifier.fillMaxWidth()) {
                    Text("各系列持有数量", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    BarList(d.series.take(10).map { it.name to it.count.toFloat() }, { "${it.toInt()} 件" }, Modifier.fillMaxWidth())
                }
            }
        }
        if (d.top.isNotEmpty()) {
            item { Text("最昂贵的十款收藏品（按单件成本）", style = MaterialTheme.typography.titleMedium) }
            itemsIndexed(d.top, key = { _, r -> r.itemId }) { i, r ->
                val row = d.holdings.firstOrNull { it.item.id == r.itemId }
                BoxCard(Modifier.fillMaxWidth(), onClick = { nav.figure(r.figureId) }, contentPadding = PaddingValues(10.dp), elevation = 2.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = if (i < 3) extra.gold else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(32.dp))
                        if (row != null) {
                            RarityFrame(r.rarity, Modifier.size(48.dp), shape = RoundedCornerShape(12.dp)) {
                                FigureArt(row.artSeed, row.colorA, row.colorB, r.rarity, Modifier.fillMaxSize().padding(2.dp), imagePath = row.figureImage)
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.figureName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${r.brandName} · ${r.seriesName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(Money.format(r.unitCost, r.currency), style = MaterialTheme.typography.titleSmall)
                            Text("持有 ${r.quantity} · ${Dates.format(r.acquiredEpochDay)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BigFigure(label: String, value: String, modifier: Modifier, color: Color = MaterialTheme.colorScheme.onPrimaryContainer) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
        Text(value, style = MaterialTheme.typography.titleLarge, color = color, maxLines = 1)
    }
}
