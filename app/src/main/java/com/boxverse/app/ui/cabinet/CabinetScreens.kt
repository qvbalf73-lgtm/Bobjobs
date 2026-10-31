package com.boxverse.app.ui.cabinet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.boxverse.app.AppEvent
import com.boxverse.app.data.db.CabinetItem
import com.boxverse.app.data.db.DisplayCabinet
import com.boxverse.app.data.db.FigureRow
import com.boxverse.app.data.repo.BoxRepository
import com.boxverse.app.ui.components.BoxCard
import com.boxverse.app.ui.components.BoxTextField
import com.boxverse.app.ui.components.CaptureBox
import com.boxverse.app.ui.components.ChoiceChip
import com.boxverse.app.ui.components.ConfirmDialog
import com.boxverse.app.ui.components.DemoTag
import com.boxverse.app.ui.components.EmptyState
import com.boxverse.app.ui.components.FigureArt
import com.boxverse.app.ui.components.GradientButton
import com.boxverse.app.ui.components.LoadingState
import com.boxverse.app.ui.components.RarityFrame
import com.boxverse.app.ui.components.StarField
import com.boxverse.app.ui.components.SubScreen
import com.boxverse.app.ui.components.TonalAction
import com.boxverse.app.ui.components.rememberCaptureLayer
import com.boxverse.app.ui.navigation.AppNav
import com.boxverse.app.ui.navigation.LocalContainer
import com.boxverse.app.ui.navigation.boxViewModel
import com.boxverse.app.ui.pokedex.ImageViewer
import com.boxverse.app.util.ShareUtil
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 收藏柜背景主题 */
enum class CabinetTheme(val label: String, val colors: List<Color>, val dark: Boolean) {
    LAVENDER("薰衣草", listOf(Color(0xFFE9E1FF), Color(0xFFD5C8FF)), false),
    SAKURA("樱花粉", listOf(Color(0xFFFFE8F1), Color(0xFFFFC9DF)), false),
    CREAM("奶油白", listOf(Color(0xFFFFF8EE), Color(0xFFF6E7D3)), false),
    GOLD("鎏金", listOf(Color(0xFFFFF1C9), Color(0xFFF2CF7A)), false),
    STARRY("星空", listOf(Color(0xFF221A4A), Color(0xFF4A3480)), true);

    companion object {
        fun of(name: String) = entries.firstOrNull { it.name == name } ?: LAVENDER
    }
}

data class CabinetData(
    val cabinets: List<Pair<DisplayCabinet, List<FigureRow>>>,
    val owned: List<FigureRow>,
)

