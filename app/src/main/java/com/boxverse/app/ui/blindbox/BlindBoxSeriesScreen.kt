package com.boxverse.app.ui.blindbox

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Redeem
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.boxverse.app.AppEvent
import com.boxverse.app.data.db.FigureRow
import com.boxverse.app.data.db.SeriesRow
import com.boxverse.app.data.repo.BoxRepository
import com.boxverse.app.data.repo.DrawResult
import com.boxverse.app.domain.BlindBoxEngine
import com.boxverse.app.domain.PoolFigure
import com.boxverse.app.domain.Rarity
import com.boxverse.app.domain.RateConfig
import com.boxverse.app.ui.components.BlindBoxArt
import com.boxverse.app.ui.components.BoxCard
import com.boxverse.app.ui.components.CaptureBox
import com.boxverse.app.ui.components.EmptyState
import com.boxverse.app.ui.components.FigureArt
import com.boxverse.app.ui.components.GodRays
import com.boxverse.app.ui.components.GradientButton
import com.boxverse.app.ui.components.LoadingState
import com.boxverse.app.ui.components.ParticleBurst
import com.boxverse.app.ui.components.PulseGlow
import com.boxverse.app.ui.components.RarityBadge
import com.boxverse.app.ui.components.RarityFrame
import com.boxverse.app.ui.components.StarField
import com.boxverse.app.ui.components.SubScreen
import com.boxverse.app.ui.components.TonalAction
import com.boxverse.app.ui.components.rememberCaptureLayer
import com.boxverse.app.ui.navigation.AppNav
import com.boxverse.app.ui.navigation.LocalContainer
import com.boxverse.app.ui.navigation.Routes
import com.boxverse.app.ui.navigation.boxViewModel
import com.boxverse.app.ui.theme.RarityStyle
import com.boxverse.app.util.ShareUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

data class BoxSeriesData(val row: SeriesRow, val figures: List<FigureRow>, val openedHere: Int)

sealed interface OpenPhase {
    data object Idle : OpenPhase
    data class Animating(val results: List<DrawResult>, val newIds: Set<Long>) : OpenPhase
    data class Revealed(val results: List<DrawResult>, val newIds: Set<Long>, val saved: Boolean) : OpenPhase
}

class BlindBoxSeriesViewModel(private val repo: BoxRepository, private val seriesId: Long) : ViewModel() {
    val data: StateFlow<BoxSeriesData?> = combine(repo.seriesRows, repo.figureRows, repo.openings) { series, figures, openings ->
        val row = series.firstOrNull { it.series.id == seriesId } ?: return@combine null
        BoxSeriesData(
            row,
            figures.filter { it.figure.seriesId == seriesId }.sortedWith(compareBy({ it.figure.rarityEnum.order }, { it.figure.code })),
            openings.count { it.seriesId == seriesId },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    var phase by mutableStateOf<OpenPhase>(OpenPhase.Idle)
        private set
    var lastCount by mutableIntStateOf(1)
        private set
    var error by mutableStateOf<String?>(null)

    fun open(count: Int) {
        if (phase is OpenPhase.Animating) return
        lastCount = count
        viewModelScope.launch {
            runCatching {
                val before = data.value?.figures?.filter { it.virtualQty > 0 }?.map { it.figure.id }?.toSet() ?: emptySet()
                val results = repo.openBoxes(seriesId, count)
                val seen = before.toMutableSet()
                val newIds = mutableSetOf<Long>()
                results.forEach { r -> if (seen.add(r.figure.id)) newIds += r.opening.id }
                OpenPhase.Animating(results, newIds)
            }.onSuccess { phase = it }.onFailure { error = it.message ?: "开盒失败" }
        }
    }

    fun finishAnimation() {
        val p = phase
        if (p is OpenPhase.Animating) phase = OpenPhase.Revealed(p.results, p.newIds, saved = false)
    }

    fun saveToVirtual(onDone: () -> Unit) {
        val p = phase as? OpenPhase.Revealed ?: return
        if (p.saved || p.results.isEmpty()) return
        viewModelScope.launch {
            repo.saveBatchToVirtual(p.results.first().opening.batchId)
            phase = p.copy(saved = true)
            onDone()
        }
    }

    fun reset() {
        phase = OpenPhase.Idle
    }
}

@Composable
fun BlindBoxSeriesScreen(nav: AppNav, seriesId: Long) {
    val vm = boxViewModel("box_$seriesId") { BlindBoxSeriesViewModel(it.repo, seriesId) }
    val data by vm.data.collectAsStateWithLifecycle()
    val d = data
    val phase = vm.phase

    SubScreen(
        title = d?.row?.series?.name ?: "盲盒",
        onBack = { if (phase is OpenPhase.Revealed) vm.reset() else nav.back() },
        actions = { IconButton(onClick = { nav.go(Routes.BOX_HISTORY) }) { Icon(Icons.Rounded.History, "开盒记录") } },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                d == null -> LoadingState()
                d.figures.isEmpty() -> EmptyState(Icons.Rounded.Redeem, "这个系列还没有款式", "先在图鉴中为系列添加款式吧", actionLabel = "添加款式", onAction = { nav.figureEdit(seriesId = seriesId) })
                else -> when (phase) {
                    is OpenPhase.Idle -> IdleContent(d, vm)
                    is OpenPhase.Animating -> OpeningAnimation(d, phase) { vm.finishAnimation() }
                    is OpenPhase.Revealed -> RevealContent(d, phase, vm, nav)
                }
            }
            vm.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
            }
        }
    }
}

