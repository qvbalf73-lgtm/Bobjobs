package com.boxverse.app.ui.profile

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boxverse.app.AppEvent
import com.boxverse.app.BuildConfig
import com.boxverse.app.data.prefs.AppSettings
import com.boxverse.app.data.prefs.ThemeMode
import com.boxverse.app.data.repo.BackupData
import com.boxverse.app.domain.Currency
import com.boxverse.app.ui.components.BlindBoxArt
import com.boxverse.app.ui.components.BoxCard
import com.boxverse.app.ui.components.BoxTextField
import com.boxverse.app.ui.components.ChoiceChip
import com.boxverse.app.ui.components.ConfirmDialog
import com.boxverse.app.ui.components.InfoNote
import com.boxverse.app.ui.components.MenuRow
import com.boxverse.app.ui.components.SelectField
import com.boxverse.app.ui.components.SubScreen
import com.boxverse.app.ui.components.SurfaceSection
import com.boxverse.app.ui.navigation.AppNav
import com.boxverse.app.ui.navigation.LocalContainer
import com.boxverse.app.util.ReminderScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/* ======================= 设置 ======================= */

@Composable
fun SettingsScreen(nav: AppNav) {
    val container = LocalContainer.current
    val settingsRepo = container.settings
    val settings by settingsRepo.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val scope = rememberCoroutineScope()
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        scope.launch {
            settingsRepo.setReminder(granted)
            if (!granted) container.emit(AppEvent.Message("未获得通知权限，无法开启提醒"))
        }
    }

    SubScreen(title = "设置", onBack = nav::back) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SurfaceSection {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Icon(Icons.Rounded.DarkMode, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text("主题与显示", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { m ->
                            ChoiceChip(m.label, settings.themeMode == m, { scope.launch { settingsRepo.setThemeMode(m) } })
                        }
                    }
                }
            }
            SurfaceSection {
                MenuRow(Icons.Rounded.VolumeUp, "开盒音效", subtitle = "程序合成的拆盒与隐藏款音效", trailing = {
                    Switch(settings.soundEnabled, { v -> scope.launch { settingsRepo.setSound(v) } })
                })
                MenuRow(Icons.Rounded.Vibration, "触觉反馈", subtitle = "重要操作与开盒时震动", trailing = {
                    Switch(settings.hapticsEnabled, { v -> scope.launch { settingsRepo.setHaptics(v) } })
                })
                MenuRow(Icons.Rounded.Notifications, "每日签到提醒", subtitle = "每天 20:00 左右提醒签到（系统可能略有延迟）", trailing = {
                    Switch(settings.reminderEnabled, { v ->
                        if (v && Build.VERSION.SDK_INT >= 33 && !ReminderScheduler.canNotify(container.appContext)) {
                            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else scope.launch { settingsRepo.setReminder(v) }
                    })
                })
            }
            CurrencySection(settings, onBase = { scope.launch { settingsRepo.setBaseCurrency(it) } }) { code, rate ->
                scope.launch {
                    settingsRepo.setExchangeRate(code, rate)
                    container.emit(AppEvent.Message(if (rate == null) "已清除 $code 汇率" else "已保存 $code 汇率"))
                }
            }
        }
    }
}

