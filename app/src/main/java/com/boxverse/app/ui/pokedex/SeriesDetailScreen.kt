package com.boxverse.app.ui.pokedex

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material.icons.rounded.Redeem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.boxverse.app.data.db.FigureRow
import com.boxverse.app.data.db.SeriesRow
import com.boxverse.app.data.repo.BoxRepository
import com.boxverse.app.data.repo.toRef
import com.boxverse.app.domain.BlindBoxEngine
import com.boxverse.app.domain.PoolFigure
import com.boxverse.app.domain.Rarity
import com.boxverse.app.domain.RateConfig
import com.boxverse.app.domain.SeriesProgress
import com.boxverse.app.domain.StatsCalculator
import com.boxverse.app.ui.components.BlindBoxArt
import com.boxverse.app.ui.components.BoxCard
import com.boxverse.app.ui.components.ConfirmDialog
import com.boxverse.app.ui.components.DemoTag
import com.boxverse.app.ui.components.EmptyState
import com.boxverse.app.ui.components.FigureTile
import com.boxverse.app.ui.components.GradientButton
import com.boxverse.app.ui.components.KeyValueRow
import com.boxverse.app.ui.components.LoadingState
import com.boxverse.app.ui.components.ProgressRing
import com.boxverse.app.ui.components.RarityBadge
import com.boxverse.app.ui.components.SubScreen
import com.boxverse.app.ui.components.TonalAction
import com.boxverse.app.ui.navigation.AppNav
import com.boxverse.app.ui.navigation.boxViewModel
import com.boxverse.app.ui.theme.LocalBoxColors
import com.boxverse.app.util.Dates
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SeriesDetailData(
    val row: SeriesRow,
    val figures: List<FigureRow>,
    val progress: SeriesProgress,
)

class SeriesDetailViewModel(private val repo: BoxRepository, seriesId: Long) : ViewModel() {
    /** null = 加载中；Result.failure = 系列不存在 */
    val data: StateFlow<Result<SeriesDetailData>?> = combine(repo.seriesRows, repo.figureRows) { series, figures ->
        val row = series.firstOrNull { it.series.id == seriesId } ?: return@combine Result.failure<SeriesDetailData>(IllegalStateException("系列不存在或已被删除"))
        val list = figures.filter { it.figure.seriesId == seriesId }.sortedWith(compareBy({ it.figure.rarityEnum.order }, { it.figure.code }, { it.figure.id }))
        val owned = list.filter { it.ownedQty > 0 }.map { it.figure.id }.toSet()
        val progress = StatsCalculator.seriesProgress(list.map { it.toRef() }, owned)[seriesId]
            ?: SeriesProgress(seriesId, 0, 0, 0, 0, 0, 0)
        Result.success(SeriesDetailData(row, list, progress))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch { repo.settings.setLastSeriesViewDay(Dates.today()) }
    }

    fun delete(onDone: () -> Unit) {
        val row = data.value?.getOrNull()?.row ?: return
        viewModelScope.launch {
            repo.deleteSeries(row.series)
            onDone()
        }
    }
}