class CabinetViewModel(private val repo: BoxRepository) : ViewModel() {
    val data: StateFlow<CabinetData?> = combine(repo.cabinets, repo.cabinetItems, repo.figureRows) { cabs, items, figures ->
        val byId = figures.associateBy { it.figure.id }
        CabinetData(
            cabinets = cabs.map { c -> c to items.filter { it.cabinetId == c.id }.sortedBy { it.position }.mapNotNull { byId[it.figureId] } },
            owned = figures.filter { it.ownedQty > 0 },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun save(c: DisplayCabinet, onDone: (Long) -> Unit = {}) = viewModelScope.launch { onDone(repo.saveCabinet(c)) }
    fun delete(c: DisplayCabinet) = viewModelScope.launch { repo.deleteCabinet(c) }
    fun add(cabinetId: Long, ids: List<Long>) = viewModelScope.launch { repo.addToCabinet(cabinetId, ids) }
    fun remove(cabinetId: Long, figureId: Long) = viewModelScope.launch { repo.removeFromCabinet(cabinetId, figureId) }
}

@Composable
fun CabinetListScreen(nav: AppNav) {
    val vm = boxViewModel { CabinetViewModel(it.repo) }
    val data by vm.data.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf<DisplayCabinet?>(null) }
    SubScreen(
        title = "我的收藏室",
        onBack = nav::back,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = DisplayCabinet(name = "") },
                icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("新建收藏柜") },
                containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White, shape = RoundedCornerShape(20.dp),
            )
        },
    ) { padding ->
        val d = data
        when {
            d == null -> LoadingState(Modifier.padding(padding))
            d.cabinets.isEmpty() -> EmptyState(Icons.Rounded.Widgets, "还没有收藏柜", "创建主题收藏柜，把心爱的潮玩摆上展示架，还能生成分享图片。", Modifier.padding(padding), "新建收藏柜") { creating = DisplayCabinet(name = "") }
            else -> LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items(d.cabinets, key = { it.first.id }) { (c, figs) -> CabinetPreview(c, figs) { nav.cabinet(c.id) } }
                item {
                    Text(
                        "当前为 2D 展示柜。3D 收藏室与导入自有 3D 模型计划在后续版本提供。",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    creating?.let { c ->
        CabinetEditDialog(c, onDismiss = { creating = null }) { edited ->
            vm.save(edited) { id -> if (c.id == 0L) nav.cabinet(id) }
            creating = null
        }
    }
}

@Composable
private fun CabinetPreview(c: DisplayCabinet, figs: List<FigureRow>, onClick: () -> Unit) {
    val theme = CabinetTheme.of(c.theme)
    val textColor = if (theme.dark) Color.White else Color(0xFF2A2340)
    Column(
        Modifier.fillMaxWidth().shadow(6.dp, RoundedCornerShape(28.dp)).clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(theme.colors)).clickable(onClick = onClick).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(c.name, style = MaterialTheme.typography.titleLarge, color = textColor)
                Text("${figs.size} 件展品 · ${theme.label} · ${if (c.layout == "GRID") "网格" else "展示架"}", style = MaterialTheme.typography.labelMedium, color = textColor.copy(alpha = 0.75f))
            }
            if (c.isDemo) DemoTag()
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            figs.take(5).forEach { f ->
                FigureArt(f.figure.artSeed, f.figure.colorA, f.figure.colorB, f.figure.rarityEnum, Modifier.size(56.dp), imagePath = f.figure.imagePath)
            }
            if (figs.isEmpty()) Text("空空如也，点击布置展品", color = textColor.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = if (theme.dark) 0.25f else 0.7f)))
    }
}

