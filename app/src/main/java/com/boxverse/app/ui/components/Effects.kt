package com.boxverse.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.boxverse.app.domain.Rarity
import com.boxverse.app.ui.theme.RarityStyle
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/* ---------------- 粒子与光效 ---------------- */

private data class Particle(val angle: Float, val speed: Float, val size: Float, val color: Color, val spin: Float, val star: Boolean)

/** 一次性粒子爆发，[trigger] 变化时重新播放 */
@Composable
fun ParticleBurst(trigger: Int, rarity: Rarity, modifier: Modifier = Modifier, count: Int = 70) {
    val colors = RarityStyle.colors(rarity) + Color.White
    val particles = remember(trigger) {
        val rnd = Random(trigger * 31 + rarity.ordinal)
        List(count + rarity.order * 30) {
            Particle(
                angle = rnd.nextFloat() * 360f,
                speed = 0.35f + rnd.nextFloat() * 0.65f,
                size = 3f + rnd.nextFloat() * (6f + rarity.order * 2f),
                color = colors[rnd.nextInt(colors.size)],
                spin = rnd.nextFloat(),
                star = rnd.nextFloat() < 0.4f,
            )
        }
    }
    val progress = remember(trigger) { Animatable(0f) }
    LaunchedEffect(trigger) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(1400 + rarity.order * 300, easing = LinearEasing))
    }
    Canvas(modifier) {
        val p = progress.value
        if (p <= 0f || p >= 1f) return@Canvas
        val center = Offset(size.width / 2, size.height / 2)
        val maxR = size.minDimension * 0.75f
        particles.forEach { pt ->
            val eased = 1f - (1f - p) * (1f - p)
            val dist = maxR * pt.speed * eased
            val rad = Math.toRadians(pt.angle.toDouble())
            val pos = Offset(center.x + (cos(rad) * dist).toFloat(), center.y + (sin(rad) * dist).toFloat() + p * p * 60f)
            val alpha = (1f - p).coerceIn(0f, 1f)
            val s = pt.size.dp.toPx() * (1f - p * 0.5f)
            if (pt.star) drawStar(pos, s, s * 0.38f, pt.color.copy(alpha = alpha))
            else drawCircle(pt.color.copy(alpha = alpha), radius = s * 0.5f, center = pos)
        }
    }
}

/** 旋转光芒（隐藏款专属） */
@Composable
fun GodRays(rarity: Rarity, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "rays")
    val angle by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "angle")
    val pulse by transition.animateFloat(0.6f, 1f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "pulse")
    val colors = RarityStyle.colors(rarity)
    Canvas(modifier.rotate(angle)) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.maxDimension
        val rays = if (rarity == Rarity.UR) 16 else 12
        for (i in 0 until rays) {
            val a0 = Math.toRadians((i * 360.0 / rays))
            val a1 = Math.toRadians((i * 360.0 / rays) + 360.0 / rays / 2.2)
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(c.x, c.y)
                lineTo(c.x + (cos(a0) * r).toFloat(), c.y + (sin(a0) * r).toFloat())
                lineTo(c.x + (cos(a1) * r).toFloat(), c.y + (sin(a1) * r).toFloat())
                close()
            }
            drawPath(path, colors[i % colors.size].copy(alpha = 0.22f * pulse))
        }
        drawCircle(
            Brush.radialGradient(listOf(Color.White.copy(alpha = 0.85f * pulse), colors.first().copy(alpha = 0.3f), Color.Transparent), center = c, radius = size.minDimension * 0.5f),
            radius = size.minDimension * 0.5f, center = c,
        )
    }
}

/** 柔和呼吸光环 */
@Composable
fun PulseGlow(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "glow")
    val s by transition.animateFloat(0.85f, 1.1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "s")
    Canvas(modifier) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension / 2 * s
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.55f), Color.Transparent), center = c, radius = r), radius = r, center = c)
    }
}

/** 装饰性漂浮星点背景 */
@Composable
fun StarField(modifier: Modifier = Modifier, color: Color = Color.White, count: Int = 26) {
    val transition = rememberInfiniteTransition(label = "stars")
    val t by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(4000), RepeatMode.Reverse), label = "t")
    val stars = remember { List(count) { Triple(Random.nextFloat(), Random.nextFloat(), Random.nextFloat()) } }
    Canvas(modifier) {
        stars.forEach { (x, y, k) ->
            val alpha = 0.25f + 0.6f * ((k + t) % 1f)
            drawStar(Offset(x * size.width, y * size.height), (2f + k * 4f).dp.toPx(), (0.8f + k * 1.4f).dp.toPx(), color.copy(alpha = alpha))
        }
    }
}

