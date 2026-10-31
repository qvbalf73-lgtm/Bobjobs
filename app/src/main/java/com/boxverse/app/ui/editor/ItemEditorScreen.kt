package com.boxverse.app.ui.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.boxverse.app.AppEvent
import com.boxverse.app.data.db.CollectionItem
import com.boxverse.app.data.db.Figure
import com.boxverse.app.data.db.FigureRow
import com.boxverse.app.data.repo.BoxRepository
import com.boxverse.app.domain.Currency
import com.boxverse.app.domain.ItemStatus
import com.boxverse.app.domain.Money
import com.boxverse.app.domain.Rarity
import com.boxverse.app.ui.components.BoxCard
import com.boxverse.app.ui.components.BoxTextField
import com.boxverse.app.ui.components.ChoiceChip
import com.boxverse.app.ui.components.FigureArt
import com.boxverse.app.ui.components.GradientButton
import com.boxverse.app.ui.components.InfoNote
import com.boxverse.app.ui.components.LoadingState
import com.boxverse.app.ui.components.PillTabs
import com.boxverse.app.ui.components.QuantityStepper
import com.boxverse.app.ui.components.RarityBadge
import com.boxverse.app.ui.components.RarityFrame
import com.boxverse.app.ui.components.SelectField
import com.boxverse.app.ui.components.StarRating
import com.boxverse.app.ui.components.SubScreen
import com.boxverse.app.ui.components.SuggestField
import com.boxverse.app.ui.navigation.AppNav
import com.boxverse.app.ui.navigation.LocalContainer
import com.boxverse.app.ui.navigation.boxViewModel
import com.boxverse.app.ui.pokedex.SearchBar
import com.boxverse.app.util.ArtPalette
import com.boxverse.app.util.Dates
import com.boxverse.app.util.ImageStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

val CHANNELS = listOf("线下门店", "官方小程序", "电商平台", "二手平台", "朋友交换", "海外门店", "展会活动")
val CONDITIONS = listOf("全新未拆", "全新", "全新拆盒", "轻微瑕疵", "明显瑕疵")

