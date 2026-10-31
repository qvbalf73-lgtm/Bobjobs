package com.boxverse.app.ui.pokedex

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.boxverse.app.data.db.FigureRow
import com.boxverse.app.data.db.HoldingRow
import com.boxverse.app.data.db.SeriesRow
import com.boxverse.app.data.repo.BoxRepository
import com.boxverse.app.data.repo.toRef
import com.boxverse.app.domain.ItemStatus
import com.boxverse.app.domain.Money
import com.boxverse.app.domain.Rarity
import com.boxverse.app.domain.SeriesProgress
import com.boxverse.app.domain.StatsCalculator
import com.boxverse.app.ui.components.BoxCard
import com.boxverse.app.ui.components.ChoiceChip
import com.boxverse.app.ui.components.DemoTag
import com.boxverse.app.ui.components.EmptyState
import com.boxverse.app.ui.components.FigureArt
import com.boxverse.app.ui.components.FigureTile
import com.boxverse.app.ui.components.GradientProgressBar
import com.boxverse.app.ui.components.LoadingState
import com.boxverse.app.ui.components.Pill
import com.boxverse.app.ui.components.PillTabs
import com.boxverse.app.ui.components.RarityBadge
import com.boxverse.app.ui.components.RarityFrame
import com.boxverse.app.ui.components.SeriesCover
import com.boxverse.app.ui.components.SoftBackground
import com.boxverse.app.ui.navigation.AppNav
import com.boxverse.app.ui.navigation.boxViewModel
import com.boxverse.app.ui.theme.LocalBoxColors
import com.boxverse.app.util.Dates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class OwnFilter(val label: String) { ALL("全部"), OWNED("已收藏"), MISSING("未收藏"), HIDDEN("隐藏款") }
enum class FigureSort(val label: String) { DEFAULT("默认"), NAME("名称"), RARITY("稀有度"), COUNT("持有数量"), YEAR("发售年份") }
enum class ItemSort(val label: String) { RECENT("最近获得"), PRICE_DESC("价格从高到低"), PRICE_ASC("价格从低到高"), NAME("名称") }

data class PokedexFilters(
    val tab: Int = 0,
    val query: String = "",
    val own: OwnFilter = OwnFilter.ALL,
    val brand: String? = null,
    val figureSort: FigureSort = FigureSort.DEFAULT,
    val itemSort: ItemSort = ItemSort.RECENT,
    val itemStatus: ItemStatus? = null,
)

data class PokedexData(
    val filters: PokedexFilters,
    val brands: List<String>,
    val series: List<Pair<SeriesRow, SeriesProgress?>>,
    val figures: List<FigureRow>,
    val items: List<HoldingRow>,
    val ownedFigures: Int,
    val totalFigures: Int,
)

class PokedexViewModel(repo: BoxRepository) : ViewModel() {
    val filters = MutableStateFlow(PokedexFilters())

