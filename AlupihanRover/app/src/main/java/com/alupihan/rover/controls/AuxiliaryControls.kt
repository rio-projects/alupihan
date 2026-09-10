package com.alupihan.rover.controls

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AuxiliaryControls(
    modifier: Modifier = Modifier,
    onHornState: (enabled: Boolean) -> Unit,
    onTowerProAngle: (angle: Int) -> Unit,
    onRelayToggle: (enabled: Boolean) -> Unit
) {
    var towerProAngle by remember { mutableIntStateOf(90) }
    var hornActive by remember { mutableStateOf(false) }
    var relayActive by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .background(Color(0xDF0F172A), RoundedCornerShape(12.dp))
            .border(BorderStroke(1.dp, Color(0x44FF9100)), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Relay ON/OFF Toggle Button (GPIO 2)
        ElevatedButton(
            onClick = {
                relayActive = !relayActive
                onRelayToggle(relayActive)
            },
            modifier = Modifier.height(30.dp),
            colors = ButtonDefaults.elevatedButtonColors(
                containerColor = if (relayActive) Color(0xFFFFD600) else Color(0xFF37474F),
                contentColor = if (relayActive) Color.Black else Color.White
            )
        ) {
            Text(if (relayActive) "💡 RELAY ON" else "💡 RELAY OFF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.width(8.dp))

        // Active Piezo Horn Button (Press to Sound)
        ElevatedButton(
            onClick = {},
            modifier = Modifier
                .height(30.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            hornActive = true
                            onHornState(true)
                            tryAwaitRelease()
                            hornActive = false
                            onHornState(false)
                        }
                    )
                },
            colors = ButtonDefaults.elevatedButtonColors(
                containerColor = if (hornActive) Color(0xFFFF3D00) else Color(0xFF37474F),
                contentColor = Color.White
            )
        ) {
            Text("📢 HORN", fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.width(8.dp))

        // TowerPro Servo Angle Controls
        Text("SERVO:", color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(4.dp))
        listOf(0, 90, 180).forEach { angle ->
            val selected = towerProAngle == angle
            FilledTonalButton(
                onClick = {
                    towerProAngle = angle
                    onTowerProAngle(angle)
                },
                modifier = Modifier.height(28.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (selected) Color(0xFF00E676) else Color(0xFF1E293B),
                    contentColor = if (selected) Color.Black else Color.White
                )
            ) {
                Text("${angle}°", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(3.dp))
        }
    }
}

@Preview
@Composable
fun AuxiliaryControlsPreview() {
    AuxiliaryControls(onHornState = {}, onTowerProAngle = {}, onRelayToggle = {})
}




