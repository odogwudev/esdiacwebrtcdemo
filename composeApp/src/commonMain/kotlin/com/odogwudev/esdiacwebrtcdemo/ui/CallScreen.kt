package com.odogwudev.esdiacwebrtcdemo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CallScreen(
    callState: CallUiState,
    onMuteToggle: () -> Unit,
    onSpeakerToggle: () -> Unit,
    onDialpadToggle: () -> Unit,
    onDtmfDigit: (String) -> Unit,
    onHoldToggle: () -> Unit,
    onEndCall: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        // Destination info
        Text(
            text = "Calling: ${callState.destinationNumber}",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Call status
        val statusText = when {
            callState.isOnHold -> "On Hold"
            else -> when (callState.callPhase) {
                CallPhase.Idle -> "Initializing..."
                CallPhase.Connecting -> "Connecting..."
                CallPhase.Calling -> "Calling..."
                CallPhase.Ringing -> "Ringing..."
                CallPhase.Connected -> "Connected"
                CallPhase.Ended -> "Call Ended"
                CallPhase.Error -> "Error"
            }
        }
        val statusColor = when {
            callState.isOnHold -> Color(0xFFFF9800)
            else -> when (callState.callPhase) {
                CallPhase.Connected -> Color(0xFF4CAF50)
                CallPhase.Ringing -> Color(0xFF2196F3)
                CallPhase.Error -> Color(0xFFF44336)
                CallPhase.Ended -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        }
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyLarge,
            color = statusColor,
            fontWeight = FontWeight.Medium
        )
        if (callState.callPhase == CallPhase.Connected) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = formatDuration(callState.connectedDurationSeconds),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Error message
        callState.errorMessage?.let { error ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        if (callState.isDialpadVisible) {
            // DTMF Dialpad
            DialpadGrid(onDigit = onDtmfDigit)
        } else {
            // Audio indicator
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when {
                        callState.isOnHold -> "ON HOLD"
                        callState.isMuted -> "MUTED"
                        else -> "ON AIR"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Call controls — top row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mute button
            CallControlButton(
                label = if (callState.isMuted) "OFF" else "MIC",
                caption = if (callState.isMuted) "Unmute" else "Mute",
                color = if (callState.isMuted) Color(0xFFF44336) else MaterialTheme.colorScheme.secondaryContainer,
                onClick = onMuteToggle
            )

            // Keypad button
            CallControlButton(
                label = "###",
                caption = "Keypad",
                color = if (callState.isDialpadVisible) Color(0xFF2196F3) else MaterialTheme.colorScheme.secondaryContainer,
                onClick = onDialpadToggle
            )

            // Hold button
            CallControlButton(
                label = if (callState.isOnHold) "RSM" else "HLD",
                caption = if (callState.isOnHold) "Resume" else "Hold",
                color = if (callState.isOnHold) Color(0xFFFF9800) else MaterialTheme.colorScheme.secondaryContainer,
                onClick = onHoldToggle
            )

            // Speaker button
            CallControlButton(
                label = if (callState.isSpeakerOn) "SPK" else "EAR",
                caption = if (callState.isSpeakerOn) "Speaker On" else "Speaker Off",
                color = if (callState.isSpeakerOn) Color(0xFF4CAF50) else MaterialTheme.colorScheme.secondaryContainer,
                onClick = onSpeakerToggle
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // End call — bottom row
        Column(
            modifier = Modifier.padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FilledIconButton(
                onClick = onEndCall,
                modifier = Modifier.size(64.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color(0xFFF44336)
                )
            ) {
                Text(
                    text = "END",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "End Call",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun DialpadGrid(onDigit: (String) -> Unit) {
    val rows = listOf(
        listOf("1" to "", "2" to "ABC", "3" to "DEF"),
        listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
        listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
        listOf("*" to "", "0" to "+", "#" to "")
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (row in rows) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                for ((digit, letters) in row) {
                    DialpadButton(digit = digit, letters = letters, onClick = { onDigit(digit) })
                }
            }
        }
    }
}

@Composable
private fun DialpadButton(digit: String, letters: String, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(72.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = digit,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (letters.isNotEmpty()) {
                Text(
                    text = letters,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun CallControlButton(
    label: String,
    caption: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(64.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = color)
        ) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = caption,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "${hours}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    }
}
