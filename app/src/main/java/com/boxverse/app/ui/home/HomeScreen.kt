package com.boxverse.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Redeem
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.boxverse.app.data.db.HoldingRow
import com.boxverse.app.data.db.SeriesRow
import com.boxverse.app.data.db.UserProfile
import com.boxverse.app.data.repo.BoxRepository
import com.boxverse.app.data.repo.toRecord
import com.boxverse.app.data.repo.toRef
import com.boxverse.app.domain.Achievements
import com.boxverse.app.domain.LevelInfo
import com.boxverse.app.domain.LevelSystem
import com.boxverse.app.domain.Money
import com.boxverse.app.domain.MoneySummary
import com.boxverse.app.domain.Rarity
import com.boxverse.app.domain.SeriesProgress
import com.boxverse.app.domain.StatsCalculator
import com.boxverse.app.ui.components.BoxCard
import com.boxverse.app.ui.components.ErrorState
import com.boxverse.app.ui.components.FigureArt
import com.boxverse.app.ui.components.GradientButton
import com.boxverse.app.ui.components.GradientProgressBar
import com.boxverse.app.ui.components.LoadingState
import com.boxverse.app.ui.components.ProgressRing
import com.boxverse.app.ui.components.RarityFrame
import com.boxverse.app.ui.components.SectionHeader
import com.boxverse.app.ui.components.SeriesCover
import com.boxverse.app.ui.components.StarField
import com.boxverse.app.ui.components.StatTile
import com.boxverse.app.ui.components.UiState
import com.boxverse.app.ui.navigation.AppNav
import com.boxverse.app.ui.navigation.LocalContainer
import com.boxverse.app.ui.navigation.Routes
import com.boxverse.app.ui.navigation.boxViewModel
import com.boxverse.app.ui.theme.LocalBoxColors
import com.boxverse.app.util.Dates
import com.boxverse.app.util.ImageStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DailyTask(val title: String, val desc: String, val done: Boolean, val icon: ImageVector)
data class SeriesCard(val row: SeriesRow, val progress: SeriesProgress?)

data class HomeData(
    val profile: UserProfile,
    val level: LevelInfo,
    val totalQuantity: Int,
    val distinctFigures: Int,
    val completedSeries: Int,
    val hiddenOwned: Int,
    val cost: MoneySummary,
    val value: MoneySummary,
    val profit: MoneySummary,
    val noEstimate: Int,
    val recent: List<HoldingRow>,
    val recommend: List<SeriesCard>,
    val wishPending: Int,
    val tasks: List<DailyTask>,
    val checkedInToday: Boolean,
    val streak: Int,
    val hasDemo: Boolean,
    val unlockedCount: Int,
)

