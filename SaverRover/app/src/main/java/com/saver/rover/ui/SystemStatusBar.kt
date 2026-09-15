package com.saver.rover.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saver.rover.camera.AiStatus
import com.saver.rover.domain.ConnectionState
import com.saver.rover.domain.RoverUiState

@Composable
fun SystemStatusBar(
    uiState: RoverUiState,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val conn = uiState.connection
    val ai = uiState.ai
    var activeDetail by remember { mutableStateOf<String?>(null) }

    val roverColor = when {
        conn.roverConnected -> Color(0xFF22C55E)
        conn.telemetryMessage.contains("Connecting", ignoreCase = true) -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }

    val camColor = when (conn.cameraStatus) {
        "Live" -> Color(0xFF22C55E)
        "Connecting" -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }

    val aiColor = when (ai.status) {
        AiStatus.READY, AiStatus.DETECTING -> Color(0xFF22C55E)
        AiStatus.HUMAN_CONFIRMED -> Color(0xFFEF4444)
        AiStatus.INITIALIZING -> Color(0xFFF59E0B)
        else -> Color(0xFF6B7280)
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier
                .background(Color(0xDF0F172A), RoundedCornerShape(20.dp))
                .border(BorderStroke(1.dp, Color(0x33FFFFFF)), RoundedCornerShape(20.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Rover Status Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable {
                    activeDetail = if (activeDetail == "ROVER") null else "ROVER"
                }
            ) {
                Text("●", color = roverColor, fontSize = 12.sp)
                Spacer(Modifier.width(4.dp))
                Text("ROVER", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Text("|", color = Color.White.copy(alpha = 0.3f), fontSize = 11.sp)

            // Camera Status Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable {
                    activeDetail = if (activeDetail == "CAM") null else "CAM"
                }
            ) {
                Text("●", color = camColor, fontSize = 12.sp)
                Spacer(Modifier.width(4.dp))
                Text("CAM ${ai.cameraFps} FPS", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Text("|", color = Color.White.copy(alpha = 0.3f), fontSize = 11.sp)

            // AI Status Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable {
                    activeDetail = if (activeDetail == "AI") null else "AI"
                }
            ) {
                Text("●", color = aiColor, fontSize = 12.sp)
                Spacer(Modifier.width(4.dp))
                Text("AI ${String.format("%.1f", ai.inferenceFps)} FPS (${ai.averageMs} ms)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Text("|", color = Color.White.copy(alpha = 0.3f), fontSize = 11.sp)

            // Settings Gear Icon
            Text(
                "⚙",
                color = Color(0xFF00E5FF),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onOpenSettings() }
            )
        }

        // Popover Details Card
        activeDetail?.let { detailType ->
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .background(Color(0xF00F172A), RoundedCornerShape(10.dp))
                    .border(BorderStroke(1.dp, Color(0x4400E5FF)), RoundedCornerShape(10.dp))
                    .padding(8.dp)
            ) {
                when (detailType) {
                    "ROVER" -> Text(
                        "ROVER IP: ${conn.roverIp}:8080\nStatus: ${conn.telemetryMessage}\nConnected: ${conn.roverConnected}",
                        color = Color.White, fontSize = 10.sp
                    )
                    "CAM" -> Text(
                        "CAMERA STREAM: http://${conn.camIp}:81/stream\nStatus: ${conn.cameraStatus}\nDisplay FPS: ${ai.cameraFps}",
                        color = Color.White, fontSize = 10.sp
                    )
                    "AI" -> Text(
                        "AI Backend: ${ai.backend}\nAvg Latency: ${ai.averageMs} ms | Max: ${ai.maximumMs} ms\nFrame Age: ${ai.frameAgeMs} ms | Votes: ${ai.positives}/${ai.evaluated}",
                        color = Color.White, fontSize = 10.sp
                    )
                }
            }
        }
    }
}
