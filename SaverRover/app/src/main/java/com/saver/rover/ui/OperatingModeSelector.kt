package com.saver.rover.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saver.rover.domain.OperatingMode

import androidx.compose.foundation.layout.PaddingValues

@Composable
fun OperatingModeSelector(
    currentMode: OperatingMode,
    onModeSelected: (OperatingMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(Color(0xDF0F172A), RoundedCornerShape(12.dp))
            .border(BorderStroke(1.dp, Color(0x3300E5FF)), RoundedCornerShape(12.dp))
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OperatingMode.entries.forEach { mode ->
            val isSelected = currentMode == mode
            ElevatedButton(
                onClick = { onModeSelected(mode) },
                modifier = Modifier.height(30.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = if (isSelected) Color(0xFF00E5FF) else Color.Transparent,
                    contentColor = if (isSelected) Color.Black else Color.White.copy(alpha = 0.7f)
                )
            ) {
                Text(mode.name, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            if (mode != OperatingMode.entries.last()) {
                Spacer(Modifier.width(2.dp))
            }
        }
    }
}