class ItemEditorViewModel(
    private val repo: BoxRepository,
    private val itemId: Long,
    initialFigureId: Long,
    private val wishId: Long,
) : ViewModel() {
    val figures: StateFlow<List<FigureRow>> = repo.figureRows.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val brands = repo.brands.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val seriesRows = repo.seriesRows.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    var loading by mutableStateOf(true)
    var saving by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    // 款式
    var newFigureMode by mutableStateOf(false)
    var figureId by mutableStateOf(initialFigureId)
    var brandName by mutableStateOf("")
    var ipName by mutableStateOf("")
    var seriesName by mutableStateOf("")
    var figureName by mutableStateOf("")
    var figureCode by mutableStateOf("")
    var rarity by mutableStateOf(Rarity.R)
    var year by mutableStateOf(LocalDate.now().year.toString())

    // 实物
    val photos = mutableStateListOf<String>()
    var quantity by mutableStateOf(1)
    var unitPrice by mutableStateOf("")
    var currency by mutableStateOf("CNY")
    var extraCost by mutableStateOf("")
    var acquiredDay by mutableStateOf(Dates.today())
    var channel by mutableStateOf("")
    var estimated by mutableStateOf("")
    var status by mutableStateOf(ItemStatus.OWNED)
    var condition by mutableStateOf("全新")
    var hasBox by mutableStateOf(true)
    var rating by mutableStateOf(0)
    var story by mutableStateOf("")
    private var existing: CollectionItem? = null

    val isEdit get() = itemId != 0L

    init {
        viewModelScope.launch {
            currency = repo.settings.current().baseCurrency
            if (itemId != 0L) {
                repo.getItem(itemId)?.let { i ->
                    existing = i
                    figureId = i.figureId
                    photos.addAll(i.photos)
                    quantity = i.quantity
                    unitPrice = Money.toInput(i.unitPrice)
                    currency = i.currency
                    extraCost = if (i.extraCost == 0L) "" else Money.toInput(i.extraCost)
                    acquiredDay = i.acquiredEpochDay
                    channel = i.channel
                    estimated = Money.toInput(i.estimatedValue)
                    status = i.statusEnum
                    condition = i.condition
                    hasBox = i.hasBox
                    rating = i.rating
                    story = i.story
                } ?: run { error = "记录不存在" }
            } else if (wishId != 0L) {
                repo.getWish(wishId)?.let { w ->
                    if (w.figureId != null) figureId = w.figureId else {
                        newFigureMode = true
                        figureName = w.name
                        seriesName = w.seriesName
                        brandName = w.brandName
                    }
                    quantity = w.desiredQuantity.coerceAtLeast(1)
                    w.targetPrice?.let { unitPrice = Money.toInput(it) }
                    currency = w.currency
                }
            } else if (initialFigureId == 0L) {
                newFigureMode = false
            }
            loading = false
        }
    }

    fun selectedFigure(): FigureRow? = figures.value.firstOrNull { it.figure.id == figureId }

    fun addPhoto(name: String) {
        if (photos.size < 9) photos.add(name)
    }

    fun save(onDone: () -> Unit) {
        error = null
        val price = if (unitPrice.isBlank()) 0L else Money.parse(unitPrice)
        val extra = if (extraCost.isBlank()) 0L else Money.parse(extraCost)
        val est = if (estimated.isBlank()) null else Money.parse(estimated)
        when {
            !newFigureMode && figureId == 0L -> error = "请选择一个款式，或切换到「新建款式」"
            newFigureMode && (brandName.isBlank() || seriesName.isBlank() || figureName.isBlank()) -> error = "请填写品牌、系列和款式名称"
            newFigureMode && year.toIntOrNull() == null -> error = "发售年份格式不正确"
            price == null -> error = "购买单价格式不正确"
            extra == null -> error = "其他成本格式不正确"
            estimated.isNotBlank() && est == null -> error = "估算价格格式不正确"
            status != ItemStatus.SOLD && quantity < 1 -> error = "数量至少为 1"
        }
        if (error != null) return
        saving = true
        viewModelScope.launch {
            runCatching {
                val fid = if (newFigureMode) {
                    val sid = repo.findOrCreateSeries(brandName, ipName, seriesName, year.toInt())
                    val seed = (System.currentTimeMillis() % 100_000).toInt()
                    val (a, b) = ArtPalette.pick(seed)
                    repo.saveFigure(Figure(seriesId = sid, name = figureName.trim(), code = figureCode.trim(), rarity = rarity.name, artSeed = seed, colorA = a, colorB = b))
                } else figureId
                val base = existing
                val item = (base ?: CollectionItem(figureId = fid, acquiredEpochDay = acquiredDay)).copy(
                    figureId = fid,
                    quantity = quantity,
                    unitPrice = price ?: 0L,
                    currency = currency,
                    extraCost = extra ?: 0L,
                    acquiredEpochDay = acquiredDay,
                    channel = channel.trim(),
                    estimatedValue = est,
                    status = status.name,
                    condition = condition,
                    hasBox = hasBox,
                    rating = rating,
                    story = story.trim(),
                    photoPaths = photos.joinToString("\n"),
                )
                repo.saveItem(item)
                if (wishId != 0L) repo.getWish(wishId)?.let { repo.saveWish(it.copy(purchased = true, figureId = fid)) }
            }.onSuccess {
                saving = false
                onDone()
            }.onFailure {
                saving = false
                error = "保存失败：${it.message}"
            }
        }
    }
}

@Composable
fun ItemEditorScreen(nav: AppNav, itemId: Long, figureId: Long, wishId: Long) {
    val vm = boxViewModel("item_${itemId}_${figureId}_$wishId") { ItemEditorViewModel(it.repo, itemId, figureId, wishId) }
    val container = LocalContainer.current
    SubScreen(title = if (vm.isEdit) "编辑收藏" else "添加收藏", onBack = nav::back) { padding ->
        if (vm.loading) {
            LoadingState(Modifier.padding(padding))
        } else {
            ItemForm(vm, Modifier.padding(padding)) {
                vm.save {
                    container.haptics.success()
                    container.emit(AppEvent.Message(if (vm.isEdit) "已保存修改" else "已加入收藏 🎉"))
                    nav.back()
                }
            }
        }
    }
}

