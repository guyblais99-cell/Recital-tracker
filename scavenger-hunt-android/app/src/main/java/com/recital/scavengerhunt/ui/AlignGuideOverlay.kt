package com.recital.scavengerhunt.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.recital.scavengerhunt.camera.ImageAlignMatcher
import com.recital.scavengerhunt.ui.theme.HuntColors

@Composable
fun AlignGuideOverlay(
    panX: Float,
    panY: Float,
    matchScore: Float,
    modifier: Modifier = Modifier
) {
    val pulse by rememberInfiniteTransition(label = "alignPulse").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "alignPulseAlpha"
    )
    val showArrows = matchScore < ImageAlignMatcher.MATCH_THRESHOLD + 0.05f
    val guideAlpha = if (showArrows) pulse else 0.85f

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val inset = 28.dp.toPx()
            val arm = 36.dp.toPx()
            val strokeW = 5.dp.toPx()
            val cornerColor = HuntColors.gold.copy(alpha = guideAlpha)

            fun corner(topLeft: Offset, dx: Float, dy: Float) {
                drawLine(
                    color = cornerColor,
                    start = topLeft,
                    end = Offset(topLeft.x + dx * arm, topLeft.y),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = cornerColor,
                    start = topLeft,
                    end = Offset(topLeft.x, topLeft.y + dy * arm),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round
                )
            }

            corner(Offset(inset, inset), 1f, 1f)
            corner(Offset(size.width - inset, inset), -1f, 1f)
            corner(Offset(inset, size.height - inset), 1f, -1f)
            corner(Offset(size.width - inset, size.height - inset), -1f, -1f)
        }

        if (showArrows) {
            val arrowAlpha = guideAlpha
            val arrowColor = Color.White.copy(alpha = arrowAlpha)
            val arrowSize = 52.sp

            if (panX <= -0.22f) {
                AlignArrow("←", Modifier.align(Alignment.CenterStart).padding(start = 8.dp), arrowColor, arrowSize)
            }
            if (panX >= 0.22f) {
                AlignArrow("→", Modifier.align(Alignment.CenterEnd).padding(end = 8.dp), arrowColor, arrowSize)
            }
            if (panY <= -0.22f) {
                AlignArrow("↑", Modifier.align(Alignment.TopCenter).padding(top = 48.dp), arrowColor, arrowSize)
            }
            if (panY >= 0.22f) {
                AlignArrow("↓", Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp), arrowColor, arrowSize)
            }
        }
    }
}

@Composable
private fun AlignArrow(
    symbol: String,
    modifier: Modifier,
    color: Color,
    size: androidx.compose.ui.unit.TextUnit
) {
    Text(
        text = symbol,
        modifier = modifier,
        color = color,
        fontSize = size,
        fontWeight = FontWeight.Black,
        style = MaterialTheme.typography.displaySmall
    )
}