@Composable
private fun CurrencySection(settings: AppSettings, onBase: (String) -> Unit, onRate: (String, Double?) -> Unit) {
    BoxCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(Icons.Rounded.CurrencyExchange, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Text("币种与汇率", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(10.dp))
        SelectField("统计基准币", Currency.of(settings.baseCurrency), Currency.entries.toList(), { "${it.code} · ${it.label}" }, { onBase(it.code) })
        Spacer(Modifier.height(10.dp))
        InfoNote("盒宇宙不联网获取汇率，也不会使用任何虚构的实时汇率。跨币种统计时，只有你手动填写了汇率的币种才会换算进合计；未填写的币种会在统计页单独列出。")
        Spacer(Modifier.height(10.dp))
        Currency.entries.filter { it.code != settings.baseCurrency }.forEach { c ->
            var text by remember(c, settings.exchangeRates) { mutableStateOf(settings.exchangeRates[c.code]?.toString() ?: "") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                BoxTextField(text, { text = it }, "1 ${c.code} = ? ${settings.baseCurrency}", Modifier.weight(1f), keyboardType = KeyboardType.Decimal)
                TextButton(onClick = {
                    val v = text.trim().toDoubleOrNull()
                    onRate(c.code, if (text.isBlank()) null else v?.takeIf { it > 0 })
                }) { Text(if (text.isBlank()) "清除" else "保存") }
            }
        }
        Text("示例：基准币为 CNY 时，若 1 SGD ≈ 5.3 CNY，则在 SGD 一栏填写 5.3。切换基准币后需重新填写。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/* ======================= 数据管理 ======================= */

@Composable
fun DataScreen(nav: AppNav) {
    val container = LocalContainer.current
    val repo = container.repo
    val scope = rememberCoroutineScope()
    val demoCount by repo.demoFigureCount.collectAsStateWithLifecycle(initialValue = 0)
    var busy by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<Pair<android.net.Uri, BackupData>?>(null) }
    var confirmClearDemo by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(0) }

    fun runOp(block: suspend () -> String) {
        busy = true
        scope.launch {
            val msg = runCatching { block() }.getOrElse { "操作失败：${it.message}" }
            busy = false
            container.emit(AppEvent.Message(msg))
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) runOp { "备份完成（含 ${container.backup.export(uri)} 张图片）" }
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) runOp { "已导出 ${container.backup.exportCsv(uri, repo.holdingRows.first())} 条收藏记录" }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                runCatching { container.backup.read(uri) }
                    .onSuccess { pendingRestore = uri to it }
                    .onFailure { container.emit(AppEvent.Message("无法读取备份：${it.message}")) }
                busy = false
            }
        }
    }

    SubScreen(title = "数据备份与管理", onBack = nav::back) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (busy) Text("处理中，请稍候…", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 20.dp))
            Text("备份与恢复", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp))
            SurfaceSection {
                MenuRow(Icons.Rounded.Upload, "备份全部数据", subtitle = "导出为 .zip（含收藏、开盒记录、图片），可保存到手机或网盘") {
                    exportLauncher.launch("盒宇宙备份_${LocalDate.now()}.zip")
                }
                MenuRow(Icons.Rounded.Restore, "从备份恢复", subtitle = "选择之前导出的 .zip 文件，将替换当前全部数据") {
                    importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                }
                MenuRow(Icons.Rounded.TableChart, "导出收藏表格 CSV", subtitle = "可用 Excel / WPS 打开的实物收藏清单") {
                    csvLauncher.launch("盒宇宙收藏_${LocalDate.now()}.csv")
                }
            }
            Text("演示数据", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp))
            SurfaceSection {
                if (demoCount > 0) {
                    MenuRow(Icons.Rounded.DeleteSweep, "一键清除演示数据", subtitle = "当前有 $demoCount 个演示款式。你自己添加的数据不会被删除", tint = Color(0xFFE2A23A)) {
                        confirmClearDemo = true
                    }
                } else {
                    MenuRow(Icons.Rounded.Refresh, "重新载入演示数据", subtitle = "载入虚构的品牌、系列与收藏，体验全部功能") {
                        runOp { if (repo.reloadDemoData()) "演示数据已载入" else "演示数据已存在" }
                    }
                }
            }
            Text("危险操作", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.error)
            SurfaceSection {
                MenuRow(Icons.Rounded.DeleteForever, "清空全部数据", subtitle = "删除所有收藏、记录与设置中的资料，无法恢复，建议先备份", tint = MaterialTheme.colorScheme.error) {
                    confirmWipe = 1
                }
            }
            Text(
                "所有数据只保存在本机的应用私有存储中。卸载 App 会删除数据，请定期备份。",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }

    pendingRestore?.let { (uri, data) ->
        ConfirmDialog(
            title = "确认恢复备份？",
            message = "备份时间：${com.boxverse.app.util.Dates.formatMillis(data.exportedAt)}\n" +
                "包含 ${data.series.size} 个系列、${data.figures.size} 个款式、${data.items.size} 条实物记录、${data.openings.size} 条开盒记录。\n\n" +
                "恢复后将替换当前全部数据。",
            confirmLabel = "恢复",
            danger = true,
            onConfirm = {
                if (data.dbVersion > com.boxverse.app.data.db.BoxDatabase.VERSION) {
                    container.emit(AppEvent.Message("该备份来自更新版本的 App，请先升级后再恢复"))
                } else runOp {
                    container.backup.restore(uri); "恢复完成"
                }
            },
            onDismiss = { pendingRestore = null },
        )
    }
    if (confirmClearDemo) {
        ConfirmDialog(
            title = "清除演示数据？",
            message = "将删除所有标记为「演示」的品牌、系列、款式、收藏、心愿和收藏柜，以及演示系列的开盒记录与虚拟藏品。\n如果你在演示款式上添加过自己的实物记录，这些款式会被保留并转为正式数据。",
            confirmLabel = "清除",
            danger = true,
            onConfirm = { runOp { repo.clearDemoData(); "演示数据已清除" } },
            onDismiss = { confirmClearDemo = false },
        )
    }
    if (confirmWipe == 1) {
        ConfirmDialog("清空全部数据？", "此操作会删除 App 中的所有数据且无法恢复。", "继续", danger = true, onConfirm = { confirmWipe = 2 }, onDismiss = { if (confirmWipe == 1) confirmWipe = 0 })
    }
    if (confirmWipe == 2) {
        ConfirmDialog("最后确认", "真的要清空全部数据吗？建议先完成备份。", "永久清空", danger = true, onConfirm = {
            runOp { repo.wipeAll(); "已清空全部数据" }
        }, onDismiss = { confirmWipe = 0 })
    }
}