class HomeViewModel(private val repo: BoxRepository) : ViewModel() {
    val state: StateFlow<UiState<HomeData>> = combine(
        combine(repo.profile, repo.holdingRows, repo.figureRows) { p, h, f -> Triple(p, h, f) },
        combine(repo.seriesRows, repo.collectorStats, repo.achievementUnlocks) { s, c, a -> Triple(s, c, a) },
        combine(repo.checkIns, repo.openings, repo.wishlist) { ci, o, w -> Triple(ci, o, w) },
        combine(repo.settings.settings, repo.demoFigureCount) { st, d -> st to d },
    ) { (profile, holdings, figures), (series, stats, unlocks), (checks, openings, wishes), (settings, demoCount) ->
        val today = Dates.today()
        val conv = settings.converter
        val records = holdings.map { it.toRecord() }
        val ownedQty = StatsCalculator.ownedQuantityByFigure(records)
        val progress = StatsCalculator.seriesProgress(figures.map { it.toRef() }, ownedQty.keys)
        val rarity = figures.associate { it.figure.id to it.figure.rarityEnum }
        val xp = LevelSystem.computeXp(stats, Achievements.xpOf(unlocks.map { it.id }))
        val checkDays = checks.map { it.epochDay }.toSet()
        val tasks = listOf(
            DailyTask("每日签到", "签到获得 10 XP，连续签到解锁成就", today in checkDays, Icons.Rounded.EventAvailable),
            DailyTask("拆一个虚拟盲盒", "去盲盒专区试试今天的手气", openings.any { Dates.epochDayOfMillis(it.openedAt) == today }, Icons.Rounded.Redeem),
            DailyTask("浏览一个系列图鉴", "看看还差哪几款就能集齐", settings.lastSeriesViewDay == today, Icons.Rounded.Collections),
        )
        val recommend = series
            .map { SeriesCard(it, progress[it.series.id]) }
            .sortedWith(
                compareBy<SeriesCard> { if (it.progress?.isFullComplete == true) 1 else 0 }
                    .thenByDescending { it.progress?.fraction ?: 0f }
            )
            .take(8)
        UiState.Success(
            HomeData(
                profile = profile ?: UserProfile(),
                level = LevelSystem.info(xp),
                totalQuantity = ownedQty.values.sum(),
                distinctFigures = ownedQty.size,
                completedSeries = progress.values.count { it.isComplete },
                hiddenOwned = ownedQty.keys.count { rarity[it]?.isHidden == true },
                cost = StatsCalculator.totalCost(records, conv),
                value = StatsCalculator.estimatedValue(records, conv),
                profit = StatsCalculator.unrealizedProfit(records, conv),
                noEstimate = StatsCalculator.itemsWithoutEstimate(records),
                recent = holdings.filter { it.item.statusEnum.isHolding }.distinctBy { it.item.figureId }.take(10),
                recommend = recommend,
                wishPending = wishes.count { !it.purchased },
                tasks = tasks,
                checkedInToday = today in checkDays,
                streak = StatsCalculator.checkInStreak(checkDays, today),
                hasDemo = demoCount > 0,
                unlockedCount = unlocks.size,
            )
        ) as UiState<HomeData>
    }
        .catch { emit(UiState.Error(it.message ?: "加载失败")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Loading)

    fun checkIn(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.checkIn()
            onDone()
        }
    }
}

@Composable
fun HomeScreen(nav: AppNav) {
    val vm = boxViewModel { HomeViewModel(it.repo) }
    val state by vm.state.collectAsStateWithLifecycle()
    val container = LocalContainer.current
    when (val s = state) {
        is UiState.Loading -> LoadingState(Modifier.statusBarsPadding())
        is UiState.Error -> ErrorState(s.message, Modifier.statusBarsPadding())
        is UiState.Empty -> ErrorState(s.message, Modifier.statusBarsPadding())
        is UiState.Success -> HomeContent(s.data, nav, onCheckIn = {
            vm.checkIn {
                container.haptics.success()
                container.sound.reveal(Rarity.SR)
            }
        })
    }
}

