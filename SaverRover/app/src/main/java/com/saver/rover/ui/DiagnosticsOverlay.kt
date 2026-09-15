package com.saver.rover.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saver.rover.domain.RoverUiState

@Composable
fun DiagnosticsOverlay(
    uiState: RoverUiState,
    modifier: Modifier = Modifier
) {
    val ai = uiState.ai
    val conn = uiState.connection
    val rescue = uiState.rescue

    Column(
        modifier = modifier
            .background(Color(0xEE0F172A), RoundedCornerShape(12.dp))
            .border(BorderStroke(1.dp, Color(0x4400E5FF)), RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Text("📊 DIAGNOSTICS & REAL-TIME PERFORMANCE", color = Color(0xFF00E5FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Camera Stream: ${ai.cameraFps} FPS | Stream Status: ${conn.cameraStatus}", color = Color.White, fontSize = 10.sp)
        Text("AI Inference: ${String.format("%.1f", ai.inferenceFps)} FPS | Backend: ${ai.backend}", color = Color.White, fontSize = 10.sp)
        Text("Latency: avg ${ai.averageMs} ms | max ${ai.maximumMs} ms | frame age ${ai.frameAgeMs} ms", color = Color.White, fontSize = 10.sp)
        Text("Temporal Filter: ${ai.positives} / ${ai.evaluated} positive votes", color = Color.White, fontSize = 10.sp)
        Text("Control Mode: ${rescue.mode.name} | Rover WS: ${conn.telemetryMessage}", color = Color.White, fontSize = 10.sp)
        if (ai.deviceMetrics.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text("System: ${ai.deviceMetrics}", color = Color.Yellow, fontSize = 9.sp)
        }
    }
}
