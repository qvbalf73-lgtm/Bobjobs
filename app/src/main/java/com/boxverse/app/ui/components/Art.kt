package com.boxverse.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.boxverse.app.domain.Rarity
import com.boxverse.app.ui.theme.LocalBoxColors
import com.boxverse.app.ui.theme.Palette
import com.boxverse.app.util.ImageStore
import kotlin.math.cos
import kotlin.math.sin

/**
 * 潮玩形象：优先显示用户上传的图片；没有图片时由程序根据种子绘制统一风格的原创占位形象。
 * [silhouette] = true 时显示灰色剪影（未解锁）。
 */
@Composable
fun FigureArt(
    seed: Int,
    colorA: Long,
    colorB: Long,
    rarity: Rarity,
    modifier: Modifier = Modifier,
    imagePath: String? = null,
    silhouette: Boolean = false,
    showLock: Boolean = silhouette,
) {
    val silColor = LocalBoxColors.current.silhouette
    Box(modifier, contentAlignment = Alignment.Center) {
        if (imagePath != null) {
            val context = LocalContext.current
            val file = remember(imagePath) { ImageStore.file(context, imagePath) }
            val filter = remember(silhouette) {
                if (silhouette) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null
            }
            AsyncImage(
                model = file,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = filter,
                alpha = if (silhouette) 0.35f else 1f,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Canvas(Modifier.fillMaxSize()) {
                drawCharacter(seed, Color(colorA), Color(colorB), rarity, if (silhouette) silColor else null)
            }
        }
        if (showLock) {
            Icon(
                Icons.Rounded.Lock,
                contentDescription = "未解锁",
                tint = Color.White,
                modifier = Modifier
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Color.Black.copy(alpha = 0.18f))
                    .padding(6.dp),
            )
        }
    }
}

private fun lighten(c: Color, f: Float) = lerp(c, Color.White, f)
private fun darken(c: Color, f: Float) = lerp(c, Color(0xFF2A2340), f)