    val data: StateFlow<PokedexData?> = combine(filters, repo.seriesRows, repo.figureRows, repo.holdingRows) { f, series, figures, holdings ->
        val q = f.query.trim()
        val ownedIds = figures.filter { it.ownedQty > 0 }.map { it.figure.id }.toSet()
        val progress = StatsCalculator.seriesProgress(figures.map { it.toRef() }, ownedIds)
        val brands = series.map { it.brandName }.distinct().sorted()

        val seriesList = series
            .filter { f.brand == null || it.brandName == f.brand }
            .filter { q.isEmpty() || it.series.name.contains(q, true) || it.brandName.contains(q, true) || it.ipName.contains(q, true) }
            .map { it to progress[it.series.id] }

        val figureList = figures
            .filter { f.brand == null || it.brandName == f.brand }
            .filter {
                when (f.own) {
                    OwnFilter.ALL -> true
                    OwnFilter.OWNED -> it.ownedQty > 0
                    OwnFilter.MISSING -> it.ownedQty == 0
                    OwnFilter.HIDDEN -> it.figure.rarityEnum.isHidden
                }
            }
            .filter { q.isEmpty() || it.figure.name.contains(q, true) || it.seriesName.contains(q, true) || it.brandName.contains(q, true) || it.ipName.contains(q, true) || it.figure.code.contains(q, true) }
            .let { list ->
                when (f.figureSort) {
                    FigureSort.DEFAULT -> list
                    FigureSort.NAME -> list.sortedBy { it.figure.name }
                    FigureSort.RARITY -> list.sortedByDescending { it.figure.rarityEnum.order }
                    FigureSort.COUNT -> list.sortedByDescending { it.ownedQty }
                    FigureSort.YEAR -> list.sortedByDescending { it.releaseYear }
                }
            }

        val itemList = holdings
            .filter { f.brand == null || it.brandName == f.brand }
            .filter { f.itemStatus == null || it.item.statusEnum == f.itemStatus }
            .filter { q.isEmpty() || it.figureName.contains(q, true) || it.seriesName.contains(q, true) || it.brandName.contains(q, true) || it.item.channel.contains(q, true) }
            .let { list ->
                when (f.itemSort) {
                    ItemSort.RECENT -> list
                    ItemSort.PRICE_DESC -> list.sortedByDescending { it.item.unitPrice }
                    ItemSort.PRICE_ASC -> list.sortedBy { it.item.unitPrice }
                    ItemSort.NAME -> list.sortedBy { it.figureName }
                }
            }

        PokedexData(f, brands, seriesList, figureList, itemList, ownedIds.size, figures.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun update(transform: (PokedexFilters) -> PokedexFilters) {
        filters.value = transform(filters.value)
    }
}

@Composable
fun PokedexScreen(nav: AppNav) {
    val vm = boxViewModel { PokedexViewModel(it.repo) }
    val data by vm.data.collectAsStateWithLifecycle()
    val d = data
    SoftBackground(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(Modifier.padding(start = 20.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("潮玩图鉴", style = MaterialTheme.typography.headlineMedium)
                        if (d != null) {
                            Text("已点亮 ${d.ownedFigures} / ${d.totalFigures} 款", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (d != null) SortMenu(d.filters, vm)
                }
                Spacer(Modifier.height(10.dp))
                SearchBar(d?.filters?.query.orEmpty(), { q -> vm.update { it.copy(query = q) } })
                Spacer(Modifier.height(10.dp))
                PillTabs(listOf("系列", "款式", "实物"), d?.filters?.tab ?: 0, { t -> vm.update { it.copy(tab = t) } }, Modifier.padding(horizontal = 16.dp).fillMaxWidth())
                if (d == null) {
                    LoadingState()
                } else {
                    FilterRow(d, vm)
                    when (d.filters.tab) {
                        0 -> SeriesList(d, nav)
                        1 -> FigureGrid(d, nav)
                        else -> ItemList(d, nav)
                    }
                }
            }
            Column(Modifier.align(Alignment.BottomEnd).padding(16.dp), horizontalAlignment = Alignment.End) {
                SmallFloatingActionButton(onClick = { nav.seriesEdit() }, containerColor = MaterialTheme.colorScheme.surface) {
                    Icon(Icons.Rounded.CreateNewFolder, "新建系列", tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(10.dp))
                ExtendedFloatingActionButton(
                    onClick = { nav.itemEdit() },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(20.dp),
                    icon = { Icon(Icons.Rounded.Add, null) },
                    text = { Text("添加收藏") },
                )
            }
        }
    }
}

@Composable
fun SearchBar(value: String, onChange: (String) -> Unit, placeholder: String = "搜索款式、系列、品牌、IP") {
    TextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        trailingIcon = if (value.isNotEmpty()) {
            { IconButton(onClick = { onChange("") }) { Icon(Icons.Rounded.Close, "清除") } }
        } else null,
        singleLine = true,
        shape = RoundedCornerShape(50),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
    )
}

@Composable
private fun SortMenu(f: PokedexFilters, vm: PokedexViewModel) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Rounded.Sort, "排序") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (f.tab == 2) {
                ItemSort.entries.forEach { s ->
                    DropdownMenuItem(text = { Text((if (s == f.itemSort) "✓ " else "") + s.label) }, onClick = {
                        vm.update { it.copy(itemSort = s) }; open = false
                    })
                }
            } else {
                FigureSort.entries.forEach { s ->
                    DropdownMenuItem(text = { Text((if (s == f.figureSort) "✓ " else "") + s.label) }, onClick = {
                        vm.update { it.copy(figureSort = s, tab = if (f.tab == 0) 1 else f.tab) }; open = false
                    })
                }
            }
        }
    }
}

