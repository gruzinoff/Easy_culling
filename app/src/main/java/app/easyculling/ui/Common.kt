package app.easyculling.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.easyculling.Thumb
import app.easyculling.app
import app.easyculling.data.Photo
import coil3.compose.AsyncImage
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

val Accent = Color(0xFFFFC94A)
val FilterGreen = Color(0xFF4CD964)

val AppColors: ColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color.Black,
    secondary = Accent,
    background = Color(0xFF0C0C0E),
    surface = Color(0xFF141417),
    surfaceContainer = Color(0xFF1B1B1F),
    surfaceContainerHigh = Color(0xFF232328),
)

/** Lightroom label colours, index = label id - 1. */
val LabelColors = listOf(
    Color(0xFFE53935), Color(0xFFFDD835), Color(0xFF43A047), Color(0xFF1E88E5), Color(0xFF8E24AA),
)
val LabelNames = listOf("Red", "Yellow", "Green", "Blue", "Purple")
fun labelColor(id: Int): Color? = LabelColors.getOrNull(id - 1)

private fun starPath(cx: Float, cy: Float, outer: Float): Path {
    val inner = outer * 0.45f
    val p = Path()
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) outer else inner
        val a = -PI / 2 + i * PI / 5
        val x = cx + (r * cos(a)).toFloat()
        val y = cy + (r * sin(a)).toFloat()
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    return p
}

@Composable
fun StarGlyph(filled: Boolean, size: Dp, modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier.size(size)) {
        val stroke = 1.8.dp.toPx()
        val outer = this.size.minDimension / 2 - stroke
        val path = starPath(this.size.width / 2, this.size.height / 2 + outer * 0.08f, outer)
        drawPath(path, Color.Black.copy(alpha = 0.25f), style = Stroke(stroke * 2.2f, join = StrokeJoin.Round))
        if (filled) drawPath(path, color, style = Fill)
        drawPath(path, color, style = Stroke(stroke, join = StrokeJoin.Round))
    }
}

/**
 * One-tap rating: tapping star N sets rating N. Tapping the current rating again clears it.
 */
@Composable
fun StarBar(
    rating: Int,
    onRate: (Int) -> Unit,
    modifier: Modifier = Modifier,
    starSize: Dp = 34.dp,
    touchSize: Dp = 52.dp,
) {
    val haptic = LocalHapticFeedback.current
    val prefs = LocalContext.current.app.prefs
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        for (n in 1..5) {
            Box(
                Modifier
                    .size(touchSize)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        if (prefs.haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onRate(n)
                    },
                contentAlignment = Alignment.Center,
            ) {
                StarGlyph(filled = n <= rating, size = starSize)
            }
        }
    }
}

@Composable
fun LabelDot(id: Int, size: Dp, modifier: Modifier = Modifier) {
    val c = labelColor(id) ?: return
    Box(modifier.size(size).background(c, CircleShape).border(1.dp, Color.Black.copy(alpha = 0.4f), CircleShape))
}

@Composable
fun PhotoThumb(photo: Photo, modifier: Modifier = Modifier) {
    AsyncImage(
        model = Thumb(photo.mediaId, photo.path),
        contentDescription = photo.name,
        contentScale = ContentScale.Crop,
        modifier = modifier.background(Color(0xFF1A1A1D)),
    )
}

@Composable
fun RatingBadge(rating: Int, modifier: Modifier = Modifier) {
    if (rating <= 0) return
    Row(
        modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$rating", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        StarGlyph(filled = true, size = 11.dp, modifier = Modifier.padding(start = 2.dp), color = Accent)
    }
}

@Composable
fun RawBadge(modifier: Modifier = Modifier) {
    Text(
        "RAW",
        color = Color.White,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

@Composable
fun SelectBox(selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(22.dp)
            .background(if (selected) Accent else Color.Black.copy(alpha = 0.35f), RoundedCornerShape(5.dp))
            .border(1.5.dp, if (selected) Accent else Color.White, RoundedCornerShape(5.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Filled.Check, null, tint = Color.Black, modifier = Modifier.size(17.dp))
    }
}

/** Bottom-bar action: icon with a small caption. */
@Composable
fun BarAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Color.White,
) {
    Column(
        modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp)
            .alpha(if (enabled) 1f else 0.35f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(24.dp))
        Text(label, color = tint, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
fun EasyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AppColors, content = content)
}