fun DrawScope.drawCharacter(seed: Int, a: Color, b: Color, rarity: Rarity, sil: Color?) {
    val w = size.minDimension
    val cx = size.width / 2f
    val top = (size.height - w) / 2f
    fun y(f: Float) = top + w * f
    val main = sil ?: a
    val second = sil ?: b

    // 背景光晕
    if (sil == null) {
        drawCircle(
            brush = Brush.radialGradient(listOf(b.copy(alpha = 0.95f), b.copy(alpha = 0f)), center = Offset(cx, y(0.5f)), radius = w * 0.5f),
            radius = w * 0.5f,
            center = Offset(cx, y(0.5f)),
        )
    }
    // 地面阴影
    drawOval(Color.Black.copy(alpha = 0.08f), topLeft = Offset(cx - w * 0.2f, y(0.86f)), size = Size(w * 0.4f, w * 0.06f))

    val headC = Offset(cx, y(0.40f))
    val r = w * 0.235f
    val earVariant = seed % 5
    val earColor = sil ?: lighten(a, 0.1f)
    val innerEar = sil ?: lighten(b, 0.2f)

    // 耳朵 / 头饰（在头后面）
    when (earVariant) {
        0 -> { // 猫耳
            for (s in listOf(-1f, 1f)) {
                val p = Path().apply {
                    moveTo(headC.x + s * r * 0.95f, headC.y - r * 0.25f)
                    lineTo(headC.x + s * r * 0.85f, headC.y - r * 1.25f)
                    lineTo(headC.x + s * r * 0.2f, headC.y - r * 0.85f)
                    close()
                }
                drawPath(p, earColor)
                val ip = Path().apply {
                    moveTo(headC.x + s * r * 0.8f, headC.y - r * 0.45f)
                    lineTo(headC.x + s * r * 0.78f, headC.y - r * 1.02f)
                    lineTo(headC.x + s * r * 0.4f, headC.y - r * 0.8f)
                    close()
                }
                drawPath(ip, innerEar)
            }
        }
        1 -> { // 兔耳
            for (s in listOf(-1f, 1f)) {
                drawOval(earColor, topLeft = Offset(headC.x + s * r * 0.45f - r * 0.22f, headC.y - r * 1.9f), size = Size(r * 0.44f, r * 1.3f))
                drawOval(innerEar, topLeft = Offset(headC.x + s * r * 0.45f - r * 0.11f, headC.y - r * 1.7f), size = Size(r * 0.22f, r * 0.95f))
            }
        }
        2 -> { // 熊耳
            for (s in listOf(-1f, 1f)) {
                drawCircle(earColor, radius = r * 0.36f, center = Offset(headC.x + s * r * 0.78f, headC.y - r * 0.78f))
                drawCircle(innerEar, radius = r * 0.2f, center = Offset(headC.x + s * r * 0.78f, headC.y - r * 0.78f))
            }
        }
        3 -> { // 小嫩芽
            drawLine(sil ?: Color(0xFF6DBE7E), Offset(headC.x, headC.y - r * 0.95f), Offset(headC.x, headC.y - r * 1.4f), strokeWidth = r * 0.1f, cap = StrokeCap.Round)
            drawOval(sil ?: Color(0xFF8ED69A), topLeft = Offset(headC.x - r * 0.55f, headC.y - r * 1.6f), size = Size(r * 0.55f, r * 0.3f))
            drawOval(sil ?: Color(0xFF7FCB8C), topLeft = Offset(headC.x, headC.y - r * 1.68f), size = Size(r * 0.55f, r * 0.3f))
        }
        else -> { // 天线
            drawLine(earColor, Offset(headC.x, headC.y - r * 0.95f), Offset(headC.x + r * 0.15f, headC.y - r * 1.45f), strokeWidth = r * 0.08f, cap = StrokeCap.Round)
            drawCircle(sil ?: Palette.Gold, radius = r * 0.16f, center = Offset(headC.x + r * 0.15f, headC.y - r * 1.5f))
        }
    }

    // 身体
    val bodyTop = y(0.56f)
    drawOval(
        brush = if (sil != null) Brush.linearGradient(listOf(sil, sil)) else Brush.verticalGradient(listOf(lighten(a, 0.15f), darken(a, 0.08f)), startY = bodyTop, endY = y(0.9f)),
        topLeft = Offset(cx - w * 0.19f, bodyTop),
        size = Size(w * 0.38f, w * 0.32f),
    )
    // 肚兜 / 围裙
    if (sil == null) {
        drawOval(lighten(b, 0.35f), topLeft = Offset(cx - w * 0.11f, y(0.64f)), size = Size(w * 0.22f, w * 0.19f))
    }
    // 手
    for (s in listOf(-1f, 1f)) {
        drawOval(main, topLeft = Offset(cx + s * w * 0.18f - w * 0.055f, y(0.64f)), size = Size(w * 0.11f, w * 0.14f))
    }
    // 脚
    for (s in listOf(-1f, 1f)) {
        drawOval(sil ?: darken(a, 0.12f), topLeft = Offset(cx + s * w * 0.09f - w * 0.07f, y(0.84f)), size = Size(w * 0.14f, w * 0.06f))
    }

    // 头
    drawCircle(
        brush = if (sil != null) Brush.linearGradient(listOf(sil, sil)) else Brush.radialGradient(listOf(lighten(a, 0.45f), a), center = Offset(headC.x - r * 0.3f, headC.y - r * 0.35f), radius = r * 1.6f),
        radius = r,
        center = headC,
    )

    if (sil == null) {
        // 五官
        val eyeY = headC.y + r * 0.1f
        val eyeDx = r * 0.4f
        val faceColor = Color(0xFF3B2F55)
        when ((seed / 5) % 3) {
            0 -> for (s in listOf(-1f, 1f)) {
                drawOval(faceColor, topLeft = Offset(headC.x + s * eyeDx - r * 0.1f, eyeY - r * 0.14f), size = Size(r * 0.2f, r * 0.27f))
                drawCircle(Color.White, radius = r * 0.05f, center = Offset(headC.x + s * eyeDx - r * 0.03f, eyeY - r * 0.06f))
            }
            1 -> for (s in listOf(-1f, 1f)) {
                drawArc(
                    faceColor, startAngle = 200f, sweepAngle = 140f, useCenter = false,
                    topLeft = Offset(headC.x + s * eyeDx - r * 0.14f, eyeY - r * 0.1f), size = Size(r * 0.28f, r * 0.24f),
                    style = Stroke(width = r * 0.07f, cap = StrokeCap.Round),
                )
            }
            else -> for (s in listOf(-1f, 1f)) {
                drawStar(Offset(headC.x + s * eyeDx, eyeY), r * 0.15f, r * 0.06f, faceColor)
                drawCircle(Color.White, radius = r * 0.035f, center = Offset(headC.x + s * eyeDx - r * 0.03f, eyeY - r * 0.04f))
            }
        }
        // 腮红
        for (s in listOf(-1f, 1f)) {
            drawOval(Color(0xFFFF8FB5).copy(alpha = 0.45f), topLeft = Offset(headC.x + s * r * 0.62f - r * 0.15f, headC.y + r * 0.3f), size = Size(r * 0.3f, r * 0.16f))
        }
        // 嘴
        drawArc(
            faceColor, startAngle = 20f, sweepAngle = 140f, useCenter = false,
            topLeft = Offset(headC.x - r * 0.12f, headC.y + r * 0.22f), size = Size(r * 0.24f, r * 0.18f),
            style = Stroke(width = r * 0.06f, cap = StrokeCap.Round),
        )

        // 配饰
        when ((seed / 15) % 4) {
            1 -> { // 蝴蝶结
                val c = Offset(headC.x + r * 0.62f, headC.y - r * 0.72f)
                val bow = lighten(b, 0.05f).let { if (it == a) Palette.Pink else it }
                drawPath(Path().apply { moveTo(c.x, c.y); lineTo(c.x - r * 0.35f, c.y - r * 0.2f); lineTo(c.x - r * 0.35f, c.y + r * 0.2f); close() }, darken(bow, 0.1f))
                drawPath(Path().apply { moveTo(c.x, c.y); lineTo(c.x + r * 0.35f, c.y - r * 0.2f); lineTo(c.x + r * 0.35f, c.y + r * 0.2f); close() }, darken(bow, 0.1f))
                drawCircle(bow, radius = r * 0.09f, center = c)
            }
            2 -> drawStar(Offset(headC.x - r * 0.6f, headC.y - r * 0.68f), r * 0.2f, r * 0.09f, Palette.Gold)
            3 -> { // 小花
                val c = Offset(headC.x - r * 0.62f, headC.y - r * 0.7f)
                for (i in 0 until 5) {
                    val ang = Math.toRadians(i * 72.0 - 90)
                    drawCircle(Color.White, radius = r * 0.1f, center = Offset(c.x + (cos(ang) * r * 0.12f).toFloat(), c.y + (sin(ang) * r * 0.12f).toFloat()))
                }
                drawCircle(Palette.Gold, radius = r * 0.07f, center = c)
            }
            else -> {}
        }

        // 隐藏款：王冠 / 光环
        if (rarity == Rarity.UR) {
            drawOval(
                brush = Brush.sweepGradient(listOf(Color(0xFFFF7EB6), Color(0xFFB57CFF), Color(0xFF6FD3FF), Color(0xFFFFE27A), Color(0xFFFF7EB6)), center = Offset(headC.x, headC.y - r * 1.25f)),
                topLeft = Offset(headC.x - r * 0.6f, headC.y - r * 1.38f),
                size = Size(r * 1.2f, r * 0.26f),
                style = Stroke(width = r * 0.09f),
            )
        }
        if (rarity.isHidden) {
            val cy = headC.y - r * 0.92f
            val crown = Path().apply {
                moveTo(headC.x - r * 0.38f, cy + r * 0.12f)
                lineTo(headC.x - r * 0.42f, cy - r * 0.22f)
                lineTo(headC.x - r * 0.18f, cy - r * 0.02f)
                lineTo(headC.x, cy - r * 0.3f)
                lineTo(headC.x + r * 0.18f, cy - r * 0.02f)
                lineTo(headC.x + r * 0.42f, cy - r * 0.22f)
                lineTo(headC.x + r * 0.38f, cy + r * 0.12f)
                close()
            }
            drawPath(crown, Brush.verticalGradient(listOf(Color(0xFFFFE58A), Color(0xFFE2A23A)), startY = cy - r * 0.3f, endY = cy + r * 0.12f))
            drawCircle(Color(0xFFFF7EB6), radius = r * 0.05f, center = Offset(headC.x, cy - r * 0.02f))
        }
        // 稀有及以上：闪光
        if (rarity.order >= 1) {
            val sparkle = if (rarity == Rarity.SR) Color.White else Color(0xFFFFF3C4)
            drawStar(Offset(cx + w * 0.32f, y(0.2f)), w * 0.035f, w * 0.012f, sparkle)
            drawStar(Offset(cx - w * 0.34f, y(0.5f)), w * 0.025f, w * 0.009f, sparkle)
            if (rarity.isHidden) drawStar(Offset(cx + w * 0.3f, y(0.68f)), w * 0.03f, w * 0.01f, sparkle)
        }
    } else {
        // 剪影的高光
        drawCircle(second.copy(alpha = 0.0f), radius = 1f, center = headC)
    }
}

