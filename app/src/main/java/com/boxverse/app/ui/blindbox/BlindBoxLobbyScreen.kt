package com.boxverse.app.ui.blindbox

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Redeem
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.boxverse.app.data.db.FigureRow
import com.boxverse.app.data.db.SeriesRow
import com.boxverse.app.data.repo.BoxRepository
import com.boxverse.app.domain.BlindBoxEngine
import com.boxverse.app.domain.PoolFigure
import com.boxverse.app.domain.Rarity
import com.boxverse.app.domain.RateConfig
import com.boxverse.app.ui.components.BlindBoxArt
import com.boxverse.app.ui.components.BoxCard
import com.boxverse.app.ui.components.DemoTag
import com.boxverse.app.ui.components.EmptyState
import com.boxverse.app.ui.components.GradientButton
import com.boxverse.app.ui.components.LoadingState
import com.boxverse.app.ui.components.StarField
import com.boxverse.app.ui.navigation.AppNav
import com.boxverse.app.ui.navigation.Routes
import com.boxverse.app.ui.navigation.boxViewModel
import com.boxverse.app.util.Dates
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class BoxSeriesCard(val row: SeriesRow, val figures: List<FigureRow>, val virtualOwned: Int)

data class LobbyData(
    val series: List<BoxSeriesCard>,
    val todayCount: Int,
    val totalCount: Int,
    val virtualCount: Int,
    val hiddenCount: Int,
)

class BlindBoxLobbyViewModel(repo: BoxRepository) : ViewModel() {
    val data: StateFlow<LobbyData?> = combine(repo.seriesRows, repo.figureRows, repo.openings, repo.virtualItems) { series, figures, openings, virtual ->
        val today = Dates.today()
        val byS = figures.groupBy { it.figure.seriesId }
        LobbyData(
            series = series.filter { it.series.blindBoxEnabled && it.figureCount > 0 }.map { r ->
                val list = byS[r.series.id].orEmpty()
                BoxSeriesCard(r, list, list.count { it.virtualQty > 0 })
            },
            todayCount = openings.count { Dates.epochDayOfMillis(it.openedAt) == today },
            totalCount = openings.size,
            virtualCount = virtual.size,
            hiddenCount = openings.count { it.rarityEnum.isHidden },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

@Composable
fun BlindBoxLobbyScreen(nav: AppNav) {
    val vm = boxViewModel { BlindBoxLobbyViewModel(it.repo) }
    val data by vm.data.collectAsStateWithLifecycle()
    val d = data
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { LobbyHeader(d, nav) }
        item {
            Row(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.VerifiedUser, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "虚拟开盒为免费娱乐模拟，不涉及真实金钱、充值、提现或奖励。抽中的是虚拟藏品，与实物收藏分开存放，不计入资产估值。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        when {
            d == null -> item { LoadingState() }
            d.series.isEmpty() -> item {
                EmptyState(
                    Icons.Rounded.Redeem, "还没有可开的盲盒系列",
                    "在图鉴中新建系列并添加款式（打开「加入虚拟盲盒专区」）即可在这里开盒。",
                    actionLabel = "新建系列", onAction = { nav.seriesEdit() },
                )
            }
            else -> {
                item {
                    Text("选择盲盒系列", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
                }
                items(d.series, key = { it.row.series.id }) { card -> BoxSeriesItem(card) { nav.boxSeries(card.row.series.id) } }
                item {
                    BoxCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), onClick = { nav.seriesEdit() }, elevation = 2.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("创建我自己的盲盒系列", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LobbyHeader(d: LobbyData?, nav: AppNav) {
    val transition = rememberInfiniteTransition(label = "lobby")
    val float by transition.animateFloat(-6f, 6f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "float")
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF2B2160), Color(0xFF5B3A8C), Color(0xFFB0679C))))
    ) {
        StarField(Modifier.matchParentSize(), count = 40)
        Column(Modifier.statusBarsPadding().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("拆盲盒", style = MaterialTheme.typography.headlineLarge, color = Color.White)
                    Text("每一次拆盒都有惊喜 ✨", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                }
                BlindBoxArt(0xFFC9B8FF, 0xFFFFB8D6, "", Modifier.size(96.dp).offset(y = float.dp), glow = 0.5f)
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeaderStat("今日开盒", "${d?.todayCount ?: 0}", Modifier.weight(1f))
                HeaderStat("累计开盒", "${d?.totalCount ?: 0}", Modifier.weight(1f))
                HeaderStat("开出隐藏", "${d?.hiddenCount ?: 0}", Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeaderButton("开盒记录", Icons.Rounded.History, Modifier.weight(1f)) { nav.go(Routes.BOX_HISTORY) }
                HeaderButton("虚拟收藏柜 ${d?.virtualCount ?: 0}", Icons.Rounded.Widgets, Modifier.weight(1f)) { nav.go(Routes.VIRTUAL) }
            }
        }
    }
}

@Composable
private fun HeaderStat(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.14f)).padding(10.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = Color.White)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
    }
}

@Composable
private fun HeaderButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.22f)).clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun BoxSeriesItem(card: BoxSeriesCard, onClick: () -> Unit) {
    val s = card.row.series
    val pool = card.figures.map { PoolFigure(it.figure.id, it.figure.rarityEnum) }
    val tiers = BlindBoxEngine.effectiveTierRates(s.rates, pool)
    val hiddenRate = ((tiers[Rarity.SSR] ?: 0.0) + (tiers[Rarity.UR] ?: 0.0)) * 10000
    BoxCard(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        onClick = onClick,
        contentPadding = PaddingValues(0.dp),
        brush = Brush.linearGradient(listOf(lerp(Color(s.themeColor), Color.White, 0.55f), lerp(Color(s.accentColor), Color.White, 0.5f))),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            BlindBoxArt(s.themeColor, s.accentColor, s.name, Modifier.size(104.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.name, style = MaterialTheme.typography.titleLarge, color = Color(0xFF2A2340), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (s.isDemo) {
                        Spacer(Modifier.width(6.dp)); DemoTag()
                    }
                }
                Text(card.row.brandName, style = MaterialTheme.typography.bodySmall, color = Color(0xFF4A4266), maxLines = 1)
                Spacer(Modifier.height(6.dp))
                Text(
                    "共 ${card.figures.size} 款 · 隐藏款合计 ${RateConfig.percentText(hiddenRate.toInt())}",
                    style = MaterialTheme.typography.labelMedium, color = Color(0xFF4A4266),
                )
                Text("虚拟图鉴 ${card.virtualOwned}/${card.figures.size}", style = MaterialTheme.typography.labelMedium, color = Color(0xFF6A5ACD))
                Spacer(Modifier.height(8.dp))
                GradientButton("进入开盒", icon = Icons.Rounded.AutoAwesome, onClick = onClick)
            }
        }
    }
}