@Composable
private fun HomeContent(data: HomeData, nav: AppNav, onCheckIn: () -> Unit) {
    val extra = LocalBoxColors.current
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item { HeroHeader(data, onCheckIn, onProfile = { nav.tab(Routes.PROFILE) }) }

        if (data.hasDemo) {
            item {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0x22FFB020))
                        .clickable { nav.go(Routes.DATA) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = Color(0xFFB57300), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "当前包含演示数据（标有\"演示\"），供你体验功能。点此管理或一键清除。",
                        style = MaterialTheme.typography.bodySmall, color = Color(0xFF8A5A00),
                    )
                }
            }
        }

        item { CollectionStatsCard(data, nav) }
        item { ValueCard(data, nav) }
        item { TasksCard(data, onCheckIn, nav) }

        item { SectionHeader("最近获得", subtitle = "你最新入手的实物潮玩", actionLabel = "全部", onAction = { nav.tab(Routes.POKEDEX) }) }
        item {
            if (data.recent.isEmpty()) {
                BoxCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), onClick = { nav.itemEdit() }) {
                    Text("还没有收藏记录，点这里添加第一件潮玩吧！", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(data.recent, key = { it.item.id }) { h -> RecentCard(h) { nav.figure(h.item.figureId) } }
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
        item { QuickEntries(data, nav) }

        item { SectionHeader("推荐探索系列", subtitle = "根据你的收集进度推荐", actionLabel = "图鉴", onAction = { nav.tab(Routes.POKEDEX) }) }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(data.recommend, key = { it.row.series.id }) { card ->
                    SeriesExploreCard(card, onClick = { nav.series(card.row.series.id) }, onOpen = { nav.boxSeries(card.row.series.id) })
                }
            }
        }
        item {
            Text(
                "盒宇宙 BOXVERSE · 每一次拆盒都有惊喜",
                style = MaterialTheme.typography.labelSmall,
                color = extra.silhouette,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun HeroHeader(data: HomeData, onCheckIn: () -> Unit, onProfile: () -> Unit) {
    val extra = LocalBoxColors.current
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
            .background(Brush.linearGradient(extra.heroGradient))
    ) {
        StarField(Modifier.matchParentSize())
        Column(Modifier.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(data.profile.avatarPath, data.profile.nickname, 56, onProfile)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("${Dates.greeting()}，", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
                    Text(data.profile.nickname, style = MaterialTheme.typography.headlineSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (data.checkedInToday) {
                    Column(
                        Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.25f)).padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.LocalFireDepartment, null, tint = Color(0xFFFFE08A), modifier = Modifier.size(16.dp))
                            Text(" ${data.streak} 天", color = Color.White, style = MaterialTheme.typography.labelLarge)
                        }
                        Text("已签到", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    GradientButton("签到", colors = listOf(Color(0xFFFFC94D), Color(0xFFFF8FB8)), onClick = onCheckIn)
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.3f)).padding(horizontal = 10.dp, vertical = 3.dp)) {
                    Text("Lv.${data.level.level} ${data.level.title}", color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    if (data.level.isMax) "已满级" else "${data.level.xp} / ${data.level.nextLevelXp} XP",
                    color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelMedium,
                )
            }
            Spacer(Modifier.height(8.dp))
            GradientProgressBar(data.level.progress, Modifier.fillMaxWidth(), colors = listOf(Color(0xFFFFE08A), Color.White))
            Spacer(Modifier.height(8.dp))
            Text("今天也要和喜欢的潮玩相遇呀 ✨", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun Avatar(path: String?, name: String, sizeDp: Int, onClick: (() -> Unit)? = null) {
    val context = LocalContext.current
    Box(
        Modifier.size(sizeDp.dp).clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(0xFFFFE6A6), Color(0xFFFFB8D6))))
            .border(2.dp, Color.White, CircleShape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        contentAlignment = Alignment.Center,
    ) {
        if (path != null) {
            val file = remember(path) { ImageStore.file(context, path) }
            AsyncImage(model = file, contentDescription = "头像", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(name.take(1).ifBlank { "盒" }, style = MaterialTheme.typography.titleLarge, color = Color(0xFF8F7BEF), fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun CollectionStatsCard(data: HomeData, nav: AppNav) {
    BoxCard(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
        onClick = { nav.tab(Routes.POKEDEX) },
        brush = Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer)),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("实物收藏总数", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${data.totalQuantity}", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(" 件", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(bottom = 6.dp))
                }
            }
            Icon(Icons.Rounded.Inventory2, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("已收藏款式", "${data.distinctFigures}", Modifier.weight(1f), Icons.Rounded.Collections)
            StatTile("完成系列", "${data.completedSeries}", Modifier.weight(1f), Icons.Rounded.CheckCircle, tint = LocalBoxColors.current.success)
            StatTile("隐藏款", "${data.hiddenOwned}", Modifier.weight(1f), Icons.Rounded.Diamond, tint = LocalBoxColors.current.gold)
        }
    }
}

@Composable
private fun ValueCard(data: HomeData, nav: AppNav) {
    val extra = LocalBoxColors.current
    BoxCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(), onClick = { nav.go(Routes.STATS) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("收藏资产", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("查看统计 ›", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(12.dp))
        Row {
            Column(Modifier.weight(1f)) {
                Text("总购买成本", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Money.formatCompact(data.cost.total, data.cost.baseCurrency), style = MaterialTheme.typography.titleLarge)
            }
            Column(Modifier.weight(1f)) {
                Text("估算价值", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Money.formatCompact(data.value.total, data.value.baseCurrency), style = MaterialTheme.typography.titleLarge, color = extra.gold)
            }
            Column(Modifier.weight(1f)) {
                Text("估算盈亏", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val p = data.profit.total
                Text(
                    (if (p > 0) "+" else "") + Money.formatCompact(p, data.profit.baseCurrency),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (p >= 0) extra.success else extra.danger,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        val notes = buildList {
            add("估值为你手动填写的价格")
            if (data.noEstimate > 0) add("${data.noEstimate} 条记录未填估值，按购入成本计")
            if (data.cost.hasUnconverted) add("另有 ${data.cost.unconvertedText()} 未设置汇率、未计入")
        }
        Text(notes.joinToString("；"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TasksCard(data: HomeData, onCheckIn: () -> Unit, nav: AppNav) {
    val done = data.tasks.count { it.done }
    BoxCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("每日收藏任务", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("$done / ${data.tasks.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(8.dp))
        GradientProgressBar(done.toFloat() / data.tasks.size, Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        data.tasks.forEachIndexed { i, t ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable {
                    when (i) {
                        0 -> if (!t.done) onCheckIn()
                        1 -> nav.tab(Routes.BLINDBOX)
                        else -> nav.tab(Routes.POKEDEX)
                    }
                }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(t.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.title, style = MaterialTheme.typography.titleSmall)
                    Text(t.desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(
                    if (t.done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    if (t.done) "已完成" else "未完成",
                    tint = if (t.done) LocalBoxColors.current.success else MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun RecentCard(h: HoldingRow, onClick: () -> Unit) {
    val rarity = Rarity.fromName(h.rarity)
    Column(Modifier.width(118.dp).clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick)) {
        RarityFrame(rarity, Modifier.fillMaxWidth().aspectRatio(0.9f)) {
            FigureArt(h.artSeed, h.colorA, h.colorB, rarity, Modifier.fillMaxSize().padding(6.dp), imagePath = h.figureImage)
        }
        Spacer(Modifier.height(6.dp))
        Text(h.figureName, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${h.seriesName} · ${Dates.format(h.item.acquiredEpochDay)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun QuickEntries(data: HomeData, nav: AppNav) = Column {
    val extra = LocalBoxColors.current
    Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        QuickEntry("心愿清单", "${data.wishPending} 个心愿", Icons.Rounded.Favorite, MaterialTheme.colorScheme.secondary, Modifier.weight(1f)) { nav.go(Routes.WISHLIST) }
        QuickEntry("收藏统计", "资产与分布", Icons.Rounded.BarChart, MaterialTheme.colorScheme.primary, Modifier.weight(1f)) { nav.go(Routes.STATS) }
    }
    Spacer(Modifier.height(10.dp))
    Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        QuickEntry("我的收藏柜", "布置展示柜", Icons.Rounded.Widgets, Color(0xFF6FC3E8), Modifier.weight(1f)) { nav.go(Routes.CABINETS) }
        QuickEntry("成就勋章", "已解锁 ${data.unlockedCount}", Icons.Rounded.EmojiEvents, extra.gold, Modifier.weight(1f)) { nav.go(Routes.ACHIEVEMENTS) }
    }
}

@Composable
private fun QuickEntry(title: String, sub: String, icon: ImageVector, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    BoxCard(modifier, onClick = onClick, contentPadding = PaddingValues(14.dp), elevation = 3.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SeriesExploreCard(card: SeriesCard, onClick: () -> Unit, onOpen: () -> Unit) {
    val s = card.row.series
    val p = card.progress
    BoxCard(Modifier.width(200.dp), onClick = onClick, contentPadding = PaddingValues(0.dp), elevation = 4.dp) {
        Box {
            SeriesCover(s.themeColor, s.accentColor, s.name, Modifier.fillMaxWidth().height(130.dp))
            ProgressRing(
                p?.fraction ?: 0f,
                Modifier.padding(10.dp).size(46.dp).align(Alignment.TopEnd).clip(CircleShape).background(Color.White.copy(alpha = 0.8f)),
                stroke = 4.dp,
            ) {
                Text("${p?.percent ?: 0}%", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2A2340), fontWeight = FontWeight.Bold)
            }
        }
        Column(Modifier.padding(12.dp)) {
            Text(s.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(card.row.brandName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${p?.owned ?: 0}/${p?.total ?: card.row.figureCount}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                if (s.blindBoxEnabled && card.row.figureCount > 0) {
                    Text(
                        "去开盒",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier.clip(RoundedCornerShape(50))
                            .background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)))
                            .clickable(onClick = onOpen)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}