/** 四角星 */
fun DrawScope.drawStar(center: Offset, outer: Float, inner: Float, color: Color) {
    val path = Path()
    for (i in 0 until 8) {
        val rad = if (i % 2 == 0) outer else inner
        val ang = Math.toRadians(i * 45.0 - 90)
        val x = center.x + (cos(ang) * rad).toFloat()
        val y = center.y + (sin(ang) * rad).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
}

/** 盲盒外包装插画 */
@Composable
fun BlindBoxArt(
    themeColor: Long,
    accentColor: Long,
    title: String,
    modifier: Modifier = Modifier,
    glow: Float = 0f,
) {
    val a = Color(themeColor)
    val b = Color(accentColor)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.minDimension
            val cx = size.width / 2f
            val top = (size.height - w) / 2f
            if (glow > 0f) {
                drawCircle(
                    Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f * glow), b.copy(alpha = 0.5f * glow), Color.Transparent), center = Offset(cx, top + w * 0.5f), radius = w * 0.55f),
                    radius = w * 0.55f, center = Offset(cx, top + w * 0.5f),
                )
            }
            drawOval(Color.Black.copy(alpha = 0.1f), topLeft = Offset(cx - w * 0.3f, top + w * 0.88f), size = Size(w * 0.6f, w * 0.06f))
            // 盒身
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(lighten(a, 0.25f), a, darken(a, 0.1f)), startY = top + w * 0.36f, endY = top + w * 0.9f),
                topLeft = Offset(cx - w * 0.3f, top + w * 0.36f),
                size = Size(w * 0.6f, w * 0.54f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.06f),
            )
            // 高光
            drawRoundRect(
                Color.White.copy(alpha = 0.25f),
                topLeft = Offset(cx - w * 0.26f, top + w * 0.4f),
                size = Size(w * 0.08f, w * 0.44f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.04f),
            )
            // 盒盖
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(lighten(b, 0.3f), b), startY = top + w * 0.24f, endY = top + w * 0.38f),
                topLeft = Offset(cx - w * 0.34f, top + w * 0.24f),
                size = Size(w * 0.68f, w * 0.15f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.05f),
            )
            // 丝带
            drawRect(Color(0xFFF6C85F), topLeft = Offset(cx - w * 0.04f, top + w * 0.24f), size = Size(w * 0.08f, w * 0.66f))
            // 蝴蝶结
            val bc = Offset(cx, top + w * 0.22f)
            drawOval(Color(0xFFF6C85F), topLeft = Offset(bc.x - w * 0.16f, bc.y - w * 0.07f), size = Size(w * 0.15f, w * 0.1f))
            drawOval(Color(0xFFF6C85F), topLeft = Offset(bc.x + w * 0.01f, bc.y - w * 0.07f), size = Size(w * 0.15f, w * 0.1f))
            drawCircle(Color(0xFFE2A23A), radius = w * 0.035f, center = bc)
            // 星星装饰
            drawStar(Offset(cx + w * 0.2f, top + w * 0.5f), w * 0.04f, w * 0.015f, Color.White.copy(alpha = 0.9f))
            drawStar(Offset(cx - w * 0.16f, top + w * 0.78f), w * 0.03f, w * 0.011f, Color.White.copy(alpha = 0.8f))
        }
        Text(
            "?",
            color = Color.White,
            fontSize = 34.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 26.dp),
        )
    }
}

/** 系列封面（用于列表）：主题渐变 + 盲盒插画 */
@Composable
fun SeriesCover(themeColor: Long, accentColor: Long, name: String, modifier: Modifier = Modifier) {
    Box(
        modifier.background(Brush.linearGradient(listOf(lighten(Color(themeColor), 0.35f), lighten(Color(accentColor), 0.25f)))),
        contentAlignment = Alignment.Center,
    ) {
        BlindBoxArt(themeColor, accentColor, name, Modifier.fillMaxWidth().padding(10.dp))
    }
}
