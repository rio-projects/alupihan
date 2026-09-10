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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import kotlin.math.abs
import kotlin.math.sqrt

enum class SteeringMode {
    SERVO_STEER,   // Dual wheel drive + servo turning
    SPIN_IN_PLACE  // Tank-style zero-radius spin in place
}

@Composable
fun Joystick(
    modifier: Modifier = Modifier,
    onMove: (leftSpeed: Int, rightSpeed: Int) -> Unit
) {
    var maxSpeedLimit by remember { mutableIntStateOf(190) } // Default MED (190 / 255)
    var steeringMode by remember { mutableStateOf(SteeringMode.SERVO_STEER) }
    var rawKnobOffset by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }
    var lastSendTime by remember { mutableLongStateOf(0L) }

    // Spring back to center smoothly when released
    val animatedKnobOffset by animateOffsetAsState(
        targetValue = if (isDragging) rawKnobOffset else Offset.Zero,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "JoystickKnobSpring"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Mode Selector ABOVE Joystick (Servo Steer vs Spin in Place)
        Row(
            modifier = Modifier
                .background(Color(0xDF0F172A), RoundedCornerShape(10.dp))
                .border(BorderStroke(1.dp, Color(0x4400E5FF)), RoundedCornerShape(10.dp))
                .padding(2.dp)
        ) {
            ElevatedButton(
                onClick = { steeringMode = SteeringMode.SERVO_STEER },
                modifier = Modifier.height(28.dp),
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = if (steeringMode == SteeringMode.SERVO_STEER) Color(0xFF00E5FF) else Color.Transparent,
                    contentColor = if (steeringMode == SteeringMode.SERVO_STEER) Color.Black else Color.White
                )
            ) {
                Text("⚙️ SERVO", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.width(3.dp))

            ElevatedButton(
                onClick = { steeringMode = SteeringMode.SPIN_IN_PLACE },
                modifier = Modifier.height(28.dp),
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = if (steeringMode == SteeringMode.SPIN_IN_PLACE) Color(0xFFFF9100) else Color.Transparent,
                    contentColor = if (steeringMode == SteeringMode.SPIN_IN_PLACE) Color.Black else Color.White
                )
            ) {
                Text("🔄 SPIN IN PLACE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(4.dp))

        // 2. Speed Limits Bar
        Row(
            modifier = Modifier
                .background(Color(0xDF0F172A), RoundedCornerShape(8.dp))
                .padding(horizontal = 3.dp, vertical = 2.dp)
        ) {
            listOf("LOW" to 120, "MED" to 190, "HIGH" to 255).forEach { (label, speed) ->
                val selected = maxSpeedLimit == speed
                ElevatedButton(
                    onClick = { maxSpeedLimit = speed },
                    modifier = Modifier.height(24.dp),
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = if (selected) Color(0xFF00E676) else Color.Transparent,
                        contentColor = if (selected) Color.Black else Color.White.copy(alpha = 0.8f)
                    )
                ) {
                    Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(2.dp))
            }
        }

        Spacer(Modifier.height(4.dp))

        // 3. Tactile 3D Touch Joystick
        Canvas(
            modifier = Modifier
                .size(110.dp)
                .background(Color(0xCC0F172A), CircleShape)
                .pointerInput(maxSpeedLimit, steeringMode) {
                    fun processMove(position: Offset, forceSend: Boolean = false) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val raw = position - center
                        val maxRadius = (minOf(size.width, size.height) / 2f) - 20f
                        val length = sqrt(raw.x * raw.x + raw.y * raw.y).coerceAtLeast(0.001f)
                        val scale = minOf(1f, maxRadius / length)
                        
                        val constrainedOffset = Offset(raw.x * scale, raw.y * scale)
                        rawKnobOffset = constrainedOffset

                        val now = System.currentTimeMillis()
                        if (!forceSend && now - lastSendTime < 40L) return // 25 Hz update throttle
                        lastSendTime = now

                        val normX = (constrainedOffset.x / maxRadius).coerceIn(-1f, 1f)
                        val normY = (-constrainedOffset.y / maxRadius).coerceIn(-1f, 1f)

                        val leftVal: Int
                        val rightVal: Int

                        if (steeringMode == SteeringMode.SPIN_IN_PLACE) {
                            // Tank Spin Mode: if turning left/right, spin wheels opposite
                            if (abs(normX) > abs(normY)) {
                                leftVal = (normX * maxSpeedLimit).toInt().coerceIn(-255, 255)
                                rightVal = (-normX * maxSpeedLimit).toInt().coerceIn(-255, 255)
                            } else {
                                leftVal = (normY * maxSpeedLimit).toInt().coerceIn(-255, 255)
                                rightVal = (normY * maxSpeedLimit).toInt().coerceIn(-255, 255)
                            }
                        } else {
                            // Standard Servo Steering Mode: Throttle + Differential Steering
                            val throttle = normY
                            val steering = normX
                            leftVal = ((throttle + steering) * maxSpeedLimit).toInt().coerceIn(-255, 255)
                            rightVal = ((throttle - steering) * maxSpeedLimit).toInt().coerceIn(-255, 255)
                        }

                        onMove(leftVal, rightVal)
                    }

                    detectDragGestures(
                        onDragStart = {
                            isDragging = true
                            processMove(it, forceSend = true)
                        },
                        onDragEnd = {
                            isDragging = false
                            rawKnobOffset = Offset.Zero
                            lastSendTime = 0L
                            onMove(0, 0)
                        },
                        onDragCancel = {
                            isDragging = false
                            rawKnobOffset = Offset.Zero
                            lastSendTime = 0L
                            onMove(0, 0)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            processMove(change.position)
                        }
                    )
                }
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerRadius = size.minDimension / 2f

            // Outer Metallic Ring with Neon Accent
            drawCircle(
                color = if (steeringMode == SteeringMode.SPIN_IN_PLACE) Color(0xFFFF9100) else Color(0xFF00E5FF),
                radius = outerRadius - 2f,
                style = Stroke(width = 2.5.dp.toPx())
            )

            // Inner Crosshair Guidelines
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

            // Vector Trail Line connecting center to knob
            val currentKnobOffset = if (isDragging) rawKnobOffset else animatedKnobOffset
            val currentKnob = center + currentKnobOffset
            if (currentKnobOffset != Offset.Zero) {
                drawLine(
                    color = if (steeringMode == SteeringMode.SPIN_IN_PLACE) Color(0xFFFF9100).copy(alpha = 0.7f) else Color(0xFF00E676).copy(alpha = 0.7f),
                    start = center,
                    end = currentKnob,
                    strokeWidth = 3.dp.toPx()
                )
            }

            // Outer Center Ring
            drawCircle(
                color = Color.White.copy(alpha = 0.15f),
                center = center,
                radius = outerRadius / 3f
            )

            // Interactive Metallic Knob Handle
            val knobRadius = 20.dp.toPx()
            val knobColor = if (isDragging) {
                if (steeringMode == SteeringMode.SPIN_IN_PLACE) Color(0xFFFF9100) else Color(0xFF00E676)
            } else {
                Color(0xFF38BDF8)
            }

            // Knob Outer Shadow
            drawCircle(
                color = Color.Black.copy(alpha = 0.4f),
                center = currentKnob + Offset(0f, 4f),
                radius = knobRadius
            )

            // Knob Base
            drawCircle(
                color = knobColor,
                center = currentKnob,
                radius = knobRadius
            )

            // Knob Core Inner Dot
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
fun JoystickPreview() {
    Joystick { _, _ -> }
}


