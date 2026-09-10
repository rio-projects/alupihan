package com.alupihan.rover.controls

import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Composable
fun GimbalControls(
    modifier: Modifier = Modifier,
    onPanTiltChange: (pan: Int, tilt: Int) -> Unit
) {
    var panAngle by remember { mutableFloatStateOf(90f) }
    var tiltAngle by remember { mutableFloatStateOf(30f) }
    var rawKnobOffset by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }
    var lastTickTime by remember { mutableLongStateOf(0L) }

    // Spring handle back to center visually on screen when released
    val animatedKnobOffset by animateOffsetAsState(
        targetValue = if (isDragging) rawKnobOffset else Offset.Zero,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "GimbalKnobSpring"
    )

    // Continuous tick loop when dragging joystick (Velocity Nudge Mode - Camera Position Retention)
    LaunchedEffect(isDragging, rawKnobOffset) {
        if (isDragging) {
            lastTickTime = System.currentTimeMillis()
            while (isActive) {
                val now = System.currentTimeMillis()
                val dt = if (lastTickTime == 0L) 0.03f else ((now - lastTickTime) / 1000f).coerceIn(0.005f, 0.1f)
                lastTickTime = now

                val maxRadius = 35f
                var normX = (rawKnobOffset.x / maxRadius).coerceIn(-1f, 1f)
                var normY = (-rawKnobOffset.y / maxRadius).coerceIn(-1f, 1f)

                // Deadzone check to prevent drift
                if (abs(normX) < 0.10f) normX = 0f
                if (abs(normY) < 0.10f) normY = 0f

                // Speed scale: 120 deg/sec pan, 90 deg/sec tilt
                val panSpeed = 120f
                val tiltSpeed = 90f

                val newPan = (panAngle + normX * panSpeed * dt).coerceIn(0f, 180f)
                val newTilt = (tiltAngle + normY * tiltSpeed * dt).coerceIn(25f, 160f)

                if (newPan != panAngle || newTilt != tiltAngle) {
                    panAngle = newPan
                    tiltAngle = newTilt
                    onPanTiltChange(panAngle.roundToInt(), tiltAngle.roundToInt())
                }

                delay(30) // ~33 Hz update rate
            }
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Center Reset Button & Angle Header
        Row(
            modifier = Modifier
                .background(Color(0xDF0F172A), RoundedCornerShape(10.dp))
                .border(BorderStroke(1.dp, Color(0x4400E5FF)), RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "📷 GIMBAL (${panAngle.roundToInt()}° / ${tiltAngle.roundToInt()}°)",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.size(6.dp))
            ElevatedButton(
                onClick = {
                    rawKnobOffset = Offset.Zero
                    panAngle = 90f
                    tiltAngle = 30f
                    onPanTiltChange(90, 30)
                },
                modifier = Modifier.height(28.dp),
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = Color(0xFF00E5FF),
                    contentColor = Color.Black
                )
            ) {
                Text("RESET (30°)", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }


        Spacer(Modifier.height(4.dp))

        // PS5-Style Touch Analog Joystick for Pan-Tilt Camera Control
        Canvas(
            modifier = Modifier
                .size(110.dp)
                .background(Color(0xCC0F172A), CircleShape)
                .pointerInput(Unit) {
                    fun processTouch(position: Offset) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val raw = position - center
                        val maxRadius = (minOf(size.width, size.height) / 2f) - 20f
                        val length = sqrt(raw.x * raw.x + raw.y * raw.y).coerceAtLeast(0.001f)
                        val scale = minOf(1f, maxRadius / length)

                        rawKnobOffset = Offset(raw.x * scale, raw.y * scale)
                    }

                    detectDragGestures(
                        onDragStart = {
                            isDragging = true
                            processTouch(it)
                        },
                        onDragEnd = {
                            isDragging = false
                            rawKnobOffset = Offset.Zero
                            // Position Retention: Camera STAYS aimed at current panAngle & tiltAngle!
                        },
                        onDragCancel = {
                            isDragging = false
                            rawKnobOffset = Offset.Zero
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            processTouch(change.position)
                        }
                    )
                }
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerRadius = size.minDimension / 2f

            // Outer Neon Accent Ring (Cyan)
            drawCircle(
                color = Color(0xFF00E5FF),
                radius = outerRadius - 2f,
                style = Stroke(width = 2.5.dp.toPx())
            )

            // Inner Crosshairs
            val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
            drawLine(
                color = Color.White.copy(alpha = 0.2f),
                start = Offset(center.x, 15f),
                end = Offset(center.x, size.height - 15f),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dash
            )
            drawLine(
                color = Color.White.copy(alpha = 0.2f),
                start = Offset(15f, center.y),
                end = Offset(size.width - 15f, center.y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dash
            )

            // Dynamic Knob Offset
            val currentKnobOffset = if (isDragging) rawKnobOffset else animatedKnobOffset
            val currentKnob = center + currentKnobOffset

            if (currentKnobOffset != Offset.Zero) {
                drawLine(
                    color = Color(0xFF00E5FF).copy(alpha = 0.7f),
                    start = center,
                    end = currentKnob,
                    strokeWidth = 3.dp.toPx()
                )
            }

            // Knob Outer Shadow
            val knobRadius = 20.dp.toPx()
            drawCircle(
                color = Color.Black.copy(alpha = 0.4f),
                center = currentKnob + Offset(0f, 4f),
                radius = knobRadius
            )

            // Knob Handle Base
            drawCircle(
                color = if (isDragging) Color(0xFF00E5FF) else Color(0xFF38BDF8),
                center = currentKnob,
                radius = knobRadius
            )

            // Knob Inner Core Dot
            drawCircle(
                color = Color.White,
                center = currentKnob,
                radius = knobRadius / 3f
            )
        }
    }
}

@Preview
@Composable
fun GimbalControlsPreview() {
    GimbalControls { _, _ -> }
}

