package com.boxverse.app.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.boxverse.app.data.db.UserProfile
import com.boxverse.app.data.repo.BoxRepository
import com.boxverse.app.domain.Achievements
import com.boxverse.app.domain.CollectorStats
import com.boxverse.app.domain.LevelInfo
import com.boxverse.app.domain.LevelSystem
import com.boxverse.app.ui.components.BoxTextField
import com.boxverse.app.ui.components.GradientProgressBar
import com.boxverse.app.ui.components.InfoNote
import com.boxverse.app.ui.components.MenuRow
import com.boxverse.app.ui.components.StarField
import com.boxverse.app.ui.components.SurfaceSection
import com.boxverse.app.ui.home.Avatar
import com.boxverse.app.ui.navigation.AppNav
import com.boxverse.app.ui.navigation.Routes
import com.boxverse.app.ui.navigation.boxViewModel
import com.boxverse.app.ui.theme.LocalBoxColors
import com.boxverse.app.util.ImageStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileData(
    val profile: UserProfile,
    val stats: CollectorStats,
    val level: LevelInfo,
    val unlocked: Int,
    val virtualCount: Int,
    val cabinetCount: Int,
    val wishCount: Int,
)

class ProfileViewModel(private val repo: BoxRepository) : ViewModel() {
    val data: StateFlow<ProfileData?> = combine(
        repo.profile, repo.collectorStats, repo.achievementUnlocks,
        combine(repo.virtualItems, repo.cabinets, repo.wishlist) { v, c, w -> Triple(v.size, c.size, w.count { !it.purchased }) },
    ) { p, stats, unlocks, (v, c, w) ->
        ProfileData(
            profile = p ?: UserProfile(),
            stats = stats,
            level = LevelSystem.info(LevelSystem.computeXp(stats, Achievements.xpOf(unlocks.map { it.id }))),
            unlocked = unlocks.size,
            virtualCount = v,
            cabinetCount = c,
            wishCount = w,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun saveProfile(p: UserProfile) = viewModelScope.launch { repo.saveProfile(p) }
}

@Composable
fun ProfileScreen(nav: AppNav) {
    val vm = boxViewModel { ProfileViewModel(it.repo) }
    val data by vm.data.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    val d = data
    val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val p = d?.profile
        if (uri != null && p != null) scope.launch {
            ImageStore.import(context, uri)?.let { name ->
                ImageStore.delete(context, p.avatarPath)
                vm.saveProfile(p.copy(avatarPath = name))
            }
        }
    }

    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            val extra = LocalBoxColors.current
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp)).background(Brush.linearGradient(extra.heroGradient))) {
                StarField(Modifier.matchParentSize())
                Column(Modifier.statusBarsPadding().padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            Avatar(d?.profile?.avatarPath, d?.profile?.nickname ?: "", 76) {
                                pickAvatar.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                            Box(Modifier.align(Alignment.BottomEnd).size(24.dp).clip(RoundedCornerShape(50)).background(Color.White), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.PhotoCamera, "更换头像", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d?.profile?.nickname ?: "", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                            Text(d?.profile?.bio ?: "", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
                        }
                        IconButton(onClick = { editing = true }) { Icon(Icons.Rounded.Edit, "编辑资料", tint = Color.White) }
                    }
                    Spacer(Modifier.height(16.dp))
                    if (d != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Lv.${d.level.level} ${d.level.title}", style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.weight(1f))
                            Text(if (d.level.isMax) "MAX" else "${d.level.xp}/${d.level.nextLevelXp} XP", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(Modifier.height(6.dp))
                        GradientProgressBar(d.level.progress, Modifier.fillMaxWidth(), colors = listOf(Color(0xFFFFE08A), Color.White))
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                            HeaderNumber("实物", d.stats.totalQuantity)
                            HeaderNumber("款式", d.stats.distinctFigures)
                            HeaderNumber("完成系列", d.stats.completedSeries)
                            HeaderNumber("勋章", d.unlocked)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("等级与成就只统计你的正式数据，演示数据不计入。", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f))
                    }
                }
            }
        }
        item {
            SurfaceSection {
                MenuRow(Icons.Rounded.EmojiEvents, "成就勋章", subtitle = "已解锁 ${d?.unlocked ?: 0} / ${Achievements.ALL.size}", tint = LocalBoxColors.current.gold, trailing = { Chevron() }) { nav.go(Routes.ACHIEVEMENTS) }
                Divider()
                MenuRow(Icons.Rounded.Widgets, "我的收藏室", subtitle = "${d?.cabinetCount ?: 0} 个收藏柜", trailing = { Chevron() }) { nav.go(Routes.CABINETS) }
                Divider()
                MenuRow(Icons.Rounded.Inventory2, "虚拟收藏柜", subtitle = "${d?.virtualCount ?: 0} 个虚拟藏品", tint = Color(0xFF6FC3E8), trailing = { Chevron() }) { nav.go(Routes.VIRTUAL) }
                Divider()
                MenuRow(Icons.Rounded.History, "开盒记录", trailing = { Chevron() }) { nav.go(Routes.BOX_HISTORY) }
                Divider()
                MenuRow(Icons.Rounded.Favorite, "心愿清单", subtitle = "${d?.wishCount ?: 0} 个心愿", tint = MaterialTheme.colorScheme.secondary, trailing = { Chevron() }) { nav.go(Routes.WISHLIST) }
                Divider()
                MenuRow(Icons.Rounded.BarChart, "收藏价值与统计", trailing = { Chevron() }) { nav.go(Routes.STATS) }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                InfoNote(
                    "智能收藏助手（规划中）：拍照识别潮玩、自动建议名称与系列、生成收藏介绍、AI 收藏问答等功能将在后续版本通过安全的后端服务提供，识别结果需要你核对。当前版本已支持拍照与相册导入 + 手动编辑。",
                    icon = Icons.Rounded.Psychology,
                )
            }
        }
        item {
            SurfaceSection {
                MenuRow(Icons.Rounded.Palette, "主题、通知与币种", subtitle = "深色模式 · 音效震动 · 签到提醒 · 汇率", trailing = { Chevron() }) { nav.go(Routes.SETTINGS) }
                Divider()
                MenuRow(Icons.Rounded.Backup, "数据备份与管理", subtitle = "备份恢复 · 导出表格 · 演示数据", tint = Color(0xFF5CC8A0), trailing = { Chevron() }) { nav.go(Routes.DATA) }
                Divider()
                MenuRow(Icons.Rounded.PrivacyTip, "隐私说明", tint = Color(0xFF9AA3C7), trailing = { Chevron() }) { nav.go(Routes.PRIVACY) }
                Divider()
                MenuRow(Icons.Rounded.Info, "关于盒宇宙", tint = Color(0xFF9AA3C7), trailing = { Chevron() }) { nav.go(Routes.ABOUT) }
            }
        }
    }

    if (editing && d != null) {
        var name by remember { mutableStateOf(d.profile.nickname) }
        var bio by remember { mutableStateOf(d.profile.bio) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("编辑资料") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BoxTextField(name, { if (it.length <= 20) name = it }, "昵称")
                    BoxTextField(bio, { if (it.length <= 40) bio = it }, "个性签名")
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    vm.saveProfile(d.profile.copy(nickname = name.trim(), bio = bio.trim()))
                    editing = false
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("取消") } },
            shape = RoundedCornerShape(28.dp),
        )
    }
}

@Composable
private fun HeaderNumber(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$value", style = MaterialTheme.typography.titleLarge, color = Color.White)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
    }
}

@Composable
fun Chevron() = Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)

@Composable
fun Divider() = HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
