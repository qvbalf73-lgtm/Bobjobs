package com.boxverse.app.ui.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.boxverse.app.AppEvent
import com.boxverse.app.data.db.Figure
import com.boxverse.app.data.db.Series
import com.boxverse.app.data.repo.BoxRepository
import com.boxverse.app.domain.BlindBoxEngine
import com.boxverse.app.domain.Currency
import com.boxverse.app.domain.Money
import com.boxverse.app.domain.PoolFigure
import com.boxverse.app.domain.Rarity
import com.boxverse.app.domain.RateConfig
import com.boxverse.app.ui.components.BlindBoxArt
import com.boxverse.app.ui.components.BoxCard
import com.boxverse.app.ui.components.BoxTextField
import com.boxverse.app.ui.components.ChoiceChip
import com.boxverse.app.ui.components.FigureArt
import com.boxverse.app.ui.components.GradientButton
import com.boxverse.app.ui.components.LoadingState
import com.boxverse.app.ui.components.RarityBadge
import com.boxverse.app.ui.components.RarityFrame
import com.boxverse.app.ui.components.SelectField
import com.boxverse.app.ui.components.SubScreen
import com.boxverse.app.ui.components.SuggestField
import com.boxverse.app.ui.components.TonalAction
import com.boxverse.app.ui.navigation.AppNav
import com.boxverse.app.ui.navigation.LocalContainer
import com.boxverse.app.ui.navigation.boxViewModel
import com.boxverse.app.util.ArtPalette
import com.boxverse.app.util.ImageStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.random.Random

/* ======================= 款式编辑 ======================= */

class FigureEditorViewModel(private val repo: BoxRepository, private val figureId: Long, initialSeriesId: Long) : ViewModel() {
    val seriesRows = repo.seriesRows.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    var loading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    var seriesId by mutableStateOf(initialSeriesId)
    var name by mutableStateOf("")
    var code by mutableStateOf("")
    var rarity by mutableStateOf(Rarity.R)
    var description by mutableStateOf("")
    var imagePath by mutableStateOf<String?>(null)
    var seed by mutableStateOf(Random.nextInt(100_000))
    var colors by mutableStateOf(ArtPalette.pick(seed))
    private var existing: Figure? = null
    val isEdit get() = figureId != 0L

    init {
        viewModelScope.launch {
            if (figureId != 0L) {
                repo.getFigure(figureId)?.let { f ->
                    existing = f
                    seriesId = f.seriesId
                    name = f.name
                    code = f.code
                    rarity = f.rarityEnum
                    description = f.description
                    imagePath = f.imagePath
                    seed = f.artSeed
                    colors = f.colorA to f.colorB
                } ?: run { error = "款式不存在" }
            } else if (seriesId != 0L) {
                val count = repo.figuresOfSeries(seriesId).size
                code = "No.%02d".format(count + 1)
            }
            loading = false
        }
    }

    fun save(onDone: () -> Unit) {
        error = when {
            seriesId == 0L -> "请选择所属系列"
            name.isBlank() -> "请填写款式名称"
            else -> null
        }
        if (error != null) return
        viewModelScope.launch {
            val f = (existing ?: Figure(seriesId = seriesId, name = name)).copy(
                seriesId = seriesId, name = name.trim(), code = code.trim(), rarity = rarity.name,
                description = description.trim(), imagePath = imagePath, artSeed = seed,
                colorA = colors.first, colorB = colors.second,
            )
            repo.saveFigure(f)
            onDone()
        }
    }
}