@Composable
fun CabinetDetailScreen(nav: AppNav, cabinetId: Long) {
    val vm = boxViewModel { CabinetViewModel(it.repo) }
    val data by vm.data.collectAsStateWithLifecycle()
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberCaptureLayer()
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var removeMode by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<FigureRow?>(null) }

    val entry = data?.cabinets?.firstOrNull { it.first.id == cabinetId }
    SubScreen(
        title = entry?.first?.name ?: "收藏柜",
        onBack = nav::back,
        actions = {
            if (entry != null) {
                IconButton(onClick = { editing = true }) { Icon(Icons.Rounded.Edit, "编辑收藏柜") }
                IconButton(onClick = { deleting = true }) { Icon(Icons.Rounded.Delete, "删除收藏柜") }
            }
        },
    ) { padding ->
        val d = data
        if (d == null) {
            LoadingState(Modifier.padding(padding)); return@SubScreen
        }
        if (entry == null) {
            EmptyState(Icons.Rounded.Widgets, "收藏柜不存在", "它可能已被删除", Modifier.padding(padding), "返回", nav::back); return@SubScreen
        }
        val (cabinet, figs) = entry
        val theme = CabinetTheme.of(cabinet.theme)
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CaptureBox(layer, Modifier.clip(RoundedCornerShape(28.dp))) {
                CabinetDisplay(cabinet, figs, theme, removeMode, onTap = { f -> if (removeMode) vm.remove(cabinet.id, f.figure.id) else viewing = f })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TonalAction("布置展品", Icons.Rounded.Add, Modifier.weight(1f)) { adding = true }
                TonalAction(if (removeMode) "完成" else "移除展品", if (removeMode) Icons.Rounded.Check else Icons.Rounded.Close, Modifier.weight(1f)) { removeMode = !removeMode }
            }
            GradientButton("生成分享图片", Modifier.fillMaxWidth(), icon = Icons.Rounded.Share) {
                scope.launch {
                    runCatching { ShareUtil.shareBitmap(context, layer.toImageBitmap().asAndroidBitmap(), "我的收藏柜「${cabinet.name}」") }
                        .onFailure { container.emit(AppEvent.Message("生成图片失败：${it.message}")) }
                }
            }
            if (removeMode) Text("点击展品即可将其移出收藏柜（不会删除收藏记录）", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            if (cabinet.description.isNotBlank()) Text(cabinet.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (adding) {
            AddFiguresSheet(d.owned, figs.map { it.figure.id }.toSet(), onDismiss = { adding = false }) { ids ->
                vm.add(cabinet.id, ids); adding = false
            }
        }
        if (editing) {
            CabinetEditDialog(cabinet, onDismiss = { editing = false }) { vm.save(it); editing = false }
        }
        if (deleting) {
            ConfirmDialog("删除收藏柜？", "只会删除收藏柜本身，柜中的潮玩收藏记录不受影响。", "删除", danger = true, onConfirm = { vm.delete(cabinet); nav.back() }, onDismiss = { deleting = false })
        }
        viewing?.let { f ->
            ImageViewer(onDismiss = { viewing = null }) {
                FigureArt(f.figure.artSeed, f.figure.colorA, f.figure.colorB, f.figure.rarityEnum, Modifier.fillMaxSize(), imagePath = f.figure.imagePath)
            }
        }
    }
}

@Composable
private fun CabinetDisplay(c: DisplayCabinet, figs: List<FigureRow>, theme: CabinetTheme, removeMode: Boolean, onTap: (FigureRow) -> Unit) {
    val textColor = if (theme.dark) Color.White else Color(0xFF2A2340)
    Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(theme.colors))) {
        if (theme.dark) StarField(Modifier.matchParentSize())
        Column(Modifier.padding(16.dp)) {
            Text(c.name, style = MaterialTheme.typography.headlineSmall, color = textColor)
            Text("${figs.size} 件展品 · 盒宇宙 BOXVERSE", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.7f))
            Spacer(Modifier.height(14.dp))
            if (figs.isEmpty()) {
                Text("这里还没有展品，点击下方「布置展品」开始吧。", color = textColor.copy(alpha = 0.75f), modifier = Modifier.padding(vertical = 40.dp))
            } else if (c.layout == "GRID") {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 3) {
                    figs.forEach { f ->
                        Column(Modifier.weight(1f).clickable { onTap(f) }, horizontalAlignment = Alignment.CenterHorizontally) {
                            RarityFrame(f.figure.rarityEnum, Modifier.fillMaxWidth().aspectRatio(0.9f), shape = RoundedCornerShape(18.dp)) {
                                FigureArt(f.figure.artSeed, f.figure.colorA, f.figure.colorB, f.figure.rarityEnum, Modifier.fillMaxSize().padding(4.dp), imagePath = f.figure.imagePath)
                                if (removeMode) RemoveBadge()
                            }
                            Text(f.figure.name, style = MaterialTheme.typography.labelSmall, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    repeat((3 - figs.size % 3) % 3) { Spacer(Modifier.weight(1f)) }
                }
            } else {
                figs.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                        row.forEach { f ->
                            Column(Modifier.weight(1f).clickable { onTap(f) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(contentAlignment = Alignment.TopEnd) {
                                    FigureArt(f.figure.artSeed, f.figure.colorA, f.figure.colorB, f.figure.rarityEnum, Modifier.size(92.dp).offset(y = 6.dp), imagePath = f.figure.imagePath)
                                    if (removeMode) RemoveBadge()
                                }
                                Text(f.figure.name, style = MaterialTheme.typography.labelSmall, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                    // 展示架隔板
                    Box(
                        Modifier.fillMaxWidth().height(12.dp).shadow(4.dp, RoundedCornerShape(6.dp)).clip(RoundedCornerShape(6.dp))
                            .background(Brush.verticalGradient(if (theme.dark) listOf(Color(0xFF6E5AA8), Color(0xFF3E2F70)) else listOf(Color.White, Color(0xFFE8DCCB))))
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun RemoveBadge() {
    Box(Modifier.padding(4.dp).size(22.dp).clip(CircleShape).background(Color(0xFFE5486A)), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.Close, "移除", tint = Color.White, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun CabinetEditDialog(initial: DisplayCabinet, onDismiss: () -> Unit, onSave: (DisplayCabinet) -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var desc by remember { mutableStateOf(initial.description) }
    var theme by remember { mutableStateOf(CabinetTheme.of(initial.theme)) }
    var layout by remember { mutableStateOf(initial.layout) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "新建收藏柜" else "编辑收藏柜") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BoxTextField(name, { name = it }, "收藏柜名称", placeholder = "例如：甜品小屋")
                BoxTextField(desc, { desc = it }, "描述（可选）")
                Text("背景主题", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CabinetTheme.entries.forEach { t ->
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(Brush.linearGradient(t.colors))
                                .border(if (theme == t) 3.dp else 1.dp, if (theme == t) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                .clickable { theme = t }
                        )
                    }
                }
                Text("布局", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip("展示架", layout == "SHELF", { layout = "SHELF" })
                    ChoiceChip("网格", layout == "GRID", { layout = "GRID" })
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(initial.copy(name = name.trim(), description = desc.trim(), theme = theme.name, layout = layout)) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        shape = RoundedCornerShape(28.dp),
    )
}

@Composable
private fun AddFiguresSheet(owned: List<FigureRow>, existing: Set<Long>, onDismiss: () -> Unit, onAdd: (List<Long>) -> Unit) {
    val selected = remember { mutableStateListOf<Long>() }
    val candidates = owned.filter { it.figure.id !in existing }
    val bySeries = candidates.groupBy { it.seriesName }
    val byBrand = candidates.groupBy { it.brandName }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text("布置展品", style = MaterialTheme.typography.titleLarge)
            Text("只能摆放已拥有实物的潮玩", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            if (candidates.isEmpty()) {
                Text("没有可添加的展品了（已拥有的潮玩都在柜中，或还没有实物收藏）。", modifier = Modifier.padding(vertical = 24.dp))
            } else {
                Text("一键按系列 / 品牌布置", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    bySeries.forEach { (name, list) -> ChoiceChip("系列·$name", false, { onAdd(list.map { it.figure.id }) }) }
                    byBrand.forEach { (name, list) -> ChoiceChip("品牌·$name", false, { onAdd(list.map { it.figure.id }) }) }
                }
                Spacer(Modifier.height(12.dp))
                Text("或手动挑选", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                LazyColumn(Modifier.fillMaxWidth().height(320.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(candidates, key = { it.figure.id }) { f ->
                        val sel = f.figure.id in selected
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                .background(if (sel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable { if (sel) selected.remove(f.figure.id) else selected.add(f.figure.id) }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FigureArt(f.figure.artSeed, f.figure.colorA, f.figure.colorB, f.figure.rarityEnum, Modifier.size(44.dp), imagePath = f.figure.imagePath)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(f.figure.name, style = MaterialTheme.typography.titleSmall)
                                Text(f.seriesName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (sel) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                GradientButton("添加所选（${selected.size}）", Modifier.fillMaxWidth(), enabled = selected.isNotEmpty()) { onAdd(selected.toList()) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
