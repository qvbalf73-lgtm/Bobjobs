package com.boxverse.app.ui.blindbox

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.boxverse.app.data.db.BlindBoxOpening
import com.boxverse.app.data.db.FigureRow
import com.boxverse.app.data.db.SeriesRow
import com.boxverse.app.data.repo.BoxRepository
import com.boxverse.app.domain.BlindBoxEngine
import com.boxverse.app.domain.PoolFigure
import com.boxverse.app.domain.Rarity
import com.boxverse.app.ui.components.BoxCard
import com.boxverse.app.ui.components.ChoiceChip
import com.boxverse.app.ui.components.ConfirmDialog
import com.boxverse.app.ui.components.EmptyState
import com.boxverse.app.ui.components.FigureArt
import com.boxverse.app.ui.components.FigureTile
import com.boxverse.app.ui.components.GradientProgressBar
import com.boxverse.app.ui.components.InfoNote
import com.boxverse.app.ui.components.LoadingState
import com.boxverse.app.ui.components.Pill
import com.boxverse.app.ui.components.RarityBadge
import com.boxverse.app.ui.components.RarityFrame
import com.boxverse.app.ui.components.SubScreen
import com.boxverse.app.ui.navigation.AppNav
import com.boxverse.app.ui.navigation.Routes
import com.boxverse.app.ui.navigation.boxViewModel
import com.boxverse.app.ui.theme.RarityStyle
import com.boxverse.app.util.Dates
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryData(
    val openings: List<BlindBoxOpening>,
    val figures: Map<Long, FigureRow>,
    val series: List<SeriesRow>,
)