@Composable
fun FigureEditorScreen(nav: AppNav, figureId: Long, seriesId: Long) {
    val vm = boxViewModel("figure_edit_${figureId}_$seriesId") { FigureEditorViewModel(it.repo, figureId, seriesId) }
    val series by vm.seriesRows.collectAsStateWithLifecycle()
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { ImageStore.import(context, uri)?.let { vm.imagePath = it } }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri
        if (ok && uri != null) scope.launch { ImageStore.import(context, uri)?.let { vm.imagePath = it } }
    }

    SubScreen(title = if (vm.isEdit) "编辑款式" else "添加款式", onBack = nav::back) { padding ->
        if (vm.loading) {
            LoadingState(Modifier.padding(padding)); return@SubScreen
        }
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BoxCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RarityFrame(vm.rarity, Modifier.size(120.dp), shape = RoundedCornerShape(24.dp)) {
                        FigureArt(vm.seed, vm.colors.first, vm.colors.second, vm.rarity, Modifier.fillMaxSize().padding(6.dp), imagePath = vm.imagePath)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TonalAction("相册选图", Icons.Rounded.PhotoLibrary) { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                        TonalAction("拍照", Icons.Rounded.PhotoCamera) {
                            val uri = ImageStore.newCameraUri(context)
                            cameraUri = uri
                            camera.launch(uri)
                        }
                        if (vm.imagePath != null) TonalAction("移除图片", Icons.Rounded.Delete) { vm.imagePath = null }
                        else TonalAction("换个造型", Icons.Rounded.Casino) { vm.seed = Random.nextInt(100_000) }
                    }
                }
                if (vm.imagePath == null) {
                    Spacer(Modifier.height(12.dp))
                    Text("占位插画配色（未上传图片时显示）", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ArtPalette.pairs.forEach { pair ->
                            Box(
                                Modifier.size(34.dp).clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(Color(pair.first), Color(pair.second))))
                                    .border(if (vm.colors == pair) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                    .clickable { vm.colors = pair }
                            )
                        }
                    }
                }
            }
            BoxCard(Modifier.fillMaxWidth()) {
                if (series.isEmpty()) {
                    Text("还没有任何系列，请先新建系列。", color = MaterialTheme.colorScheme.error)
                    TonalAction("新建系列", Icons.Rounded.Restore) { nav.seriesEdit() }
                } else {
                    val selected = series.firstOrNull { it.series.id == vm.seriesId }
                    SelectField(
                        "所属系列 *",
                        selected,
                        series,
                        { it?.let { r -> "${r.series.name}（${r.brandName}）" } ?: "请选择" },
                        { vm.seriesId = it?.series?.id ?: 0L },
                    )
                }
                Spacer(Modifier.height(8.dp))
                BoxTextField(vm.name, { vm.name = it }, "款式 / 角色名称 *")
                Spacer(Modifier.height(8.dp))
                BoxTextField(vm.code, { vm.code = it }, "系列编号", placeholder = "No.01")
                Spacer(Modifier.height(10.dp))
                Text("稀有度（SSR / UR 为隐藏款）", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Rarity.entries.forEach { r -> ChoiceChip(r.fullLabel, vm.rarity == r, { vm.rarity = r }) }
                }
                Spacer(Modifier.height(8.dp))
                BoxTextField(vm.description, { vm.description = it }, "角色介绍（可选）", singleLine = false, minLines = 2)
            }
            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            GradientButton("保存款式", Modifier.fillMaxWidth()) {
                vm.save {
                    container.haptics.success()
                    container.emit(AppEvent.Message("款式已保存"))
                    nav.back()
                }
            }
        }
    }
}

/* ======================= 系列编辑 + 概率配置 ======================= */

class SeriesEditorViewModel(private val repo: BoxRepository, private val seriesId: Long) : ViewModel() {
    val brands = repo.brands.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val ips = repo.ips.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    var loading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    var brandName by mutableStateOf("")
    var ipName by mutableStateOf("")
    var name by mutableStateOf("")
    var year by mutableStateOf(LocalDate.now().year.toString())
    var description by mutableStateOf("")
    var boxPrice by mutableStateOf("")
    var currency by mutableStateOf("CNY")
    var theme by mutableStateOf(ArtPalette.seriesThemes.first())
    var blindBoxEnabled by mutableStateOf(true)
    var rateTexts by mutableStateOf(rateToTexts(RateConfig.DEFAULT))
    var pool by mutableStateOf<List<PoolFigure>>(emptyList())
    private var existing: Series? = null
    val isEdit get() = seriesId != 0L

    init {
        viewModelScope.launch {
            currency = repo.settings.current().baseCurrency
            if (seriesId != 0L) {
                val s = repo.getSeries(seriesId)
                if (s == null) error = "系列不存在" else {
                    existing = s
                    name = s.name
                    year = s.releaseYear.toString()
                    description = s.description
                    boxPrice = if (s.boxPrice == 0L) "" else Money.toInput(s.boxPrice)
                    currency = s.currency
                    theme = s.themeColor to s.accentColor
                    blindBoxEnabled = s.blindBoxEnabled
                    rateTexts = rateToTexts(s.rates)
                    pool = repo.figuresOfSeries(seriesId).map { PoolFigure(it.id, it.rarityEnum) }
                    brandName = repo.brands.first().firstOrNull { it.id == s.brandId }?.name ?: ""
                    ipName = repo.ips.first().firstOrNull { it.id == s.ipId }?.name ?: ""
                }
            }
            loading = false
        }
    }

    fun parsedRates(): RateConfig? {
        val values = Rarity.entries.map { RateConfig.parsePercent(rateTexts[it] ?: "") ?: return null }
        return RateConfig(values[0], values[1], values[2], values[3])
    }

    fun setRate(rarity: Rarity, text: String) {
        rateTexts = rateTexts + (rarity to text)
    }

    fun resetRates() {
        rateTexts = rateToTexts(RateConfig.DEFAULT)
    }

    fun save(onDone: () -> Unit) {
        val rates = parsedRates()
        val price = if (boxPrice.isBlank()) 0L else Money.parse(boxPrice)
        error = when {
            brandName.isBlank() -> "请填写品牌"
            name.isBlank() -> "请填写系列名称"
            year.toIntOrNull() == null -> "发售年份格式不正确"
            price == null -> "参考价格式不正确"
            rates == null -> "概率请填写 0~100 之间的数字"
            rates.total != RateConfig.TOTAL -> "四档概率之和必须正好为 100%（当前 ${RateConfig.percentText(rates.total)}）"
            else -> null
        }
        if (error != null || rates == null) return
        viewModelScope.launch {
            runCatching {
                val s = (existing ?: Series(brandId = 0, ipId = 0, name = name, releaseYear = year.toInt())).copy(
                    name = name.trim(), releaseYear = year.toInt(), description = description.trim(),
                    boxPrice = price ?: 0L, currency = currency, themeColor = theme.first, accentColor = theme.second,
                    rateR = rates.r, rateSR = rates.sr, rateSSR = rates.ssr, rateUR = rates.ur, blindBoxEnabled = blindBoxEnabled,
                )
                repo.saveSeries(s, brandName, ipName)
            }.onSuccess { onDone() }.onFailure { error = "保存失败：${it.message}" }
        }
    }