@Composable
private fun FilterRow(d: PokedexData, vm: PokedexViewModel) {
    val f = d.filters
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (f.tab == 1) {
            items(OwnFilter.entries) { o -> ChoiceChip(o.label, f.own == o, { vm.update { it.copy(own = o) } }) }
        }
        if (f.tab == 2) {
            item { ChoiceChip("全部状态", f.itemStatus == null, { vm.update { it.copy(itemStatus = null) } }) }
            items(ItemStatus.entries) { st -> ChoiceChip(st.label, f.itemStatus == st, { vm.update { it.copy(itemStatus = st) } }) }
        }
        item { ChoiceChip("全部品牌", f.brand == null, { vm.update { it.copy(brand = null) } }) }
        items(d.brands) { b -> ChoiceChip(b, f.brand == b, { vm.update { it.copy(brand = b) } }) }
    }
}

@Composable
private fun SeriesList(d: PokedexData, nav: AppNav) {
    if (d.series.isEmpty()) {
        EmptyState(Icons.Rounded.SearchOff, "没有找到系列", "试试其他关键词，或新建一个系列", actionLabel = "新建系列", onAction = { nav.seriesEdit() })
        return
    }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 140.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(d.series, key = { it.first.series.id }) { (row, p) -> SeriesRowCard(row, p) { nav.series(row.series.id) } }
    }
}

@Composable
fun SeriesRowCard(row: SeriesRow, p: SeriesProgress?, onClick: () -> Unit) {
    val extra = LocalBoxColors.current
    BoxCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(12.dp), elevation = 3.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SeriesCover(row.series.themeColor, row.series.accentColor, row.series.name, Modifier.size(84.dp).clip(RoundedCornerShape(18.dp)))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(row.series.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (row.series.isDemo) {
                        Spacer(Modifier.width(6.dp)); DemoTag()
                    }
                }
                Text("${row.brandName} · ${row.ipName} · ${row.series.releaseYear}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GradientProgressBar(p?.fraction ?: 0f, Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text("${p?.owned ?: 0}/${p?.total ?: row.figureCount} · ${p?.percent ?: 0}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                if (p?.isFullComplete == true) {
                    Spacer(Modifier.height(6.dp)); Pill("含隐藏款全收集", color = extra.gold.copy(alpha = 0.2f), contentColor = extra.gold)
                } else if (p?.isComplete == true) {
                    Spacer(Modifier.height(6.dp)); Pill("常规款已集齐", color = extra.success.copy(alpha = 0.18f), contentColor = extra.success)
                }
            }
        }
    }
}

@Composable
private fun FigureGrid(d: PokedexData, nav: AppNav) {
    if (d.figures.isEmpty()) {
        EmptyState(Icons.Rounded.SearchOff, "没有符合条件的款式", "调整筛选条件，或添加新的收藏")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 140.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("彩色为已收藏，灰色剪影为未解锁", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(d.figures, key = { it.figure.id }) { r ->
            FigureTile(
                name = r.figure.name,
                subtitle = r.seriesName,
                rarity = r.figure.rarityEnum,
                seed = r.figure.artSeed,
                colorA = r.figure.colorA,
                colorB = r.figure.colorB,
                imagePath = r.figure.imagePath,
                owned = r.ownedQty > 0,
                count = r.ownedQty,
                isDemo = r.figure.isDemo,
                onClick = { nav.figure(r.figure.id) },
            )
        }
    }
}

@Composable
private fun ItemList(d: PokedexData, nav: AppNav) {
    if (d.items.isEmpty()) {
        EmptyState(Icons.Rounded.Inventory2, "还没有实物收藏", "记录你拥有的每一件潮玩：价格、渠道、故事都能保存", actionLabel = "添加收藏", onAction = { nav.itemEdit() })
        return
    }
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 140.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(d.items, key = { it.item.id }) { h -> HoldingCard(h) { nav.figure(h.item.figureId) } }
    }
}

@Composable
fun HoldingCard(h: HoldingRow, onClick: () -> Unit) {
    val rarity = Rarity.fromName(h.rarity)
    val i = h.item
    BoxCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(10.dp), elevation = 2.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RarityFrame(rarity, Modifier.size(64.dp), shape = RoundedCornerShape(16.dp)) {
                FigureArt(h.artSeed, h.colorA, h.colorB, rarity, Modifier.fillMaxSize().padding(4.dp), imagePath = i.photos.firstOrNull() ?: h.figureImage)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(h.figureName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(6.dp))
                    RarityBadge(rarity)
                    if (i.isDemo) {
                        Spacer(Modifier.width(4.dp)); DemoTag()
                    }
                }
                Text("${h.brandName} · ${h.seriesName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${Dates.format(i.acquiredEpochDay)} · ${i.channel.ifBlank { "未填渠道" }} · ${i.statusEnum.label}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("×${i.quantity}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text(Money.format(i.unitPrice, i.currency), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
