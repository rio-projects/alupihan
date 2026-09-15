package com.saver.rover.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.saver.rover.camera.AiStatus
import com.saver.rover.domain.AiState

@Composable
fun AiDetectionOverlay(
    aiState: AiState,
    isHorizontallyFlipped: Boolean = true,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val isConfirmed = aiState.status == AiStatus.HUMAN_CONFIRMED

    Canvas(modifier = modifier.fillMaxSize()) {
        if (aiState.people.isEmpty()) return@Canvas

        val boxColor = if (isConfirmed) Color(0xFFEF4444) else Color(0xFF00E5FF)
        val badgeBg = if (isConfirmed) Color(0xDDCC0022) else Color(0xDD0088AA)

        for (res in aiState.people) {
            val boxLeft = (if (isHorizontallyFlipped) 1f - res.boundingBox.right else res.boundingBox.left) * size.width
            val boxTop = res.boundingBox.top * size.height
            val boxRight = (if (isHorizontallyFlipped) 1f - res.boundingBox.left else res.boundingBox.right) * size.width
            val boxBottom = res.boundingBox.bottom * size.height
            val boxWidth = boxRight - boxLeft
            val boxHeight = boxBottom - boxTop

            // Draw bounding box
            drawRect(
                color = boxColor,
                topLeft = Offset(boxLeft, boxTop),
                size = Size(boxWidth, boxHeight),
                style = Stroke(width = 4f)
            )

            // Draw Label Badge
            val statusLabel = if (isConfirmed) "🚨 HUMAN CONFIRMED" else "🔍 PERSON CANDIDATE"
            val text = "$statusLabel (${(res.confidence * 100).toInt()}%)"
            val textLayout = textMeasurer.measure(
                text = text,
                style = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            )

            val badgeWidth = textLayout.size.width + 16f
            val badgeHeight = textLayout.size.height + 8f

            drawRect(
                color = badgeBg,
                topLeft = Offset(boxLeft, (boxTop - badgeHeight).coerceAtLeast(0f)),
                size = Size(badgeWidth, badgeHeight)
            )

            drawText(
                textLayoutResult = textLayout,
                topLeft = Offset(boxLeft + 8f, (boxTop - badgeHeight + 4f).coerceAtLeast(4f))
            )
        }
    }
}