class HistoryViewModel(private val repo: BoxRepository) : ViewModel() {
    val data: StateFlow<HistoryData?> = combine(repo.openings, repo.figureRows, repo.seriesRows) { o, f, s ->
        HistoryData(o, f.associateBy { it.figure.id }, s)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun clear() = viewModelScope.launch { repo.clearBlindBoxHistory() }
}

@Composable
fun OpeningHistoryScreen(nav: AppNav) {
    val vm = boxViewModel { HistoryViewModel(it.repo) }
    val data by vm.data.collectAsStateWithLifecycle()
    var seriesFilter by remember { mutableStateOf<Long?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    SubScreen(
        title = "开盒记录",
        onBack = nav::back,
        actions = { IconButton(onClick = { confirmClear = true }) { Icon(Icons.Rounded.DeleteSweep, "清空记录") } },
    ) { padding ->
        val d = data
        if (d == null) {
            LoadingState(Modifier.padding(padding)); return@SubScreen
        }
        if (d.openings.isEmpty()) {
            EmptyState(Icons.Rounded.History, "还没有开盒记录", "去盲盒专区开一个试试吧！每一次开盒都会被记录在这里。", Modifier.padding(padding), "去开盒") { nav.tab(Routes.BLINDBOX) }
            return@SubScreen
        }
        val seriesWithOpenings = d.series.filter { s -> d.openings.any { it.seriesId == s.series.id } }
        val filtered = d.openings.filter { seriesFilter == null || it.seriesId == seriesFilter }
        val batches = filtered.groupBy { it.batchId }.entries.sortedByDescending { it.key }

        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { ChoiceChip("全部系列", seriesFilter == null, { seriesFilter = null }) }
                    items(seriesWithOpenings, key = { it.series.id }) { s ->
                        ChoiceChip(s.series.name, seriesFilter == s.series.id, { seriesFilter = s.series.id })
                    }
                }
            }
            item { StatsCard(filtered, d, seriesFilter) }
            item { Text("开盒历史（${batches.size} 次操作 · ${filtered.size} 盒）", style = MaterialTheme.typography.titleMedium) }
            items(batches, key = { it.key }) { (batchId, list) ->
                val seriesName = d.series.firstOrNull { it.series.id == list.first().seriesId }?.series?.name ?: "已删除系列"
                BoxCard(Modifier.fillMaxWidth(), elevation = 2.dp, contentPadding = PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("$seriesName · ${list.size} 连开", style = MaterialTheme.typography.titleSmall)
                            Text(Dates.formatMillis(batchId), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Pill(if (list.all { it.savedToVirtual }) "已收入虚拟柜" else "未收入")
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(list, key = { it.id }) { o ->
                            val f = d.figures[o.figureId]
                            Column(Modifier.width(56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                RarityFrame(o.rarityEnum, Modifier.size(52.dp), shape = RoundedCornerShape(12.dp)) {
                                    if (f != null) FigureArt(f.figure.artSeed, f.figure.colorA, f.figure.colorB, o.rarityEnum, Modifier.fillMaxSize().padding(2.dp), imagePath = f.figure.imagePath)
                                }
                                Text(f?.figure?.name ?: "—", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "清空开盒记录？",
            message = "将删除全部开盒历史和虚拟收藏柜中的虚拟藏品（实物收藏不受影响），且无法恢复。",
            confirmLabel = "清空",
            danger = true,
            onConfirm = { vm.clear() },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun StatsCard(list: List<BlindBoxOpening>, d: HistoryData, seriesFilter: Long?) {
    val series = seriesFilter?.let { id -> d.series.firstOrNull { it.series.id == id } }
    val expected: Map<Rarity, Double>? = series?.let { s ->
        val pool = d.figures.values.filter { it.figure.seriesId == s.series.id }.map { PoolFigure(it.figure.id, it.figure.rarityEnum) }
        BlindBoxEngine.effectiveTierRates(s.series.rates, pool)
    }
    BoxCard(Modifier.fillMaxWidth()) {
        Text("概率统计", style = MaterialTheme.typography.titleMedium)
        Text(
            if (series != null) "${series.series.name}：实际结果 vs 当前设定概率" else "全部系列的实际结果分布（选择单个系列可对比设定概率）",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Rarity.entries.forEach { r ->
            val count = list.count { it.rarityEnum == r }
            val actual = if (list.isEmpty()) 0.0 else count * 100.0 / list.size
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                RarityBadge(r, Modifier.width(80.dp), showLabel = true)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    GradientProgressBar((actual / 100).toFloat(), Modifier.fillMaxWidth(), colors = RarityStyle.colors(r).take(2))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "$count 次 · ${"%.1f".format(actual)}%" + (expected?.let { " / 设定 ${"%.2f".format((it[r] ?: 0.0) * 100)}%" } ?: ""),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("样本越多，实际分布越接近设定概率。共 ${list.size} 盒。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/* ---------------- 虚拟收藏柜 ---------------- */

@Composable
fun VirtualCabinetScreen(nav: AppNav) {
    val vm = boxViewModel { HistoryViewModel(it.repo) }
    val data by vm.data.collectAsStateWithLifecycle()
    SubScreen(title = "虚拟收藏柜", onBack = nav::back) { padding ->
        val d = data
        if (d == null) {
            LoadingState(Modifier.padding(padding)); return@SubScreen
        }
        val bySeries = d.series.map { s -> s to d.figures.values.filter { it.figure.seriesId == s.series.id } }
            .filter { (_, figs) -> figs.any { it.virtualQty > 0 } }
        if (bySeries.isEmpty()) {
            EmptyState(Icons.Rounded.Widgets, "虚拟收藏柜还是空的", "开盒后点击「收入虚拟收藏柜」，抽到的潮玩就会出现在这里。", Modifier.padding(padding), "去开盒") { nav.tab(Routes.BLINDBOX) }
            return@SubScreen
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(96.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                InfoNote("这里是虚拟开盒获得的藏品，仅作娱乐收集，不计入实物收藏数量、图鉴点亮或资产估值。")
            }
            bySeries.forEach { (s, figs) ->
                val got = figs.count { it.virtualQty > 0 }
                item(span = { GridItemSpan(maxLineSpan) }, key = "h${s.series.id}") {
                    Column(Modifier.padding(top = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(s.series.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text("$got/${figs.size} · 共 ${figs.sumOf { it.virtualQty }} 个", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.height(6.dp))
                        GradientProgressBar(got.toFloat() / figs.size, Modifier.fillMaxWidth())
                    }
                }
                items(figs.sortedBy { it.figure.rarityEnum.order }, key = { "v${it.figure.id}" }) { f ->
                    FigureTile(
                        name = f.figure.name,
                        subtitle = if (f.virtualQty > 0) "虚拟 ×${f.virtualQty}" else "未获得",
                        rarity = f.figure.rarityEnum,
                        seed = f.figure.artSeed,
                        colorA = f.figure.colorA,
                        colorB = f.figure.colorB,
                        imagePath = f.figure.imagePath,
                        owned = f.virtualQty > 0,
                        count = f.virtualQty,
                        revealHidden = false,
                        onClick = { nav.figure(f.figure.id) },
                    )
                }
            }
        }
    }
}
