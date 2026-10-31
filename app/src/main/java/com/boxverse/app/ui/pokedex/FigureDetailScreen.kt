package com.boxverse.app.ui.pokedex

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.boxverse.app.AppEvent
import com.boxverse.app.data.db.CollectionItem
import com.boxverse.app.data.db.CollectionTransaction
import com.boxverse.app.data.db.FigureRow
import com.boxverse.app.data.db.WishlistItem
import com.boxverse.app.data.repo.BoxRepository
import com.boxverse.app.domain.Money
import com.boxverse.app.domain.Rarity
import com.boxverse.app.domain.StatsCalculator
import com.boxverse.app.domain.TxType
import com.boxverse.app.ui.components.BoxCard
import com.boxverse.app.ui.components.BoxTextField
import com.boxverse.app.ui.components.ConfirmDialog
import com.boxverse.app.ui.components.DemoTag
import com.boxverse.app.ui.components.EmptyState
import com.boxverse.app.ui.components.FigureArt
import com.boxverse.app.ui.components.GradientButton
import com.boxverse.app.ui.components.KeyValueRow
import com.boxverse.app.ui.components.LoadingState
import com.boxverse.app.ui.components.Pill
import com.boxverse.app.ui.components.QuantityStepper
import com.boxverse.app.ui.components.RarityAura
import com.boxverse.app.ui.components.RarityBadge
import com.boxverse.app.ui.components.RarityFrame
import com.boxverse.app.ui.components.StarRating
import com.boxverse.app.ui.components.SubScreen
import com.boxverse.app.ui.components.TonalAction
import com.boxverse.app.ui.navigation.AppNav
import com.boxverse.app.ui.navigation.LocalContainer
import com.boxverse.app.ui.navigation.boxViewModel
import com.boxverse.app.ui.theme.LocalBoxColors
import com.boxverse.app.util.Dates
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FigureDetailData(
    val row: FigureRow,
    val items: List<CollectionItem>,
    val sales: List<CollectionTransaction>,
    val wish: WishlistItem?,
)

