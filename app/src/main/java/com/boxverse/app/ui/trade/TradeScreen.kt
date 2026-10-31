package com.boxverse.app.ui.trade

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.boxverse.app.AppEvent
import com.boxverse.app.data.db.FigureRow
import com.boxverse.app.data.db.TradeListing
import com.boxverse.app.data.repo.BoxRepository
import com.boxverse.app.ui.components.BoxCard
import com.boxverse.app.ui.components.BoxTextField
import com.boxverse.app.ui.components.CaptureBox
import com.boxverse.app.ui.components.ChoiceChip
import com.boxverse.app.ui.components.DemoTag
import com.boxverse.app.ui.components.EmptyState
import com.boxverse.app.ui.components.FigureArt
import com.boxverse.app.ui.components.GradientButton
import com.boxverse.app.ui.components.LoadingState
import com.boxverse.app.ui.components.Pill
import com.boxverse.app.ui.components.PillTabs
import com.boxverse.app.ui.components.QuantityStepper
import com.boxverse.app.ui.components.RarityBadge
import com.boxverse.app.ui.components.RarityFrame
import com.boxverse.app.ui.components.SoftBackground
import com.boxverse.app.ui.components.StarField
import com.boxverse.app.ui.components.rememberCaptureLayer
import com.boxverse.app.ui.editor.CONDITIONS
import com.boxverse.app.ui.navigation.AppNav
import com.boxverse.app.ui.navigation.LocalContainer
import com.boxverse.app.ui.navigation.boxViewModel
import com.boxverse.app.util.ShareUtil
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TradeEntry(val row: FigureRow, val listing: TradeListing?)
data class TradeData(val duplicates: List<TradeEntry>, val listed: List<TradeEntry>, val nickname: String)

