package com.boxverse.app.ui.wishlist

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.boxverse.app.data.db.FigureRow
import com.boxverse.app.data.db.WishlistItem
import com.boxverse.app.data.repo.BoxRepository
import com.boxverse.app.domain.Currency
import com.boxverse.app.domain.Money
import com.boxverse.app.ui.components.BoxCard
import com.boxverse.app.ui.components.BoxTextField
import com.boxverse.app.ui.components.ChoiceChip
import com.boxverse.app.ui.components.ConfirmDialog
import com.boxverse.app.ui.components.DemoTag
import com.boxverse.app.ui.components.EmptyState
import com.boxverse.app.ui.components.FigureArt
import com.boxverse.app.ui.components.GradientButton
import com.boxverse.app.ui.components.LoadingState
import com.boxverse.app.ui.components.Pill
import com.boxverse.app.ui.components.PillTabs
import com.boxverse.app.ui.components.QuantityStepper
import com.boxverse.app.ui.components.RarityFrame
import com.boxverse.app.ui.components.SelectField
import com.boxverse.app.ui.components.SubScreen
import com.boxverse.app.ui.components.TonalAction
import com.boxverse.app.ui.editor.FigurePickerSheet
import com.boxverse.app.ui.navigation.AppNav
import com.boxverse.app.ui.navigation.boxViewModel
import com.boxverse.app.ui.theme.LocalBoxColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WishData(val wishes: List<WishlistItem>, val figures: Map<Long, FigureRow>, val baseCurrency: String)

class WishlistViewModel(private val repo: BoxRepository) : ViewModel() {
    val data: StateFlow<WishData?> = combine(repo.wishlist, repo.figureRows, repo.settings.settings) { w, f, s ->
        WishData(w, f.associateBy { it.figure.id }, s.baseCurrency)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun save(item: WishlistItem) = viewModelScope.launch { repo.saveWish(item) }
    fun delete(item: WishlistItem) = viewModelScope.launch { repo.deleteWish(item) }
}

private fun priorityLabel(p: Int) = when (p) {
    1 -> "高优先"
    2 -> "中优先"
    else -> "低优先"
}

@Composable
fun WishlistScreen(nav: AppNav) {
    val vm = boxViewModel { WishlistViewModel(it.repo) }
    val data by vm.data.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<WishlistItem?>(null) }
    var deleting by remember { mutableStateOf<WishlistItem?>(null) }
    val d = data

    SubScreen(
        title = "心愿清单",
        onBack = nav::back,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = WishlistItem(name = "", currency = d?.baseCurrency ?: "CNY") },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("添加心愿") },
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = Color.White,
                shape = RoundedCornerShape(20.dp),
            )
        },
    ) { padding ->
        if (d == null) {
            LoadingState(Modifier.padding(padding)); return@SubScreen
        }
        val pending = d.wishes.filter { !it.purchased }
        val done = d.wishes.filter { it.purchased }
        val list = if (tab == 0) pending else done
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 100.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { PillTabs(listOf("想要 ${pending.size}", "已购入 ${done.size}"), tab, { tab = it }, Modifier.fillMaxWidth()) }
            if (tab == 0 && pending.isNotEmpty()) {
                item {
                    val budget = pending.filter { it.currency == d.baseCurrency }.sumOf { (it.targetPrice ?: 0L) * it.desiredQuantity }
                    Text(
                        "按目标价估算预算约 ${Money.format(budget, d.baseCurrency)}" + if (pending.any { it.currency != d.baseCurrency }) "（不含其他币种心愿）" else "",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (list.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Rounded.Favorite,
                        if (tab == 0) "心愿清单是空的" else "还没有已购入的心愿",
                        if (tab == 0) "把想要的潮玩加进来，设置目标价格与优先级，入手后一键转为收藏记录。" else "心愿购入后会出现在这里。",
                    )
                }
            }
            items(list, key = { it.id }) { w ->
                WishCard(
                    w, d.figures[w.figureId ?: -1L],
                    onConvert = { nav.itemEdit(wishId = w.id) },
                    onEdit = { editing = w },
                    onDelete = { deleting = w },
                )
            }
        }
    }

    editing?.let { w ->
        WishEditSheet(w, d?.figures?.values?.toList().orEmpty(), onDismiss = { editing = null }) {
            vm.save(it); editing = null
        }
    }
    deleting?.let { w ->
        ConfirmDialog("删除心愿？", "确定从心愿清单中删除「${w.name}」吗？", "删除", danger = true, onConfirm = { vm.delete(w) }, onDismiss = { deleting = null })
    }
}