/* ---------------- 选择 & 介绍 ---------------- */

@Composable
private fun IdleContent(d: BoxSeriesData, vm: BlindBoxSeriesViewModel) {
    val s = d.row.series
    val pool = remember(d.figures) { d.figures.map { PoolFigure(it.figure.id, it.figure.rarityEnum) } }
    val tiers = remember(pool, s) { BlindBoxEngine.effectiveTierRates(s.rates, pool) }
    val errors = remember(pool, s) { BlindBoxEngine.validate(s.rates, pool) }
    val transition = rememberInfiniteTransition(label = "idle")
    val float by transition.animateFloat(-8f, 8f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "float")
    val tilt by transition.animateFloat(-3f, 3f, infiniteRepeatable(tween(2200), RepeatMode.Reverse), label = "tilt")

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Box(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(300.dp).clip(RoundedCornerShape(32.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF2B2160), lerp(Color(s.themeColor), Color(0xFF2B2160), 0.4f), lerp(Color(s.accentColor), Color(0xFF2B2160), 0.2f)))),
            contentAlignment = Alignment.Center,
        ) {
            StarField(Modifier.fillMaxSize(), count = 34)
            PulseGlow(Color(s.accentColor), Modifier.size(260.dp))
            BlindBoxArt(s.themeColor, s.accentColor, s.name, Modifier.size(190.dp).offset(y = float.dp).rotate(tilt), glow = 0.35f)
            Text(
                "免费 · 虚拟模拟",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.TopStart).padding(14.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.2f)).padding(horizontal = 10.dp, vertical = 4.dp),
            )
            Text(
                "本系列已开 ${d.openedHere} 次",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(s.name, style = MaterialTheme.typography.headlineSmall)
            Text("${d.row.brandName} · ${d.row.ipName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (s.description.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(s.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OpenButton("开 1 盒", Modifier.weight(1f), listOf(Color(0xFFB39CFF), Color(0xFFF59AC4))) { vm.open(1) }
            OpenButton("开 5 盒", Modifier.weight(1f), listOf(Color(0xFF8F7BEF), Color(0xFFFF8FB8))) { vm.open(5) }
            OpenButton("开 10 盒", Modifier.weight(1f), listOf(Color(0xFFE2AE45), Color(0xFFFF7EB6))) { vm.open(10) }
        }
        Spacer(Modifier.height(16.dp))
        BoxCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
            Text("概率公示", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Rarity.entries.forEach { r ->
                val count = pool.count { it.rarity == r }
                val p = (tiers[r] ?: 0.0) * 100
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    RarityBadge(r, showLabel = true)
                    Spacer(Modifier.width(8.dp))
                    Text("$count 款", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text("${"%.2f".format(p)}%" + if (count > 0) "（每款 ${"%.3f".format(p / count)}%）" else "", style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                (if (errors.isEmpty()) "设定概率合计 100%，同档位内各款等概率。"
                else "设定存在问题（${errors.joinToString("；")}），已在有款式的档位间按比例归一化，上表为实际生效概率。") +
                    "以上为本 App 中该系列的模拟设定，不代表任何真实品牌的官方概率。每次开盒结果都会写入开盒记录，可随时追溯。",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(14.dp))
        Text("系列阵容", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(8.dp))
        FlowRow(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            maxItemsInEachRow = 4,
        ) {
            d.figures.forEach { f ->
                val got = f.virtualQty > 0 || f.ownedQty > 0
                val mystery = f.figure.rarityEnum.isHidden && !got
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    RarityFrame(f.figure.rarityEnum, Modifier.fillMaxWidth().aspectRatio(0.9f), owned = got, shape = RoundedCornerShape(16.dp)) {
                        FigureArt(f.figure.artSeed, f.figure.colorA, f.figure.colorB, f.figure.rarityEnum, Modifier.fillMaxSize().padding(4.dp), imagePath = f.figure.imagePath, silhouette = !got, showLock = false)
                        if (mystery) Text("?", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color.White)
                    }
                    Text(if (mystery) "？？？" else f.figure.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            repeat((4 - d.figures.size % 4) % 4) { Spacer(Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "彩色 = 已在虚拟或实物收藏中获得；隐藏款在获得前以 ？？？ 显示。",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
    }
}

@Composable
private fun OpenButton(text: String, modifier: Modifier, colors: List<Color>, onClick: () -> Unit) {
    val container = LocalContainer.current
    GradientButton(text, modifier, icon = Icons.Rounded.AutoAwesome, colors = colors) {
        container.haptics.tick()
        onClick()
    }
}

/* ---------------- 开盒动画 ---------------- */

@Composable
private fun OpeningAnimation(d: BoxSeriesData, phase: OpenPhase.Animating, onFinished: () -> Unit) {
    val s = d.row.series
    val container = LocalContainer.current
    val best = phase.results.maxOf { it.figure.rarityEnum.order }.let { o -> Rarity.entries.first { it.order == o } }
    val shake = remember { Animatable(0f) }
    val burst = remember { Animatable(0f) }
    val flash = remember { Animatable(0f) }
    var burstTrigger by remember { mutableIntStateOf(0) }
    var banner by remember { mutableStateOf(false) }

    LaunchedEffect(phase) {
        val pulses = launch {
            repeat(6) {
                container.haptics.shake()
                container.sound.shake()
                delay(230)
            }
        }
        shake.animateTo(1f, tween(1500, easing = LinearEasing))
        pulses.cancel()
        burstTrigger++
        container.sound.reveal(best)
        container.haptics.reveal(best)
        if (best.isHidden) {
            launch { flash.animateTo(1f, tween(120)); flash.animateTo(0f, tween(700)) }
            banner = true
        }
        burst.animateTo(1f, tween(500, easing = FastOutSlowInEasing))
        delay(if (best.isHidden) 1300 else 350)
        onFinished()
    }

    val glowColor = if (shake.value > 0.55f) RarityStyle.main(best) else Color(s.accentColor)
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1B1538), Color(0xFF3B2A6B), Color(0xFF1B1538)))),
        contentAlignment = Alignment.Center,
    ) {
        StarField(Modifier.fillMaxSize(), count = 50)
        if (best.isHidden && burst.value > 0f) GodRays(best, Modifier.fillMaxSize().alpha(burst.value))
        PulseGlow(glowColor, Modifier.size((220 + 160 * shake.value).dp))
        val wobble = sin(shake.value * PI.toFloat() * 18f) * (3f + 14f * shake.value)
        BlindBoxArt(
            s.themeColor, s.accentColor, s.name,
            Modifier.size(200.dp)
                .rotate(wobble)
                .scale(1f + 0.12f * shake.value + 0.7f * burst.value)
                .alpha(1f - burst.value),
            glow = shake.value,
        )
        ParticleBurst(burstTrigger, best, Modifier.fillMaxSize())
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (phase.results.size > 1) "正在开启 ${phase.results.size} 个盲盒…" else "正在开启盲盒…",
                color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.titleMedium,
            )
        }
        AnimatedVisibility(banner, enter = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(), modifier = Modifier.align(Alignment.TopCenter).padding(top = 60.dp)) {
            Text(
                if (best == Rarity.UR) "🌈 超级隐藏降临 🌈" else "✨ 隐藏款出现 ✨",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(RarityStyle.brush(best)).padding(horizontal = 22.dp, vertical = 10.dp),
            )
        }
        Box(Modifier.fillMaxSize().alpha(flash.value).background(Color.White))
    }
}

