package com.saver.rover.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CameraHud(
    cameraStatus: String,
    fps: Int,
    telemetry: String,
    roverIp: String,
    camIp: String,
    onIpChanged: (roverIp: String, camIp: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showIpDialog by remember { mutableStateOf(false) }
    var inputRoverIp by remember { mutableStateOf(roverIp) }
    var inputCamIp by remember { mutableStateOf(camIp) }

    Column(
        modifier = modifier
            .background(Color(0xDF0F172A), RoundedCornerShape(12.dp))
            .border(BorderStroke(1.dp, Color(0x4400E5FF)), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("📹 $fps FPS", color = Color(0xFF00E5FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "⚙️ CONFIG IP",
                color = Color(0xFF00E676),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(Color(0x3300E676), RoundedCornerShape(6.dp))
                    .border(BorderStroke(1.dp, Color(0x6600E676)), RoundedCornerShape(6.dp))
                    .clickable {
                        inputRoverIp = roverIp
                        inputCamIp = camIp
                        showIpDialog = true
                    }
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = "ROVER: $telemetry",
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }

    if (showIpDialog) {
        AlertDialog(
            onDismissRequest = { showIpDialog = false },
            title = { Text("Set Device IP Addresses", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Enter IPs from Arduino Serial Monitor:", fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = inputRoverIp,
                        onValueChange = { inputRoverIp = it },
                        singleLine = true,
                        label = { Text("Main ESP32 IP (WebSockets :8080)") }
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = inputCamIp,
                        onValueChange = { inputCamIp = it },
                        singleLine = true,
                        label = { Text("ESP32-CAM IP (Stream :81)") }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onIpChanged(inputRoverIp.trim(), inputCamIp.trim())
                    showIpDialog = false
                }) {
                    Text("SAVE & CONNECT", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showIpDialog = false }) { Text("CANCEL") }
            }
        )
    }
}

@Preview
@Composable
fun CameraHudPreview() {
    CameraHud(
        cameraStatus = "Live",
        fps = 30,
        telemetry = "Connected",
        roverIp = "192.168.4.1",
        camIp = "192.168.4.1",
        onIpChanged = { _, _ -> }
    )
}