/* ======================= 隐私说明 / 关于 ======================= */

@Composable
fun PrivacyScreen(nav: AppNav) {
    SubScreen(title = "隐私说明", onBack = nav::back) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(
                "数据存储" to "盒宇宙第一版完全离线运行，不需要注册或登录。你的收藏记录、图片、开盒记录、设置全部保存在手机本地的应用私有目录中，不会上传到任何服务器。",
                "权限使用" to "相机：仅在你点击「拍照」时调用系统相机拍摄潮玩照片。\n相册：通过系统照片选择器选择图片，App 只能访问你选中的图片。\n通知：仅在你开启「每日签到提醒」时使用。\n震动：用于操作反馈，可在设置中关闭。",
                "网络" to "当前版本不联网，不包含广告、统计或第三方追踪 SDK，也不内置任何第三方 API 密钥。",
                "虚拟开盒" to "虚拟开盒为免费娱乐模拟，不涉及任何真实金钱、充值、提现或奖励。概率为本 App 中虚构系列的设定值。",
                "AI 功能（规划中）" to "未来如提供 AI 识别，将通过安全的后端服务调用，并在使用前征得你的同意；AI 结果仅供参考，需要你核对确认。",
                "数据删除" to "你可以随时在「数据备份与管理」中清除演示数据或清空全部数据；卸载 App 也会删除全部本地数据。",
            ).forEach { (t, c) ->
                BoxCard(Modifier.fillMaxWidth(), elevation = 2.dp) {
                    Text(t, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(c, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun AboutScreen(nav: AppNav) {
    SubScreen(title = "关于盒宇宙", onBack = nav::back) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BlindBoxArt(0xFFC9B8FF, 0xFFFFB8D6, "", Modifier.padding(top = 8.dp).width(140.dp).height(140.dp), glow = 0.4f)
            Text("盒宇宙 BOXVERSE", style = MaterialTheme.typography.headlineMedium)
            Text("版本 ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("你的潮玩收藏宇宙，每一次拆盒都有惊喜。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            BoxCard(Modifier.fillMaxWidth(), elevation = 2.dp) {
                Text("已实现功能", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "· 首页收藏中心与每日任务、签到\n· 潮玩图鉴：品牌 / IP / 系列 / 款式管理，搜索、筛选、排序，收集进度与未解锁剪影\n" +
                        "· 实物收藏：多件管理、价格 / 渠道 / 成色 / 评分 / 故事、拍照与相册\n· 虚拟拆盲盒：1 / 5 / 10 连开、稀有度特效与音效、概率公示与配置、开盒记录、虚拟收藏柜\n" +
                        "· 成就勋章与收藏家等级\n· 收藏价值统计与图表、多币种手动汇率、售出与盈亏\n· 心愿清单（一键转收藏）\n· 重复款交换卡分享\n" +
                        "· 2D 收藏展示柜与分享图片\n· 明暗主题、数据备份恢复、CSV 导出、演示数据管理",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            BoxCard(Modifier.fillMaxWidth(), elevation = 2.dp) {
                Text("规划中（尚未实现）", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "· AI 拍照识别与收藏助手（需安全后端）\n· 云同步与账号\n· 在线交换广场、私信等社区功能\n· 3D 收藏室与导入 3D 模型\n· 收藏日记",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "演示内容中的品牌、角色与插画均为本 App 原创虚构，与任何真实品牌无关。",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