@Composable
private fun ItemForm(vm: ItemEditorViewModel, modifier: Modifier, onSave: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val figures by vm.figures.collectAsStateWithLifecycle()
    val brands by vm.brands.collectAsStateWithLifecycle()
    val seriesRows by vm.seriesRows.collectAsStateWithLifecycle()
    var showPicker by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { ImageStore.import(context, uri)?.let { vm.addPhoto(it) } }
    }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri
        if (ok && uri != null) scope.launch { ImageStore.import(context, uri)?.let { vm.addPhoto(it) } }
    }

    Column(modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 款式
        BoxCard(Modifier.fillMaxWidth()) {
            Text("潮玩款式", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            if (!vm.isEdit) {
                PillTabs(listOf("选择已有款式", "新建款式"), if (vm.newFigureMode) 1 else 0, { vm.newFigureMode = it == 1 }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
            }
            if (!vm.newFigureMode) {
                val sel = vm.selectedFigure()
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable { showPicker = true }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (sel != null) {
                        RarityFrame(sel.figure.rarityEnum, Modifier.size(56.dp), shape = RoundedCornerShape(14.dp)) {
                            FigureArt(sel.figure.artSeed, sel.figure.colorA, sel.figure.colorB, sel.figure.rarityEnum, Modifier.fillMaxSize().padding(3.dp), imagePath = sel.figure.imagePath)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(sel.figure.name, style = MaterialTheme.typography.titleSmall)
                            Text("${sel.brandName} · ${sel.seriesName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("更换", color = MaterialTheme.colorScheme.primary)
                    } else {
                        Icon(Icons.Rounded.SwapHoriz, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text("点击从图鉴中选择款式", color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SuggestField(vm.brandName, { vm.brandName = it }, "品牌 *", brands.map { it.name }.distinct())
                    SuggestField(vm.ipName, { vm.ipName = it }, "IP 名称（可选）", emptyList())
                    SuggestField(vm.seriesName, { vm.seriesName = it }, "系列名称 *", seriesRows.filter { vm.brandName.isBlank() || it.brandName == vm.brandName }.map { it.series.name }.distinct())
                    BoxTextField(vm.figureName, { vm.figureName = it }, "款式 / 角色名称 *")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BoxTextField(vm.figureCode, { vm.figureCode = it }, "编号", Modifier.weight(1f), placeholder = "No.01")
                        BoxTextField(vm.year, { vm.year = it }, "发售年份", Modifier.weight(1f), keyboardType = KeyboardType.Number)
                    }
                    Text("稀有度", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Rarity.entries.forEach { r -> ChoiceChip(r.fullLabel, vm.rarity == r, { vm.rarity = r }) }
                    }
                    Text("若该品牌/系列已存在会自动归入，否则自动新建。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // 图片
        BoxCard(Modifier.fillMaxWidth()) {
            Text("实拍图片", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    PhotoButton(Icons.Rounded.PhotoCamera, "拍照") {
                        val uri = ImageStore.newCameraUri(context)
                        cameraUri = uri
                        takePicture.launch(uri)
                    }
                }
                item {
                    PhotoButton(Icons.Rounded.PhotoLibrary, "相册") {
                        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                }
                items(vm.photos.toList()) { p ->
                    Box(Modifier.size(84.dp)) {
                        FigureArt(0, 0, 0, Rarity.R, Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)), imagePath = p)
                        Box(
                            Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).clickable { vm.photos.remove(p) },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.Close, "移除", tint = Color.White, modifier = Modifier.size(14.dp)) }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            InfoNote("智能识别（AI 自动识别名称与系列）将在后续版本通过安全的后端服务提供，识别结果需要你核对确认。当前版本请拍照或从相册导入后手动填写。", icon = Icons.Rounded.AutoAwesome)
        }

        // 购买信息
        BoxCard(Modifier.fillMaxWidth()) {
            Text("购买信息", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("数量", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                QuantityStepper(vm.quantity, { vm.quantity = it }, min = if (vm.status == ItemStatus.SOLD) 0 else 1)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BoxTextField(vm.unitPrice, { vm.unitPrice = it }, "购买单价", Modifier.weight(1.4f), keyboardType = KeyboardType.Decimal)
                SelectField("币种", Currency.of(vm.currency), Currency.entries.toList(), { "${it.code} ${it.symbol}" }, { vm.currency = it.code }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            BoxTextField(vm.extraCost, { vm.extraCost = it }, "物流及其他成本（本条合计，可选）", keyboardType = KeyboardType.Decimal, suffix = vm.currency)
            Spacer(Modifier.height(8.dp))
            BoxTextField(vm.estimated, { vm.estimated = it }, "当前估算单价（手动填写，可选）", keyboardType = KeyboardType.Decimal, suffix = vm.currency)
            Text("估值仅为你自己的判断，App 不会联网获取或虚构市场价格。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = Dates.formatCn(vm.acquiredDay),
                onValueChange = {},
                readOnly = true,
                label = { Text("获得日期") },
                trailingIcon = { Icon(Icons.Rounded.CalendarMonth, null) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().clickable { showDate = true },
                enabled = false,
            )
            TextButton(onClick = { showDate = true }) { Text("选择日期") }
            Text("购买渠道", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CHANNELS.forEach { c -> ChoiceChip(c, vm.channel == c, { vm.channel = if (vm.channel == c) "" else c }) }
            }
            Spacer(Modifier.height(8.dp))
            BoxTextField(vm.channel, { vm.channel = it }, "或自定义渠道")
        }

        // 收藏状态
        BoxCard(Modifier.fillMaxWidth()) {
            Text("收藏状态", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ItemStatus.entries.filter { it != ItemStatus.SOLD || vm.isEdit }.forEach { s -> ChoiceChip(s.label, vm.status == s, { vm.status = s }) }
            }
            Spacer(Modifier.height(12.dp))
            Text("成色", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CONDITIONS.forEach { c -> ChoiceChip(c, vm.condition == c, { vm.condition = c }) }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("带原盒 / 原卡", modifier = Modifier.weight(1f))
                Switch(checked = vm.hasBox, onCheckedChange = { vm.hasBox = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("个人评分", modifier = Modifier.weight(1f))
                StarRating(vm.rating, onChange = { vm.rating = it })
            }
            Spacer(Modifier.height(8.dp))
            BoxTextField(vm.story, { vm.story = it }, "收藏故事与备注", singleLine = false, minLines = 3)
        }

        vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        GradientButton(if (vm.saving) "保存中…" else "保存", Modifier.fillMaxWidth(), enabled = !vm.saving, onClick = onSave)
        Spacer(Modifier.height(24.dp))
    }

    if (showPicker) FigurePickerSheet(figures, onDismiss = { showPicker = false }) {
        vm.figureId = it
        showPicker = false
    }

    if (showDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = vm.acquiredDay * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { vm.acquiredDay = it / 86_400_000L }
                    showDate = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("取消") } },
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun PhotoButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.size(84.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primaryContainer).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

/** 款式选择底部弹窗 */
@Composable
fun FigurePickerSheet(figures: List<FigureRow>, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var q by remember { mutableStateOf("") }
    val filtered = remember(q, figures) {
        figures.filter { q.isBlank() || it.figure.name.contains(q, true) || it.seriesName.contains(q, true) || it.brandName.contains(q, true) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text("选择款式", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(10.dp))
        SearchBar(q, { q = it }, "搜索款式、系列或品牌")
        Spacer(Modifier.height(8.dp))
        if (filtered.isEmpty()) {
            Text("没有找到匹配的款式，可以返回切换到「新建款式」。", modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(Modifier.fillMaxWidth().height(460.dp), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.figure.id }) { r ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onPick(r.figure.id) }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RarityFrame(r.figure.rarityEnum, Modifier.size(48.dp), owned = r.ownedQty > 0, shape = RoundedCornerShape(12.dp)) {
                        FigureArt(r.figure.artSeed, r.figure.colorA, r.figure.colorB, r.figure.rarityEnum, Modifier.fillMaxSize().padding(2.dp), imagePath = r.figure.imagePath, silhouette = r.ownedQty == 0, showLock = false)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.figure.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${r.brandName} · ${r.seriesName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    RarityBadge(r.figure.rarityEnum)
                    if (r.ownedQty > 0) Text("  ×${r.ownedQty}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