class FigureDetailViewModel(private val repo: BoxRepository, private val figureId: Long) : ViewModel() {
    val data: StateFlow<Result<FigureDetailData>?> = combine(repo.figureRows, repo.holdingRows, repo.transactions, repo.wishlist) { figures, holdings, txs, wishes ->
        val row = figures.firstOrNull { it.figure.id == figureId }
            ?: return@combine Result.failure<FigureDetailData>(IllegalStateException("款式不存在或已被删除"))
        Result.success(
            FigureDetailData(
                row = row,
                items = holdings.filter { it.item.figureId == figureId }.map { it.item },
                sales = txs.filter { it.figureId == figureId && it.typeEnum == TxType.SELL },
                wish = wishes.firstOrNull { it.figureId == figureId && !it.purchased },
            )
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun deleteItem(item: CollectionItem) = viewModelScope.launch { repo.deleteItem(item) }

    fun deleteFigure(onDone: () -> Unit) {
        val f = data.value?.getOrNull()?.row?.figure ?: return
        viewModelScope.launch {
            repo.deleteFigure(f)
            onDone()
        }
    }

    fun toggleWish() {
        val d = data.value?.getOrNull() ?: return
        viewModelScope.launch {
            if (d.wish != null) repo.deleteWish(d.wish)
            else repo.saveWish(
                WishlistItem(
                    figureId = d.row.figure.id, name = d.row.figure.name, seriesName = d.row.seriesName, brandName = d.row.brandName,
                )
            )
        }
    }

    fun sell(item: CollectionItem, qty: Int, price: Long, fee: Long, platform: String, onDone: (Long) -> Unit) {
        viewModelScope.launch {
            val r = repo.sellItem(item.id, qty, price, fee, Dates.today(), platform, "")
            onDone(r.profit)
        }
    }
}

@Composable
fun FigureDetailScreen(nav: AppNav, figureId: Long) {
    val vm = boxViewModel("figure_$figureId") { FigureDetailViewModel(it.repo, figureId) }
    val result by vm.data.collectAsStateWithLifecycle()
    val d = result?.getOrNull()
    var confirmDeleteFigure by remember { mutableStateOf(false) }

    SubScreen(
        title = d?.row?.figure?.name ?: "款式详情",
        onBack = nav::back,
        actions = {
            if (d != null) {
                IconButton(onClick = { vm.toggleWish() }) {
                    Icon(if (d.wish != null) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (d.wish != null) "移出心愿单" else "加入心愿单", tint = MaterialTheme.colorScheme.secondary)
                }
                IconButton(onClick = { nav.figureEdit(figureId = figureId) }) { Icon(Icons.Rounded.Edit, "编辑款式") }
                IconButton(onClick = { confirmDeleteFigure = true }) { Icon(Icons.Rounded.Delete, "删除款式") }
            }
        },
    ) { padding ->
        val r = result
        when {
            r == null -> LoadingState(Modifier.padding(padding))
            r.isFailure -> EmptyState(Icons.Rounded.HelpOutline, "款式不存在", r.exceptionOrNull()?.message ?: "", Modifier.padding(padding), "返回", nav::back)
            d != null -> FigureDetailContent(d, vm, nav, Modifier.padding(padding))
        }
    }

    if (confirmDeleteFigure && d != null) {
        ConfirmDialog(
            title = "删除款式？",
            message = "将同时删除该款式的 ${d.items.size} 条实物记录、交易记录、虚拟藏品与开盒记录，且无法恢复。",
            confirmLabel = "删除",
            danger = true,
            onConfirm = { vm.deleteFigure { nav.back() } },
            onDismiss = { confirmDeleteFigure = false },
        )
    }
}

@Composable
private fun FigureDetailContent(d: FigureDetailData, vm: FigureDetailViewModel, nav: AppNav, modifier: Modifier) {
    val f = d.row.figure
    val rarity = f.rarityEnum
    val owned = d.row.ownedQty > 0
    val extra = LocalBoxColors.current
    val container = LocalContainer.current
    var viewer by remember { mutableStateOf<String?>(null) }
    var showFigureViewer by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<CollectionItem?>(null) }
    var selling by remember { mutableStateOf<CollectionItem?>(null) }
    val gallery = remember(d) { listOfNotNull(f.imagePath) + d.items.flatMap { it.photos } }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Box(Modifier.fillMaxWidth().aspectRatio(1.15f), contentAlignment = Alignment.Center) {
                if (owned && rarity.order >= 1) RarityAura(rarity, Modifier.fillMaxSize())
                RarityFrame(rarity, Modifier.fillMaxWidth(0.72f).aspectRatio(0.9f).clickable { showFigureViewer = true }, owned = owned, shape = RoundedCornerShape(32.dp)) {
                    FigureArt(f.artSeed, f.colorA, f.colorB, rarity, Modifier.fillMaxSize().padding(14.dp), imagePath = f.imagePath, silhouette = !owned)
                }
            }
        }
        if (gallery.isNotEmpty()) {
            item {
                Text("图片画廊", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(gallery) { path ->
                        RarityFrame(rarity, Modifier.size(86.dp).clickable { viewer = path }, shape = RoundedCornerShape(16.dp)) {
                            FigureArt(f.artSeed, f.colorA, f.colorB, rarity, Modifier.fillMaxSize(), imagePath = path)
                        }
                    }
                }
            }
        }
        item {
            BoxCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(f.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(8.dp))
                    RarityBadge(rarity, showLabel = true)
                    if (f.isDemo) {
                        Spacer(Modifier.width(6.dp)); DemoTag()
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "${d.row.seriesName} ›",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { nav.series(f.seriesId) }.padding(vertical = 2.dp),
                )
                Spacer(Modifier.height(8.dp))
                KeyValueRow("编号", f.code.ifBlank { "—" })
                KeyValueRow("款式类型", rarity.styleType, valueColor = if (rarity.isHidden) extra.gold else MaterialTheme.colorScheme.onSurface)
                KeyValueRow("品牌", d.row.brandName)
                KeyValueRow("IP", d.row.ipName)
                KeyValueRow("发售年份", "${d.row.releaseYear}")
                KeyValueRow("实物持有", if (owned) "${d.row.ownedQty} 件" else "未收藏", valueColor = if (owned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                if (d.row.virtualQty > 0) KeyValueRow("虚拟收藏", "${d.row.virtualQty} 个（不计入实物与资产）")
                if (f.description.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(f.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("实物收藏记录（${d.items.size}）", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                GradientButton("添加实物", icon = Icons.Rounded.Add) { nav.itemEdit(figureId = f.id) }
            }
        }
        if (d.items.isEmpty()) {
            item {
                BoxCard(Modifier.fillMaxWidth()) {
                    Text("还没有这款的实物记录。入手后点击「添加实物」记录价格、渠道和收藏故事。", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        items(d.items, key = { it.id }) { item ->
            ItemRecordCard(
                item = item,
                onEdit = { nav.itemEdit(itemId = item.id) },
                onSell = { selling = item },
                onDelete = { deleting = item },
                onPhoto = { viewer = it },
            )
        }
        if (d.sales.isNotEmpty()) {
            item { Text("售出记录", style = MaterialTheme.typography.titleMedium) }
            items(d.sales, key = { "tx${it.id}" }) { tx ->
                val profit = tx.unitPrice * tx.quantity - tx.fee - tx.costBasis
                BoxCard(Modifier.fillMaxWidth(), elevation = 1.dp, contentPadding = PaddingValues(12.dp)) {
                    KeyValueRow("${Dates.format(tx.epochDay)} · 售出 ${tx.quantity} 件", Money.format(tx.unitPrice * tx.quantity, tx.currency))
                    KeyValueRow("成本 / 手续费", "${Money.format(tx.costBasis, tx.currency)} / ${Money.format(tx.fee, tx.currency)}")
                    KeyValueRow("已实现盈亏", Money.format(profit, tx.currency, showSign = true), valueColor = if (profit >= 0) extra.success else extra.danger)
                }
            }
        }
    }

    if (showFigureViewer) {
        ImageViewer(onDismiss = { showFigureViewer = false }) {
            FigureArt(f.artSeed, f.colorA, f.colorB, rarity, Modifier.fillMaxSize(), imagePath = f.imagePath, silhouette = !owned)
        }
    }
    viewer?.let { path ->
        ImageViewer(onDismiss = { viewer = null }) {
            FigureArt(f.artSeed, f.colorA, f.colorB, rarity, Modifier.fillMaxSize(), imagePath = path)
        }
    }
    deleting?.let { item ->
        ConfirmDialog(
            title = "删除这条实物记录？",
            message = "数量 ${item.quantity} 件，购入价 ${Money.format(item.unitPrice, item.currency)}。相关的购入/售出交易记录也会一并删除。",
            confirmLabel = "删除",
            danger = true,
            onConfirm = { vm.deleteItem(item) },
            onDismiss = { deleting = null },
        )
    }
    selling?.let { item ->
        SellDialog(item, onDismiss = { selling = null }) { qty, price, fee, platform ->
            vm.sell(item, qty, price, fee, platform) { profit ->
                container.haptics.success()
                container.emit(AppEvent.Message("已记录售出，已实现盈亏 ${Money.format(profit, item.currency, showSign = true)}"))
            }
            selling = null
        }
    }
}

@Composable
private fun ItemRecordCard(
    item: CollectionItem,
    onEdit: () -> Unit,
    onSell: () -> Unit,
    onDelete: () -> Unit,
    onPhoto: (String) -> Unit,
) {
    val extra = LocalBoxColors.current
    BoxCard(Modifier.fillMaxWidth(), elevation = 3.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("×${item.quantity}", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(Dates.formatCn(item.acquiredEpochDay), style = MaterialTheme.typography.titleSmall)
                Text(item.channel.ifBlank { "未填写渠道" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Pill(item.statusEnum.label)
            if (item.isDemo) {
                Spacer(Modifier.width(4.dp)); DemoTag()
            }
        }
        Spacer(Modifier.height(8.dp))
        KeyValueRow("购买单价", Money.format(item.unitPrice, item.currency))
        if (item.extraCost > 0) KeyValueRow("物流及其他成本", Money.format(item.extraCost, item.currency))
        KeyValueRow("估算单价（手动）", item.estimatedValue?.let { Money.format(it, item.currency) } ?: "未填写", valueColor = if (item.estimatedValue != null) extra.gold else MaterialTheme.colorScheme.onSurfaceVariant)
        KeyValueRow("成色 / 原盒", "${item.condition} · ${if (item.hasBox) "带原盒" else "无原盒"}")
        if (item.rating > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("个人评分", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                StarRating(item.rating, size = 18.dp)
            }
        }
        if (item.story.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text("“${item.story}”", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 6, overflow = TextOverflow.Ellipsis)
        }
        if (item.photos.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(item.photos) { p ->
                    FigureArt(0, 0, 0, Rarity.R, Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).clickable { onPhoto(p) }, imagePath = p)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TonalAction("编辑", Icons.Rounded.Edit, Modifier.weight(1f), onEdit)
            if (item.quantity > 0 && item.statusEnum.isHolding) TonalAction("卖出", Icons.Rounded.Sell, Modifier.weight(1f), onSell)
            OutlinedButton(onClick = onDelete, shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Rounded.Delete, "删除", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SellDialog(item: CollectionItem, onDismiss: () -> Unit, onConfirm: (Int, Long, Long, String) -> Unit) {
    var qty by remember { mutableIntStateOf(1) }
    var price by remember { mutableStateOf(Money.toInput(item.estimatedValue ?: item.unitPrice)) }
    var fee by remember { mutableStateOf("") }
    var platform by remember { mutableStateOf("") }
    val priceCents = Money.parse(price)
    val feeCents = if (fee.isBlank()) 0L else Money.parse(fee)
    val preview = if (priceCents != null && feeCents != null) StatsCalculator.sell(item.quantity, item.unitPrice, item.extraCost, qty, priceCents, feeCents) else null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("记录售出") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("售出数量", modifier = Modifier.weight(1f))
                    QuantityStepper(qty, { qty = it }, max = item.quantity)
                }
                BoxTextField(price, { price = it }, "成交单价", keyboardType = KeyboardType.Decimal, suffix = item.currency, error = if (priceCents == null) "请输入有效金额" else null)
                BoxTextField(fee, { fee = it }, "手续费 / 运费（可选）", keyboardType = KeyboardType.Decimal, suffix = item.currency, error = if (feeCents == null) "请输入有效金额" else null)
                BoxTextField(platform, { platform = it }, "出售平台（可选）")
                if (preview != null) {
                    Text(
                        "成本 ${Money.format(preview.costBasis, item.currency)} · 预计盈亏 ${Money.format(preview.profit, item.currency, showSign = true)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (preview.profit >= 0) LocalBoxColors.current.success else LocalBoxColors.current.danger,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = priceCents != null && feeCents != null, onClick = { onConfirm(qty, priceCents ?: 0, feeCents ?: 0, platform.trim()) }) { Text("确认售出") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        shape = RoundedCornerShape(28.dp),
    )
}

@Composable
fun ImageViewer(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.8f).clip(RoundedCornerShape(28.dp)).background(Color.Black.copy(alpha = 0.85f)).clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.fillMaxSize().padding(8.dp)) { content() }
        }
    }
}