class TradeViewModel(private val repo: BoxRepository) : ViewModel() {
    val data: StateFlow<TradeData?> = combine(repo.figureRows, repo.trades, repo.profile) { figures, trades, profile ->
        val byFigure = trades.associateBy { it.figureId }
        val rows = figures.associateBy { it.figure.id }
        TradeData(
            duplicates = figures.filter { it.ownedQty >= 2 }.map { TradeEntry(it, byFigure[it.figure.id]) },
            listed = trades.mapNotNull { t -> rows[t.figureId]?.let { TradeEntry(it, t) } },
            nickname = profile?.nickname ?: "潮玩收藏家",
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun save(listing: TradeListing) = viewModelScope.launch { repo.saveTrade(listing) }
    fun delete(figureId: Long) = viewModelScope.launch { repo.deleteTrade(figureId) }
}

@Composable
fun TradeScreen(nav: AppNav) {
    val vm = boxViewModel { TradeViewModel(it.repo) }
    val data by vm.data.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<TradeEntry?>(null) }
    var sharing by remember { mutableStateOf<List<TradeEntry>?>(null) }
    val d = data

    SoftBackground(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("交换中心", style = MaterialTheme.typography.headlineMedium)
                Text("整理重复款，生成精美交换卡分享给同好", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item { PillTabs(listOf("重复款 ${d?.duplicates?.size ?: 0}", "我的交换卡 ${d?.listed?.size ?: 0}"), tab, { tab = it }, Modifier.fillMaxWidth()) }
            when {
                d == null -> item { LoadingState() }
                tab == 0 && d.duplicates.isEmpty() -> item {
                    EmptyState(Icons.Rounded.SwapHoriz, "暂时没有重复款", "同一款式持有 2 件及以上时，会自动出现在这里，方便你设置交换。")
                }
                tab == 0 -> items(d.duplicates, key = { it.row.figure.id }) { e -> TradeRow(e) { editing = e } }
                d.listed.isEmpty() -> item {
                    EmptyState(Icons.Rounded.SwapHoriz, "还没有交换卡", "在「重复款」中为想换出的款式设置交换信息即可生成交换卡。")
                }
                else -> {
                    item {
                        GradientButton("生成交换卡片并分享", Modifier.fillMaxWidth(), icon = Icons.Rounded.Share) { sharing = d.listed }
                    }
                    items(d.listed, key = { "l${it.row.figure.id}" }) { e -> TradeRow(e) { editing = e } }
                }
            }
            item { CommunityPlanCard() }
        }
    }

    editing?.let { e ->
        TradeEditSheet(e, onDismiss = { editing = null }, onSave = {
            vm.save(it); editing = null
        }, onDelete = {
            vm.delete(e.row.figure.id); editing = null
        }, onShare = { sharing = listOf(e.copy(listing = it)); editing = null })
    }
    sharing?.let { list -> TradeShareDialog(list, d?.nickname ?: "", onDismiss = { sharing = null }) }
}

@Composable
private fun TradeRow(e: TradeEntry, onClick: () -> Unit) {
    val f = e.row.figure
    BoxCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(12.dp), elevation = 3.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RarityFrame(f.rarityEnum, Modifier.size(66.dp), shape = RoundedCornerShape(16.dp)) {
                FigureArt(f.artSeed, f.colorA, f.colorB, f.rarityEnum, Modifier.fillMaxSize().padding(3.dp), imagePath = f.imagePath)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(f.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(6.dp)); RarityBadge(f.rarityEnum)
                    if (e.listing?.isDemo == true) {
                        Spacer(Modifier.width(4.dp)); DemoTag()
                    }
                }
                Text(e.row.seriesName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (e.listing != null) {
                    Text("想换：${e.listing.wantText.ifBlank { "可议" }}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("持有 ${e.row.ownedQty}", style = MaterialTheme.typography.labelLarge)
                if (e.listing != null) Pill("可换 ${e.listing.tradeQuantity}") else Text("设置交换 ›", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun TradeEditSheet(
    e: TradeEntry,
    onDismiss: () -> Unit,
    onSave: (TradeListing) -> Unit,
    onDelete: () -> Unit,
    onShare: (TradeListing) -> Unit,
) {
    val maxQty = (e.row.ownedQty - 1).coerceAtLeast(1)
    val base = e.listing
    var qty by remember { mutableIntStateOf((base?.tradeQuantity ?: 1).coerceIn(1, maxQty)) }
    var want by remember { mutableStateOf(base?.wantText ?: "") }
    var note by remember { mutableStateOf(base?.note ?: "") }
    var condition by remember { mutableStateOf(base?.condition ?: "全新") }
    var hasBox by remember { mutableStateOf(base?.hasBox ?: true) }
    fun build() = TradeListing(e.row.figure.id, qty, want.trim(), note.trim(), condition, hasBox, isDemo = false)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("交换设置 · ${e.row.figure.name}", style = MaterialTheme.typography.titleLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("可交换数量（持有 ${e.row.ownedQty}）", modifier = Modifier.weight(1f))
                QuantityStepper(qty, { qty = it }, max = maxQty)
            }
            BoxTextField(want, { want = it }, "希望交换的款式", placeholder = "例如：同系列隐藏款 / 任意 SR")
            Text("收藏品成色", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CONDITIONS.forEach { c -> ChoiceChip(c, condition == c, { condition = c }) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("带原盒", modifier = Modifier.weight(1f))
                Switch(checked = hasBox, onCheckedChange = { hasBox = it })
            }
            BoxTextField(note, { note = it }, "交换备注", singleLine = false, minLines = 2, placeholder = "面交地点、邮寄方式等")
            GradientButton("保存交换卡", Modifier.fillMaxWidth()) { onSave(build()) }
            Row {
                TextButton(onClick = { onSave(build()); onShare(build()) }, modifier = Modifier.weight(1f)) { Text("保存并分享") }
                if (base != null) TextButton(onClick = onDelete, modifier = Modifier.weight(1f)) { Text("删除交换卡", color = MaterialTheme.colorScheme.error) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TradeShareDialog(list: List<TradeEntry>, nickname: String, onDismiss: () -> Unit) {
    val layer = rememberCaptureLayer()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val container = LocalContainer.current
    Dialog(onDismissRequest = onDismiss) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CaptureBox(layer, Modifier.clip(RoundedCornerShape(28.dp))) {
                Box(Modifier.background(Brush.linearGradient(listOf(Color(0xFFC6B6FF), Color(0xFFFFC9DF), Color(0xFFFFE6A6))))) {
                    StarField(Modifier.matchParentSize())
                    Column(Modifier.padding(18.dp)) {
                        Text("潮玩交换卡", style = MaterialTheme.typography.headlineSmall, color = Color(0xFF2A2340), fontWeight = FontWeight.Black)
                        Text("来自 $nickname", style = MaterialTheme.typography.bodySmall, color = Color(0xFF4A4266))
                        Spacer(Modifier.height(12.dp))
                        list.take(6).forEach { e ->
                            val f = e.row.figure
                            val l = e.listing
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.8f)).padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RarityFrame(f.rarityEnum, Modifier.size(54.dp), shape = RoundedCornerShape(14.dp)) {
                                    FigureArt(f.artSeed, f.colorA, f.colorB, f.rarityEnum, Modifier.fillMaxSize().padding(2.dp), imagePath = f.imagePath)
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("${f.name}  ×${l?.tradeQuantity ?: 1}", style = MaterialTheme.typography.titleSmall, color = Color(0xFF2A2340))
                                    Text("${e.row.seriesName} · ${f.rarityEnum.fullLabel}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF6E6688))
                                    Text("${l?.condition ?: "全新"} · ${if (l?.hasBox != false) "带原盒" else "无原盒"}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF6E6688))
                                    if (!l?.wantText.isNullOrBlank()) Text("想换：${l?.wantText}", style = MaterialTheme.typography.labelMedium, color = Color(0xFF8F7BEF))
                                }
                            }
                        }
                        if (list.size > 6) Text("……还有 ${list.size - 6} 款", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4A4266))
                        Spacer(Modifier.height(10.dp))
                        Text("盒宇宙 BOXVERSE · 你的潮玩收藏宇宙", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4A4266))
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            GradientButton("分享图片", icon = Icons.Rounded.Share) {
                scope.launch {
                    runCatching { ShareUtil.shareBitmap(context, layer.toImageBitmap().asAndroidBitmap(), "我的潮玩交换卡") }
                        .onFailure { container.emit(AppEvent.Message("生成图片失败：${it.message}")) }
                }
            }
        }
    }
}

@Composable
private fun CommunityPlanCard() {
    BoxCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerHigh, elevation = 0.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(Icons.Rounded.Forum, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text("交换广场（第二阶段规划，尚未上线）", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "未来版本计划提供在线发布交换帖、同好私信与社区互动，需要配套的服务器与账号体系。当前版本完全离线，请通过上方「分享交换卡」发送到其他社交 App。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