    companion object {
        fun rateToTexts(r: RateConfig): Map<Rarity, String> =
            Rarity.entries.associateWith { (r.of(it) / 100.0).let { v -> if (v == Math.floor(v)) v.toInt().toString() else v.toString() } }
    }
}

@Composable
fun SeriesEditorScreen(nav: AppNav, seriesId: Long) {
    val vm = boxViewModel("series_edit_$seriesId") { SeriesEditorViewModel(it.repo, seriesId) }
    val brands by vm.brands.collectAsStateWithLifecycle()
    val ips by vm.ips.collectAsStateWithLifecycle()
    val container = LocalContainer.current

    SubScreen(title = if (vm.isEdit) "编辑系列" else "新建系列", onBack = nav::back) { padding ->
        if (vm.loading) {
            LoadingState(Modifier.padding(padding)); return@SubScreen
        }
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BoxCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BlindBoxArt(vm.theme.first, vm.theme.second, vm.name, Modifier.size(96.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("系列主题色", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ArtPalette.seriesThemes.forEach { pair ->
                                Box(
                                    Modifier.size(32.dp).clip(CircleShape)
                                        .background(Brush.linearGradient(listOf(Color(pair.first), Color(pair.second))))
                                        .border(if (vm.theme == pair) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                        .clickable { vm.theme = pair }
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                SuggestField(vm.brandName, { vm.brandName = it }, "品牌 *", brands.map { it.name })
                Spacer(Modifier.height(8.dp))
                SuggestField(vm.ipName, { vm.ipName = it }, "IP 名称（可选，默认同品牌）", ips.map { it.name }.distinct())
                Spacer(Modifier.height(8.dp))
                BoxTextField(vm.name, { vm.name = it }, "系列名称 *")
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BoxTextField(vm.year, { vm.year = it }, "发售年份", Modifier.weight(1f), keyboardType = KeyboardType.Number)
                    BoxTextField(vm.boxPrice, { vm.boxPrice = it }, "单盒参考价", Modifier.weight(1f), keyboardType = KeyboardType.Decimal)
                }
                Spacer(Modifier.height(8.dp))
                SelectField("参考价币种", Currency.of(vm.currency), Currency.entries.toList(), { "${it.code} ${it.label}" }, { vm.currency = it.code })
                Spacer(Modifier.height(8.dp))
                BoxTextField(vm.description, { vm.description = it }, "系列介绍（可选）", singleLine = false, minLines = 2)
            }

            BoxCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("加入虚拟盲盒专区", style = MaterialTheme.typography.titleMedium)
                        Text("开启后可用该系列进行免费的模拟开盒", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = vm.blindBoxEnabled, onCheckedChange = { vm.blindBoxEnabled = it })
                }
                Spacer(Modifier.height(12.dp))
                Text("稀有度概率配置（%）", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Rarity.entries.forEach { r ->
                    val count = vm.pool.count { it.rarity == r }
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RarityBadge(r, Modifier.width(92.dp), showLabel = true)
                        Spacer(Modifier.width(8.dp))
                        BoxTextField(vm.rateTexts[r] ?: "", { vm.setRate(r, it) }, "${r.code} 概率", Modifier.weight(1f), keyboardType = KeyboardType.Decimal, suffix = "%")
                        if (vm.isEdit) {
                            Spacer(Modifier.width(8.dp))
                            Text("$count 款", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                val rates = vm.parsedRates()
                val total = rates?.total
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        rates == null -> "请输入 0~100 之间的数字"
                        total == RateConfig.TOTAL -> "✓ 合计 100%"
                        else -> "合计 ${RateConfig.percentText(total ?: 0)}，必须正好为 100% 才能保存"
                    },
                    color = if (total == RateConfig.TOTAL) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelLarge,
                )
                if (rates != null && vm.isEdit) {
                    val warnings = BlindBoxEngine.validate(rates, vm.pool).filterNot { it.contains("100%") }
                    if (warnings.isNotEmpty()) {
                        Text("提示：" + warnings.joinToString("；"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }
                Text(
                    "同一稀有度内的各款等概率。这些概率只用于本 App 的免费娱乐模拟，请勿当作任何品牌的官方概率。",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                TonalAction("恢复默认概率（80 / 17 / 2.5 / 0.5）", Icons.Rounded.Restore, Modifier.fillMaxWidth()) { vm.resetRates() }
            }

            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            GradientButton("保存系列", Modifier.fillMaxWidth()) {
                vm.save {
                    container.haptics.success()
                    container.emit(AppEvent.Message("系列已保存"))
                    nav.back()
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
