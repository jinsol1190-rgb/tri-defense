package org.tridefense.android.ui.demo

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

internal val Ink = Color(0xFF1B2A25)
internal val Green = Color(0xFFD5F565)
internal val Lime = Color(0xFFDDF28A)
internal val Paper = Color(0xFF0C1513)
internal val Muted = Color(0xFF9AAAA3)
internal val Rust = Color(0xFFFF6B5D)
internal val Amber = Color(0xFFEAC47A)

@Composable internal fun DemoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Green, onPrimary = Paper,
        primaryContainer = Lime, onPrimaryContainer = Ink, secondary = Green, onSecondary = Paper,
        secondaryContainer = Lime, onSecondaryContainer = Ink, outline = Color(0xFFA8B6AA),
        outlineVariant = Color(0xFFE0E6DD), background = Paper, onBackground = Color.White,
        surface = Ink, onSurface = Color.White, surfaceVariant = Ink,
        onSurfaceVariant = Muted, error = Rust), typography = Typography(
        headlineLarge = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
        headlineMedium = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
        titleLarge = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
    ), content = content)
}

@Composable internal fun Eyebrow(text: String, color: Color = Green) {
    Text(text, color = color, style = MaterialTheme.typography.labelMedium, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
}

@Composable internal fun Heading(kicker: String, title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Eyebrow(kicker)
        Text(title, style = MaterialTheme.typography.headlineLarge, lineHeight = 39.sp)
        Text(subtitle, color = Muted, style = MaterialTheme.typography.bodyMedium, lineHeight = 22.sp)
    }
}

@Composable internal fun DemoCard(modifier: Modifier = Modifier, color: Color = Ink, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), color = color, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

@Composable internal fun Note(text: String) {
    Text(text, color = Muted, style = MaterialTheme.typography.bodySmall, lineHeight = 19.sp)
}

@Composable internal fun Action(text: String, onClick: () -> Unit, enabled: Boolean = true, danger: Boolean = false) {
    Button(onClick, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = enabled,
        shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.buttonColors(containerColor = if (danger) Rust else Green)) {
        Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 5.dp))
    }
}

@Composable internal fun IconDisc(icon: ImageVector = Icons.Outlined.Shield, color: Color = Green, size: Int = 64) {
    Box(Modifier.size(size.dp).background(color.copy(alpha = .10f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size((size / 2).dp), tint = color)
    }
}

@Composable internal fun InfoRow(icon: ImageVector, title: String, body: String, color: Color = Green) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        IconDisc(icon, color, 44)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = Muted, lineHeight = 18.sp)
        }
    }
}


/** Smooth deterministic presentation score. No inference or calibrated probability. */
@Composable internal fun CallRiskGauge(score: Float) {
    val tint = if (score >= 65) Rust else if (score >= 35) Amber else Lime
    Box(Modifier.fillMaxWidth().height(174.dp).semantics {
        contentDescription = "MOCK 위험 게이지 ${score.toInt()} / 100"
    }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(174.dp)) {
            val inset = 12.dp.toPx()
            val arc = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(Muted.copy(alpha = .18f), 140f, 260f, false, Offset(inset, inset), arc,
                style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
            drawArc(tint, 140f, 260f * score / 100f, false, Offset(inset, inset), arc,
                style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${score.toInt()}", fontSize = 46.sp, fontWeight = FontWeight.Bold, color = tint)
            Text("/ 100 · MOCK", color = Muted, fontSize = 12.sp)
        }
    }
}
