package com.boxverse.app.ui.profile

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.Redeem
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.boxverse.app.data.repo.BoxRepository
import com.boxverse.app.domain.AchievementProgress
import com.boxverse.app.domain.Achievements
import com.boxverse.app.domain.LevelInfo
import com.boxverse.app.domain.LevelSystem
import com.boxverse.app.ui.components.BoxCard
import com.boxverse.app.ui.components.GradientProgressBar
import com.boxverse.app.ui.components.LoadingState
import com.boxverse.app.ui.components.SubScreen
import com.boxverse.app.ui.navigation.AppNav
import com.boxverse.app.ui.navigation.boxViewModel
import com.boxverse.app.util.Dates
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class AchievementsData(val level: LevelInfo, val list: List<AchievementProgress>)

class AchievementsViewModel(repo: BoxRepository) : ViewModel() {
    val data: StateFlow<AchievementsData?> = combine(repo.collectorStats, repo.achievementUnlocks) { stats, unlocks ->
        val map = unlocks.associate { it.id to it.unlockedAt }
        AchievementsData(
            LevelSystem.info(LevelSystem.computeXp(stats, Achievements.xpOf(map.keys))),
            Achievements.progress(stats, map).sortedWith(compareBy({ !it.unlocked }, { -it.fraction })),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

fun achievementIcon(key: String): ImageVector = when (key) {
    "favorite" -> Icons.Rounded.Favorite
    "collections" -> Icons.Rounded.Collections
    "workspace" -> Icons.Rounded.WorkspacePremium
    "check" -> Icons.Rounded.CheckCircle
    "diamond" -> Icons.Rounded.Diamond
    "fire" -> Icons.Rounded.LocalFireDepartment
    "gift" -> Icons.Rounded.Redeem
    "sparkle" -> Icons.Rounded.AutoAwesome
    "crown" -> Icons.Rounded.MilitaryTech
    "star" -> Icons.Rounded.Star
    "cabinet" -> Icons.Rounded.Widgets
    "swap" -> Icons.Rounded.SwapHoriz
    "money" -> Icons.Rounded.AttachMoney
    else -> Icons.Rounded.EmojiEvents
}

@Composable
fun AchievementsScreen(nav: AppNav) {
    val vm = boxViewModel { AchievementsViewModel(it.repo) }
    val data by vm.data.collectAsStateWithLifecycle()
    SubScreen(title = "成就与等级", onBack = nav::back) { padding ->
        val d = data
        if (d == null) {
            LoadingState(Modifier.padding(padding)); return@SubScreen
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { LevelCard(d.level) }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("成就勋章 · ${d.list.count { it.unlocked }}/${d.list.size}", style = MaterialTheme.typography.titleMedium)
            }
            items(d.list, key = { it.def.id }) { a -> AchievementCard(a) }
        }
    }
}

@Composable
private fun LevelCard(level: LevelInfo) {
    BoxCard(Modifier.fillMaxWidth(), brush = Brush.linearGradient(listOf(Color(0xFFC6B6FF), Color(0xFFFFC9DF), Color(0xFFFFE6A6)))) {
        Text("当前等级", style = MaterialTheme.typography.labelLarge, color = Color(0xFF4A4266))
        Text("Lv.${level.level} ${level.title}", style = MaterialTheme.typography.headlineMedium, color = Color(0xFF2A2340))
        Spacer(Modifier.height(8.dp))
        GradientProgressBar(level.progress, Modifier.fillMaxWidth(), height = 10.dp, colors = listOf(Color(0xFF8F7BEF), Color(0xFFF59AC4)))
        Spacer(Modifier.height(4.dp))
        Text(if (level.isMax) "已达到最高等级！" else "${level.xp} XP · 距下一级还差 ${level.nextLevelXp - level.xp} XP", style = MaterialTheme.typography.labelMedium, color = Color(0xFF4A4266))
        Spacer(Modifier.height(12.dp))
        Text("称号之路", style = MaterialTheme.typography.titleSmall, color = Color(0xFF2A2340))
        Spacer(Modifier.height(6.dp))
        LevelSystem.milestones.forEach { (lv, title) ->
            val reached = level.level >= lv
            Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (reached) Icons.Rounded.CheckCircle else Icons.Rounded.Lock, null, tint = if (reached) Color(0xFF8F7BEF) else Color(0xFF9D94C4), modifier = Modifier.size(16.dp))
                Text("  Lv.$lv  $title", style = MaterialTheme.typography.bodySmall, color = if (reached) Color(0xFF2A2340) else Color(0xFF6E6688))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "经验来源：每件实物 +10，每个新款式 +20，完成系列 +150，隐藏款 +60，签到 +10，虚拟开盒 +2，以及成就奖励。",
            style = MaterialTheme.typography.labelSmall, color = Color(0xFF4A4266),
        )
    }
}

@Composable
private fun AchievementCard(a: AchievementProgress) {
    val unlocked = a.unlocked
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier.fillMaxWidth()
            .shadow(if (unlocked) 8.dp else 1.dp, shape, ambientColor = Color(0xFFE2AE45), spotColor = Color(0xFFE2AE45))
            .clip(shape)
            .background(
                if (unlocked) Brush.verticalGradient(listOf(Color(0xFFFFF3D1), Color(0xFFFFE1EE)))
                else Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.surfaceContainerHigh))
            )
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(
                if (unlocked) Brush.linearGradient(listOf(Color(0xFFFFD86B), Color(0xFFFF9FC7)))
                else Brush.linearGradient(listOf(Color(0xFFCFC8E0), Color(0xFFBDB5D3)))
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(achievementIcon(a.def.icon), null, tint = Color.White, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(a.def.title, style = MaterialTheme.typography.titleSmall, color = if (unlocked) Color(0xFF2A2340) else MaterialTheme.colorScheme.onSurface)
        Text(a.def.description, style = MaterialTheme.typography.labelSmall, color = if (unlocked) Color(0xFF6E6688) else MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, minLines = 2)
        Spacer(Modifier.height(8.dp))
        if (unlocked) {
            Text("✓ ${Dates.formatMillis(a.unlockedAt ?: 0)} 解锁", style = MaterialTheme.typography.labelSmall, color = Color(0xFFB57300))
        } else {
            GradientProgressBar(a.fraction, Modifier.fillMaxWidth(), height = 6.dp)
            Spacer(Modifier.height(4.dp))
            val cur = if (a.def.id == "value_1000") a.current / 100 else a.current
            val target = if (a.def.id == "value_1000") a.def.target / 100 else a.def.target
            Text("$cur / $target", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("+${a.def.xp} XP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    }
}