/* ---------------- 结果展示 ---------------- */

@Composable
private fun RevealContent(d: BoxSeriesData, phase: OpenPhase.Revealed, vm: BlindBoxSeriesViewModel, nav: AppNav) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberCaptureLayer()
    val results = phase.results
    val best = results.maxOf { it.figure.rarityEnum.order }.let { o -> Rarity.entries.first { it.order == o } }

    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF241B4A), Color(0xFF3B2A6B), MaterialTheme.colorScheme.background)))
            .verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CaptureBox(layer, Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFF241B4A), Color(0xFF3B2A6B)))).padding(16.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    if (results.size == 1) SingleResult(results.first(), results.first().opening.id in phase.newIds)
                    else MultiResult(results, phase.newIds)
                    Spacer(Modifier.height(10.dp))
                    Text("${d.row.series.name} · 盒宇宙 BOXVERSE 虚拟开盒", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (phase.saved) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primaryContainer).padding(12.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Text("已收入虚拟收藏柜", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                }
            } else {
                GradientButton("收入虚拟收藏柜", Modifier.fillMaxWidth(), icon = Icons.Rounded.Inventory2) {
                    vm.saveToVirtual {
                        container.haptics.success()
                        container.emit(AppEvent.Message("已收入虚拟收藏柜（虚拟藏品不计入实物资产）"))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TonalAction("再开 ${vm.lastCount} 盒", Icons.Rounded.Refresh, Modifier.weight(1f)) { vm.open(vm.lastCount) }
                TonalAction("分享结果", Icons.Rounded.Share, Modifier.weight(1f)) {
                    scope.launch {
                        runCatching {
                            val bmp = layer.toImageBitmap()
                            ShareUtil.shareBitmap(context, bmp.asAndroidBitmap(), "我在盒宇宙开出了${best.fullLabel}！")
                        }.onFailure { container.emit(AppEvent.Message("生成分享图片失败：${it.message}")) }
                    }
                }
            }
            TonalAction("返回系列", Icons.Rounded.Redeem, Modifier.fillMaxWidth()) { vm.reset() }
            Text(
                "开盒结果已自动写入开盒记录。虚拟藏品仅存在于虚拟收藏柜，与实物收藏严格分开。",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}


@Composable
private fun SingleResult(r: DrawResult, isNew: Boolean) {
    val rarity = r.figure.rarityEnum
    val flip = remember { Animatable(90f) }
    var burst by remember { mutableIntStateOf(0) }
    LaunchedEffect(r.opening.id) {
        burst++
        flip.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 180f))
    }
    Box(Modifier.fillMaxWidth().height(360.dp), contentAlignment = Alignment.Center) {
        if (rarity.isHidden) GodRays(rarity, Modifier.fillMaxSize()) else PulseGlow(RarityStyle.main(rarity), Modifier.size(320.dp))
        RarityFrame(
            rarity,
            Modifier.size(230.dp, 270.dp).graphicsLayer {
                rotationY = flip.value
                cameraDistance = 14f * density
            },
            shape = RoundedCornerShape(32.dp),
        ) {
            FigureArt(r.figure.artSeed, r.figure.colorA, r.figure.colorB, rarity, Modifier.fillMaxSize().padding(14.dp), imagePath = r.figure.imagePath)
        }
        ParticleBurst(burst, rarity, Modifier.fillMaxSize())
    }
    RarityBadge(rarity, showLabel = true)
    Spacer(Modifier.height(8.dp))
    Text(r.figure.name, style = MaterialTheme.typography.headlineMedium, color = Color.White)
    Text(
        (if (isNew) "🎉 首次获得！" else "又遇见你啦") + " · 抽中概率 ${"%.3f".format(r.opening.probabilityPpm / 10000.0)}%",
        style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f),
    )
    if (r.figure.description.isNotBlank()) {
        Spacer(Modifier.height(4.dp))
        Text(r.figure.description, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
    }
}

@Composable
private fun MultiResult(results: List<DrawResult>, newIds: Set<Long>) {
    val best = results.maxOf { it.figure.rarityEnum.order }
    val counts = Rarity.entries.associateWith { r -> results.count { it.figure.rarityEnum == r } }
    Text("开盒结果 × ${results.size}", style = MaterialTheme.typography.titleLarge, color = Color.White)
    Spacer(Modifier.height(4.dp))
    Text(
        counts.filterValues { it > 0 }.entries.joinToString("  ") { "${it.key.code}×${it.value}" },
        style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f),
    )
    Spacer(Modifier.height(12.dp))
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        maxItemsInEachRow = 5,
    ) {
        results.forEachIndexed { i, r ->
            ResultCell(r, i, r.opening.id in newIds, r.figure.rarityEnum.order == best && best >= 2, Modifier.weight(1f))
        }
        repeat((5 - results.size % 5) % 5) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun ResultCell(r: DrawResult, index: Int, isNew: Boolean, highlight: Boolean, modifier: Modifier) {
    val rarity = r.figure.rarityEnum
    val container = LocalContainer.current
    val appear = remember { Animatable(0f) }
    var burst by remember { mutableIntStateOf(0) }
    LaunchedEffect(r.opening.id) {
        delay(index * 140L)
        if (rarity.isHidden) {
            container.sound.reveal(rarity)
            container.haptics.reveal(rarity)
        } else container.sound.tap()
        burst++
        appear.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 260f))
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.8f), contentAlignment = Alignment.Center) {
            if (highlight) PulseGlow(RarityStyle.main(rarity), Modifier.fillMaxSize().scale(1.3f))
            RarityFrame(
                rarity,
                Modifier.fillMaxSize().graphicsLayer {
                    rotationY = (1f - appear.value) * 90f
                    alpha = appear.value.coerceIn(0f, 1f)
                    cameraDistance = 12f * density
                },
                shape = RoundedCornerShape(14.dp),
            ) {
                FigureArt(r.figure.artSeed, r.figure.colorA, r.figure.colorB, rarity, Modifier.fillMaxSize().padding(3.dp), imagePath = r.figure.imagePath)
                Box(Modifier.fillMaxSize().padding(3.dp)) {
                    RarityBadge(rarity, Modifier.align(Alignment.TopStart).scale(0.8f))
                    if (isNew) Text(
                        "NEW", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black,
                        modifier = Modifier.align(Alignment.BottomEnd).clip(RoundedCornerShape(4.dp)).background(Color(0xFFFF6F9F)).padding(horizontal = 3.dp),
                    )
                }
            }
            if (rarity.order >= 1) ParticleBurst(burst, rarity, Modifier.fillMaxSize().scale(1.6f), count = 16)
        }
        Text(r.figure.name, style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
