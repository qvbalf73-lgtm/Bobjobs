package com.boxverse.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boxverse.app.domain.Rarity
import com.boxverse.app.ui.theme.LocalBoxColors
import com.boxverse.app.ui.theme.RarityStyle

/** 通用界面状态 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Empty(val message: String) : UiState<Nothing>
    data class Error(val message: String) : UiState<Nothing>
}

/* ---------------- 布局容器 ---------------- */

/** 带返回按钮的二级页面骨架 */
@Composable
fun SubScreen(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回") }
                },
                actions = actions,
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        floatingActionButton = floatingActionButton,
        content = content,
    )
}

/** 柔和渐变背景 */
@Composable
fun SoftBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier.background(
            Brush.verticalGradient(
                0f to scheme.primaryContainer.copy(alpha = 0.55f),
                0.35f to scheme.background,
                1f to scheme.background,
            )
        )
    ) { content() }
}

/** 品牌卡片：大圆角 + 柔和阴影 */
@Composable
fun BoxCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(24.dp),
    color: Color = MaterialTheme.colorScheme.surface,
    brush: Brush? = null,
    elevation: Dp = 6.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val glow = LocalBoxColors.current.cardGlow
    val base = modifier
        .shadow(elevation, shape, ambientColor = glow, spotColor = glow)
        .clip(shape)
        .let { if (brush != null) it.background(brush) else it.background(color) }
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
        .padding(contentPadding)
    Column(base, content = content)
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/* ---------------- 状态组件 ---------------- */

@Composable
fun LoadingState(modifier: Modifier = Modifier, text: String = "加载中…") {
    Column(modifier.fillMaxWidth().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(88.dp).clip(CircleShape)
                .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            GradientButton(actionLabel, onClick = onAction)
        }
    }
}

@Composable
fun ErrorState(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    EmptyState(Icons.Rounded.ErrorOutline, "出了点小问题", message, modifier, if (onRetry != null) "重试" else null, onRetry)
}

/* ---------------- 按钮与标签 ---------------- */

@Composable
fun GradientButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    colors: List<Color> = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary),
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .shadow(if (enabled) 8.dp else 0.dp, shape, ambientColor = colors.first().copy(alpha = 0.4f), spotColor = colors.first().copy(alpha = 0.4f))
            .clip(shape)
            .background(if (enabled) Brush.horizontalGradient(colors) else Brush.horizontalGradient(listOf(Color.Gray.copy(alpha = 0.35f), Color.Gray.copy(alpha = 0.35f))))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun RarityBadge(rarity: Rarity, modifier: Modifier = Modifier, showLabel: Boolean = false) {
    Box(
        modifier.clip(RoundedCornerShape(50)).background(RarityStyle.brush(rarity)).padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (showLabel) rarity.fullLabel else rarity.code,
            color = if (rarity == Rarity.R) Color(0xFF4A4266) else Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primaryContainer, contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer) {
    Box(modifier.clip(RoundedCornerShape(50)).background(color).padding(horizontal = 10.dp, vertical = 3.dp)) {
        Text(text, color = contentColor, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
fun DemoTag(modifier: Modifier = Modifier) {
    Pill("演示", modifier, color = Color(0x33FFB020), contentColor = Color(0xFFB57300))
}

/** 选择标签（筛选） */
@Composable
fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Brush.horizontalGradient(listOf(scheme.primary, scheme.secondary)) else Brush.horizontalGradient(listOf(scheme.surface, scheme.surface)))
            .border(BorderStroke(1.dp, if (selected) Color.Transparent else scheme.outlineVariant), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(text, color = if (selected) Color.White else scheme.onSurface, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/** 分段标签页 */
@Composable
fun PillTabs(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier.clip(RoundedCornerShape(50)).background(scheme.surfaceContainerHighest.copy(alpha = 0.7f)).padding(4.dp),
    ) {
        tabs.forEachIndexed { i, t ->
            val sel = i == selected
            Box(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (sel) scheme.surface else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    t,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (sel) scheme.primary else scheme.onSurfaceVariant,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}

/* ---------------- 进度 ---------------- */

@Composable
fun GradientProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    colors: List<Color> = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary),
) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700), label = "progress")
    Box(modifier.height(height).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        Box(
            Modifier.fillMaxWidth(animated).height(height).clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(colors))
        )
    }
}

@Composable
fun ProgressRing(
    fraction: Float,
    modifier: Modifier = Modifier,
    stroke: Dp = 6.dp,
    colors: List<Color> = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.primary),
    content: @Composable () -> Unit = {},
) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(800), label = "ring")
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val d = size.minDimension - s
            val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
            drawArc(track, 0f, 360f, false, topLeft = tl, size = Size(d, d), style = Stroke(s))
            drawArc(Brush.sweepGradient(colors), -90f, 360f * animated, false, topLeft = tl, size = Size(d, d), style = Stroke(s, cap = StrokeCap.Round))
        }
        content()
    }
}

/* ---------------- 表单 ---------------- */

@Composable
fun BoxTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    error: String? = null,
    suffix: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        suffix = suffix?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth(),
    )
}

/** 下拉选择框 */
@Composable
fun <T> SelectField(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = optionLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, null) },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.matchParentSize().clip(RoundedCornerShape(16.dp)).clickable { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(text = { Text(optionLabel(opt)) }, onClick = {
                    onSelect(opt)
                    expanded = false
                })
            }
        }
    }
}