@Composable
private fun WishCard(w: WishlistItem, figure: FigureRow?, onConvert: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val extra = LocalBoxColors.current
    val prColor = when (w.priority) {
        1 -> MaterialTheme.colorScheme.secondary
        2 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    BoxCard(Modifier.fillMaxWidth(), elevation = 3.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (figure != null) {
                RarityFrame(figure.figure.rarityEnum, Modifier.size(70.dp), owned = figure.ownedQty > 0, shape = RoundedCornerShape(18.dp)) {
                    FigureArt(figure.figure.artSeed, figure.figure.colorA, figure.figure.colorB, figure.figure.rarityEnum, Modifier.fillMaxSize().padding(3.dp), imagePath = figure.figure.imagePath)
                }
            } else {
                Box(
                    Modifier.size(70.dp).clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(listOf(Color(0xFFFFD3E6), Color(0xFFE6DEFF)))),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Favorite, null, tint = Color(0xFFF59AC4), modifier = Modifier.size(32.dp)) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(w.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (w.isDemo) {
                        Spacer(Modifier.width(6.dp)); DemoTag()
                    }
                }
                Text(listOf(w.brandName, w.seriesName).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "未关联系列" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(priorityLabel(w.priority), color = prColor.copy(alpha = 0.15f), contentColor = prColor)
                    Pill("×${w.desiredQuantity}")
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("目标价", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(w.targetPrice?.let { Money.format(it, w.currency) } ?: "未设置", style = MaterialTheme.typography.titleSmall, color = extra.gold)
            }
        }
        if (w.note.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(w.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!w.purchased) {
                GradientButton("已购入 · 转为收藏", Modifier.weight(1f), icon = Icons.Rounded.ShoppingBag, onClick = onConvert)
            } else {
                Text("✓ 已购入并转为收藏", color = extra.success, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            }
            OutlinedButton(onClick = onEdit, shape = RoundedCornerShape(16.dp)) { Icon(Icons.Rounded.Edit, "编辑", modifier = Modifier.size(18.dp)) }
            OutlinedButton(onClick = onDelete, shape = RoundedCornerShape(16.dp)) { Icon(Icons.Rounded.Delete, "删除", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) }
        }
    }
}

@Composable
private fun WishEditSheet(initial: WishlistItem, figures: List<FigureRow>, onDismiss: () -> Unit, onSave: (WishlistItem) -> Unit) {
    var figureId by remember { mutableStateOf(initial.figureId) }
    var name by remember { mutableStateOf(initial.name) }
    var series by remember { mutableStateOf(initial.seriesName) }
    var brand by remember { mutableStateOf(initial.brandName) }
    var price by remember { mutableStateOf(Money.toInput(initial.targetPrice)) }
    var currency by remember { mutableStateOf(initial.currency) }
    var qty by remember { mutableIntStateOf(initial.desiredQuantity) }
    var priority by remember { mutableIntStateOf(initial.priority) }
    var note by remember { mutableStateOf(initial.note) }
    var purchased by remember { mutableStateOf(initial.purchased) }
    var picking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (initial.id == 0L) "添加心愿" else "编辑心愿", style = MaterialTheme.typography.titleLarge)
            val linked = figures.firstOrNull { it.figure.id == figureId }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable { picking = true }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (linked != null) "已关联图鉴款式：${linked.figure.name}" else "从图鉴选择款式（可选）", color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                if (linked != null) Text("取消关联", color = MaterialTheme.colorScheme.error, modifier = Modifier.clickable { figureId = null })
            }
            BoxTextField(name, { name = it }, "潮玩名称 *")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BoxTextField(brand, { brand = it }, "品牌", Modifier.weight(1f))
                BoxTextField(series, { series = it }, "系列", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BoxTextField(price, { price = it }, "目标价格", Modifier.weight(1.3f), keyboardType = KeyboardType.Decimal)
                SelectField("币种", Currency.of(currency), Currency.entries.toList(), { it.code }, { currency = it.code }, Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("期望数量", modifier = Modifier.weight(1f))
                QuantityStepper(qty, { qty = it })
            }
            Text("优先级", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 2, 3).forEach { p -> ChoiceChip(priorityLabel(p), priority == p, { priority = p }) }
            }
            BoxTextField(note, { note = it }, "心愿备注", singleLine = false, minLines = 2)
            if (initial.id != 0L) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("标记为已购入（不生成收藏记录）", modifier = Modifier.weight(1f))
                    androidx.compose.material3.Switch(checked = purchased, onCheckedChange = { purchased = it })
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            GradientButton("保存", Modifier.fillMaxWidth()) {
                val p = if (price.isBlank()) null else Money.parse(price)
                when {
                    name.isBlank() -> error = "请填写潮玩名称"
                    price.isNotBlank() && p == null -> error = "目标价格格式不正确"
                    else -> onSave(
                        initial.copy(
                            figureId = figureId, name = name.trim(), seriesName = series.trim(), brandName = brand.trim(),
                            targetPrice = p, currency = currency, desiredQuantity = qty, priority = priority, note = note.trim(), purchased = purchased,
                        )
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (picking) {
        FigurePickerSheet(figures, onDismiss = { picking = false }) { id ->
            val r = figures.firstOrNull { it.figure.id == id }
            figureId = id
            if (r != null) {
                name = r.figure.name; series = r.seriesName; brand = r.brandName
            }
            picking = false
        }
    }
}