/* ---------------- 图表 ---------------- */

val ChartColors = listOf(
    Color(0xFF8F7BEF), Color(0xFFF59AC4), Color(0xFFE2AE45), Color(0xFF6FC3E8), Color(0xFF5CC8A0),
    Color(0xFFFF9F7A), Color(0xFFB99CFF), Color(0xFF9AA3C7),
)

data class ChartSlice(val label: String, val value: Float, val display: String)

@Composable
fun DonutChart(slices: List<ChartSlice>, modifier: Modifier = Modifier, centerTitle: String = "", centerValue: String = "") {
    val total = slices.sumOf { it.value.toDouble() }.toFloat().coerceAtLeast(0.0001f)
    val anim = remember(slices) { Animatable(0f) }
    LaunchedEffect(slices) { anim.animateTo(1f, tween(900)) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 22.dp.toPx()
                val d = size.minDimension - stroke
                val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
                var start = -90f
                slices.forEachIndexed { i, s ->
                    val sweep = 360f * (s.value / total) * anim.value
                    drawArc(ChartColors[i % ChartColors.size], start, (sweep - 1.5f).coerceAtLeast(0.5f), false, topLeft = tl, size = Size(d, d), style = Stroke(stroke))
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(centerValue, style = MaterialTheme.typography.titleMedium)
                Text(centerTitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            slices.take(6).forEachIndexed { i, s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(ChartColors[i % ChartColors.size]))
                    Spacer(Modifier.width(6.dp))
                    Text(s.label, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("${(s.value / total * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** 竖向柱状图（例如每月支出） */
@Composable
fun ColumnChart(values: List<Float>, labels: List<String>, modifier: Modifier = Modifier, highlightLast: Boolean = true) {
    val max = (values.maxOrNull() ?: 0f).coerceAtLeast(0.0001f)
    val scheme = MaterialTheme.colorScheme
    val anim = remember(values) { Animatable(0f) }
    LaunchedEffect(values) { anim.animateTo(1f, tween(800)) }
    Column(modifier) {
        Row(Modifier.fillMaxWidth().height(140.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            values.forEachIndexed { i, v ->
                val frac = (v / max) * anim.value
                val last = highlightLast && i == values.lastIndex
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier.fillMaxWidth()
                            .fillMaxHeight(frac.coerceAtLeast(if (v > 0) 0.03f else 0.01f))
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
                            .background(
                                if (last) Brush.verticalGradient(listOf(scheme.secondary, scheme.primary))
                                else Brush.verticalGradient(listOf(scheme.primary.copy(alpha = 0.55f), scheme.primary.copy(alpha = 0.3f)))
                            )
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            labels.forEach { l ->
                Text(l, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

/** 横向条形列表（例如各系列数量） */
@Composable
fun BarList(items: List<Pair<String, Float>>, valueText: (Float) -> String, modifier: Modifier = Modifier) {
    val max = (items.maxOfOrNull { it.second } ?: 0f).coerceAtLeast(0.0001f)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEachIndexed { i, (label, v) ->
            Column {
                Row {
                    Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(valueText(v), style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.height(4.dp))
                GradientProgressBar(v / max, Modifier.fillMaxWidth(), height = 10.dp, colors = listOf(ChartColors[i % ChartColors.size], ChartColors[(i + 1) % ChartColors.size]))
            }
        }
    }
}

/* ---------------- 截图分享 ---------------- */

/** 把内容绘制到 [layer] 中，之后可调用 layer.toImageBitmap() 生成分享图片 */
@Composable
fun CaptureBox(layer: GraphicsLayer, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier.drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
        }
    ) { content() }
}

@Composable
fun rememberCaptureLayer(): GraphicsLayer = rememberGraphicsLayer()

/** 稀有度光效背景带（用于结果展示） */
@Composable
fun RarityAura(rarity: Rarity, modifier: Modifier = Modifier) {
    Box(modifier.padding(4.dp)) {
        when (rarity) {
            Rarity.SSR, Rarity.UR -> GodRays(rarity, Modifier.fillMaxSize())
            else -> PulseGlow(RarityStyle.main(rarity), Modifier.fillMaxSize())
        }
    }
}