/** 带建议的输入框（可从已有名称中选择，也可直接输入新名称） */
@Composable
fun SuggestField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    suggestions: List<String>,
    modifier: Modifier = Modifier,
    error: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val filtered = remember(value, suggestions) {
        suggestions.filter { it.contains(value.trim(), ignoreCase = true) && it != value }.take(8)
    }
    Box(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text(label) },
            singleLine = true,
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            trailingIcon = if (suggestions.isNotEmpty()) {
                { IconButton(onClick = { expanded = !expanded }) { Icon(Icons.Rounded.ArrowDropDown, "选择已有") } }
            } else null,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(
            expanded = expanded && filtered.isNotEmpty(),
            onDismissRequest = { expanded = false },
            properties = androidx.compose.ui.window.PopupProperties(focusable = false),
        ) {
            filtered.forEach { s ->
                DropdownMenuItem(text = { Text(s) }, onClick = {
                    onValueChange(s)
                    expanded = false
                })
            }
        }
    }
}

@Composable
fun QuantityStepper(value: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier, min: Int = 1, max: Int = 999) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier.clip(RoundedCornerShape(50)).background(scheme.surfaceContainerHigh).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { if (value > min) onChange(value - 1) }, enabled = value > min, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.Remove, "减少")
        }
        Text("$value", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)
        IconButton(onClick = { if (value < max) onChange(value + 1) }, enabled = value < max, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.Add, "增加")
        }
    }
}

@Composable
fun StarRating(rating: Int, onChange: ((Int) -> Unit)? = null, modifier: Modifier = Modifier, size: Dp = 28.dp) {
    Row(modifier) {
        for (i in 1..5) {
            val on = i <= rating
            Icon(
                if (on) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                contentDescription = "$i 星",
                tint = if (on) LocalBoxColors.current.gold else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(size).let { m ->
                    if (onChange != null) m.clip(CircleShape).clickable { onChange(if (rating == i) 0 else i) } else m
                },
            )
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = "确定",
    danger: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) { Text(confirmLabel, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        shape = RoundedCornerShape(28.dp),
    )
}

/* ---------------- 卡片 ---------------- */

/** 带稀有度光效边框的形象框 */
@Composable
fun RarityFrame(
    rarity: Rarity,
    modifier: Modifier = Modifier,
    owned: Boolean = true,
    shape: Shape = RoundedCornerShape(20.dp),
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val glowColor = RarityStyle.main(rarity)
    val elevation = if (!owned) 0.dp else when (rarity) {
        Rarity.R -> 2.dp
        Rarity.SR -> 6.dp
        Rarity.SSR -> 12.dp
        Rarity.UR -> 16.dp
    }
    Box(
        modifier
            .shadow(elevation, shape, ambientColor = glowColor, spotColor = glowColor)
            .clip(shape)
            .background(
                if (owned) Brush.verticalGradient(listOf(scheme.surface, RarityStyle.colors(rarity)[1].copy(alpha = 0.25f)))
                else Brush.verticalGradient(listOf(scheme.surfaceContainerHigh, scheme.surfaceContainerHigh))
            )
            .border(
                if (owned) RarityStyle.borderWidth(rarity) else 1.dp,
                if (owned) RarityStyle.brush(rarity) else Brush.linearGradient(listOf(scheme.outlineVariant, scheme.outlineVariant)),
                shape,
            ),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** 图鉴中的款式卡片 */
@Composable
fun FigureTile(
    name: String,
    subtitle: String?,
    rarity: Rarity,
    seed: Int,
    colorA: Long,
    colorB: Long,
    imagePath: String?,
    owned: Boolean,
    count: Int,
    modifier: Modifier = Modifier,
    isDemo: Boolean = false,
    revealHidden: Boolean = true,
    onClick: () -> Unit,
) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        RarityFrame(rarity, Modifier.fillMaxWidth().aspectRatio(0.86f), owned = owned) {
            FigureArt(seed, colorA, colorB, rarity, Modifier.fillMaxSize().padding(6.dp), imagePath = imagePath, silhouette = !owned)
            Box(Modifier.fillMaxSize().padding(6.dp)) {
                if (owned || revealHidden || !rarity.isHidden) RarityBadge(rarity, Modifier.align(Alignment.TopStart))
                if (count > 1) {
                    Box(
                        Modifier.align(Alignment.TopEnd).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary).padding(horizontal = 7.dp, vertical = 1.dp)
                    ) { Text("×$count", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                }
                if (isDemo) DemoTag(Modifier.align(Alignment.BottomEnd))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (!owned && rarity.isHidden && !revealHidden) "？？？" else name,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (owned) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 统计小方块 */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, icon: ImageVector? = null, tint: Color = MaterialTheme.colorScheme.primary) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)).padding(12.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(6.dp))
        }
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** 设置/菜单列表项 */
@Composable
fun MenuRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) trailing()
    }
}

@Composable
fun InfoNote(text: String, modifier: Modifier = Modifier, icon: ImageVector = Icons.Rounded.ErrorOutline) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)).padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
    }
}

@Composable
fun TonalAction(text: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(16.dp)) {
        Icon(icon, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text)
    }
}

@Composable
fun KeyValueRow(key: String, value: String, modifier: Modifier = Modifier, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(key, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = valueColor, textAlign = TextAlign.End)
    }
}

@Composable
fun SurfaceSection(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
    ) { Column(content = content) }
}