@Composable
fun SeriesDetailScreen(nav: AppNav, seriesId: Long) {
    val vm = boxViewModel("series_$seriesId") { SeriesDetailViewModel(it.repo, seriesId) }
    val result by vm.data.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    val d = result?.getOrNull()

    SubScreen(
        title = d?.row?.series?.name ?: "系列详情",
        onBack = nav::back,
        actions = {
            if (d != null) {
                IconButton(onClick = { nav.seriesEdit(seriesId) }) { Icon(Icons.Rounded.Edit, "编辑系列") }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, "删除系列") }
            }
        },
    ) { padding ->
        val r = result
        when {
            r == null -> LoadingState(Modifier.padding(padding))
            r.isFailure -> EmptyState(Icons.Rounded.Redeem, "系列不存在", r.exceptionOrNull()?.message ?: "", Modifier.padding(padding), "返回", nav::back)
            d != null -> SeriesDetailContent(d, nav, Modifier.padding(padding))
        }
    }

    if (confirmDelete && d != null) {
        ConfirmDialog(
            title = "删除系列？",
            message = "将同时删除「${d.row.series.name}」下的 ${d.figures.size} 个款式、相关实物收藏记录、虚拟藏品和开盒记录，且无法恢复。",
            confirmLabel = "删除",
            danger = true,
            onConfirm = { vm.delete { nav.back() } },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun SeriesDetailContent(d: SeriesDetailData, nav: AppNav, modifier: Modifier) {
    val s = d.row.series
    val extra = LocalBoxColors.current
    val pool = remember(d.figures) { d.figures.map { PoolFigure(it.figure.id, it.figure.rarityEnum) } }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(listOf(lerp(Color(s.themeColor), Color.White, 0.2f), lerp(Color(s.accentColor), Color.White, 0.1f))))
                    .padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        if (s.isDemo) DemoTag()
                        Text(s.name, style = MaterialTheme.typography.headlineSmall, color = Color(0xFF2A2340))
                        Text("${d.row.brandName}\n${d.row.ipName} · ${s.releaseYear} 年", style = MaterialTheme.typography.bodySmall, color = Color(0xFF4A4266))
                        if (s.description.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(s.description, style = MaterialTheme.typography.bodySmall, color = Color(0xFF4A4266))
                        }
                    }
                    BlindBoxArt(s.themeColor, s.accentColor, s.name, Modifier.size(110.dp))
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            BoxCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(d.progress.fraction, Modifier.size(92.dp), stroke = 9.dp) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${d.progress.percent}%", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                            Text("${d.progress.owned}/${d.progress.total}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        KeyValueRow("常规款", "${d.progress.regularOwned} / ${d.progress.regularTotal}")
                        KeyValueRow("隐藏款", "${d.progress.hiddenOwned} / ${d.progress.hiddenTotal}", valueColor = extra.gold)
                        Text(
                            when {
                                d.progress.isFullComplete -> "🎉 含隐藏款全收集！"
                                d.progress.isComplete -> "✅ 常规款已集齐（视为完成系列）"
                                else -> "还差 ${d.progress.regularTotal - d.progress.regularOwned} 款常规款即可完成系列"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (s.blindBoxEnabled && d.figures.isNotEmpty()) {
                        GradientButton("虚拟开盒", Modifier.weight(1f), icon = Icons.Rounded.Redeem) { nav.boxSeries(s.id) }
                    }
                    TonalAction("添加款式", Icons.Rounded.Add, Modifier.weight(1f)) { nav.figureEdit(seriesId = s.id) }
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("系列图鉴（${d.figures.size} 款）", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
        }
        if (d.figures.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(Icons.Rounded.Add, "这个系列还没有款式", "添加全部款式后，就能看到收集进度与未解锁剪影", actionLabel = "添加款式", onAction = { nav.figureEdit(seriesId = s.id) })
            }
        }
        items(d.figures, key = { it.figure.id }) { r ->
            FigureTile(
                name = r.figure.name,
                subtitle = r.figure.code.ifBlank { r.figure.rarityEnum.label },
                rarity = r.figure.rarityEnum,
                seed = r.figure.artSeed,
                colorA = r.figure.colorA,
                colorB = r.figure.colorB,
                imagePath = r.figure.imagePath,
                owned = r.ownedQty > 0,
                count = r.ownedQty,
                onClick = { nav.figure(r.figure.id) },
            )
        }

        if (d.figures.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                BoxCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Percent, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("虚拟开盒概率", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        
                    }
                    Spacer(Modifier.height(8.dp))
                    val errors = BlindBoxEngine.validate(s.rates, pool)
                    Rarity.entries.forEach { rarity ->
                        val count = pool.count { it.rarity == rarity }
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RarityBadge(rarity, showLabel = true)
                            Spacer(Modifier.width(8.dp))
                            Text("$count 款", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            Text(
                                RateConfig.percentText(s.rates.of(rarity)) + if (count > 0) "（每款 ${"%.2f".format(s.rates.of(rarity) / 100.0 / count)}%）" else "",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (errors.isEmpty()) "概率总和 100%，同一稀有度内各款等概率。以上为本 App 设定的模拟概率，并非任何品牌的官方数据。"
                        else "⚠ " + errors.joinToString("；") + "。开盒时会在有款式的档位间按比例自动归一化。",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (errors.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(8.dp))
                    TonalAction("编辑系列与概率", Icons.Rounded.Edit, Modifier.fillMaxWidth()) { nav.seriesEdit(s.id) }
                }
            }
        }
    }
}
