package com.adamglin.compose.markdown.sample.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
internal data class SampleChatColors(
    val background: Color,
    val backgroundShade: Color,
    val panel: Color,
    val panelAlt: Color,
    val panelInset: Color,
    val accent: Color,
    val accentSoft: Color,
    val border: Color,
    val borderStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
)

@Immutable
internal data class SampleChatTypography(
    val titleLarge: TextStyle,
    val titleMedium: TextStyle,
    val titleSmall: TextStyle,
    val bodyLarge: TextStyle,
    val bodyMedium: TextStyle,
    val bodySmall: TextStyle,
    val label: TextStyle,
)

private val lightSampleChatColors = SampleChatColors(
    background = Color(0xFFF7F7F5),
    backgroundShade = Color(0xFFE9E9E4),
    panel = Color(0xFFFFFFFF),
    panelAlt = Color(0xFFF1F1EC),
    panelInset = Color(0xFFFCFCFA),
    accent = Color(0xFF111111),
    accentSoft = Color(0xFFE8E8E3),
    border = Color(0xFFD9D9D2),
    borderStrong = Color(0xFF111111),
    textPrimary = Color(0xFF111111),
    textSecondary = Color(0xFF4B4B46),
    textMuted = Color(0xFF7A7A73),
)

private val darkSampleChatColors = SampleChatColors(
    background = Color(0xFF090909),
    backgroundShade = Color(0xFF151515),
    panel = Color(0xFF111111),
    panelAlt = Color(0xFF171717),
    panelInset = Color(0xFF0D0D0D),
    accent = Color(0xFFF5F5F2),
    accentSoft = Color(0xFF242424),
    border = Color(0xFF2C2C2C),
    borderStrong = Color(0xFFF5F5F2),
    textPrimary = Color(0xFFF5F5F2),
    textSecondary = Color(0xFFD0D0CB),
    textMuted = Color(0xFFA0A09B),
)

private val LocalSampleChatColors = staticCompositionLocalOf<SampleChatColors> {
    error("SampleChatColors not provided")
}

private val LocalSampleChatTypography = staticCompositionLocalOf<SampleChatTypography> {
    error("SampleChatTypography not provided")
}

internal object SampleChatTheme {
    val colors: SampleChatColors
        @Composable get() = LocalSampleChatColors.current

    val typography: SampleChatTypography
        @Composable get() = LocalSampleChatTypography.current
}

@Composable
internal fun ProvideSampleChatTheme(
    content: @Composable () -> Unit,
) {
    val colors = if (isSystemInDarkTheme()) darkSampleChatColors else lightSampleChatColors
    androidx.compose.runtime.CompositionLocalProvider(
        LocalSampleChatColors provides colors,
        LocalSampleChatTypography provides rememberSampleChatTypography(colors),
        content = content,
    )
}

@Composable
private fun rememberSampleChatTypography(
    colors: SampleChatColors,
): SampleChatTypography = SampleChatTypography(
    titleLarge = TextStyle(
        color = colors.textPrimary,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        color = colors.textPrimary,
        fontSize = 20.sp,
        lineHeight = 27.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleSmall = TextStyle(
        color = colors.textPrimary,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Medium,
    ),
    bodyLarge = TextStyle(
        color = colors.textPrimary,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        color = colors.textSecondary,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        color = colors.textMuted,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
    label = TextStyle(
        color = colors.textPrimary,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.3.sp,
    ),
)

@Composable
internal fun AppText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = style.color,
    fontWeight: FontWeight? = null,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(
            color = color,
            fontWeight = fontWeight ?: style.fontWeight,
        ),
    )
}

@Composable
internal fun AppPanel(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(20.dp),
    backgroundColor: Color = SampleChatTheme.colors.panel,
    borderColor: Color = SampleChatTheme.colors.border,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .border(width = 1.dp, color = borderColor, shape = shape),
        content = content,
    )
}

@Composable
internal fun AppButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
) {
    val colors = SampleChatTheme.colors
    val backgroundColor = when {
        filled && enabled -> colors.accent
        filled -> colors.border
        enabled -> Color.Transparent
        else -> colors.panelAlt.copy(alpha = 0.55f)
    }
    val borderColor = when {
        filled && enabled -> colors.accent
        filled -> colors.border
        enabled -> colors.borderStrong
        else -> colors.border.copy(alpha = 0.6f)
    }
    val contentColor = when {
        filled && enabled -> colors.panelInset
        enabled -> colors.textPrimary
        else -> colors.textMuted
    }
    val shape = RoundedCornerShape(999.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        AppText(
            text = text,
            style = SampleChatTheme.typography.label,
            color = contentColor,
        )
    }
}

@Composable
internal fun AppSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = SampleChatTheme.colors
    val currentValue = value.coerceIn(valueRange.start, valueRange.endInclusive)
    val rangeSpan = valueRange.endInclusive - valueRange.start
    val fraction = if (rangeSpan == 0f) 0f else (currentValue - valueRange.start) / rangeSpan
    val thumbSize = 20.dp

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp),
    ) {
        val sliderWidthPx = with(LocalDensity.current) { maxWidth.toPx().coerceAtLeast(1f) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .progressSemantics(currentValue, valueRange)
                .pointerInput(enabled, valueRange, sliderWidthPx) {
                    if (!enabled) {
                        return@pointerInput
                    }
                    detectTapGestures { offset ->
                        val tappedFraction = (offset.x / sliderWidthPx).coerceIn(0f, 1f)
                        onValueChange(valueRange.start + (rangeSpan * tappedFraction))
                    }
                }
                .draggable(
                    orientation = Orientation.Horizontal,
                    enabled = enabled,
                    state = rememberDraggableState { delta ->
                        val deltaFraction = delta / sliderWidthPx
                        val nextValue = currentValue + (rangeSpan * deltaFraction)
                        onValueChange(nextValue.coerceIn(valueRange.start, valueRange.endInclusive))
                    },
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(colors.panelAlt),
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(fraction)
                .height(6.dp)
                .clip(CircleShape)
                .background(if (enabled) colors.accent else colors.border),
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = (maxWidth - thumbSize) * fraction)
                .size(thumbSize)
                .clip(CircleShape)
                .background(if (enabled) colors.panelInset else colors.panelAlt)
                .border(
                    width = 2.dp,
                    color = if (enabled) colors.accent else colors.borderStrong,
                    shape = CircleShape,
                ),
        )
    }
}
