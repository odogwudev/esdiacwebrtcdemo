package com.odogwudev.esdiacwebrtcdemo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.odogwudev.esdiacwebrtcdemo.AudioRoute
import com.odogwudev.esdiacwebrtcdemo.AudioRouteType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallScreen(
    callState: CallUiState,
    onMuteToggle: () -> Unit,
    onAudioRouteClick: () -> Unit,
    onAudioRouteSelected: (AudioRoute) -> Unit,
    onAudioRouteDismiss: () -> Unit,
    onDialpadToggle: () -> Unit,
    onDtmfDigit: (String) -> Unit,
    onHoldToggle: () -> Unit,
    onEndCall: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
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
                DialpadGrid(onDigit = onDtmfDigit)
            } else {
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
                CallControlButton(
                    label = if (callState.isMuted) "OFF" else "MIC",
                    caption = if (callState.isMuted) "Unmute" else "Mute",
                    color = if (callState.isMuted) Color(0xFFF44336) else MaterialTheme.colorScheme.secondaryContainer,
                    onClick = onMuteToggle
                )

                CallControlButton(
                    label = "###",
                    caption = "Keypad",
                    color = if (callState.isDialpadVisible) Color(0xFF2196F3) else MaterialTheme.colorScheme.secondaryContainer,
                    onClick = onDialpadToggle
                )

                CallControlButton(
                    label = if (callState.isOnHold) "RSM" else "HLD",
                    caption = if (callState.isOnHold) "Resume" else "Hold",
                    color = if (callState.isOnHold) Color(0xFFFF9800) else MaterialTheme.colorScheme.secondaryContainer,
                    onClick = onHoldToggle
                )

                // Audio route button
                CallControlButton(
                    label = when (callState.activeAudioRoute.type) {
                        AudioRouteType.Speaker -> "SPK"
                        AudioRouteType.Bluetooth -> "BT"
                        AudioRouteType.WiredHeadset -> "HP"
                        AudioRouteType.Earpiece -> "EAR"
                    },
                    caption = when (callState.activeAudioRoute.type) {
                        AudioRouteType.Speaker -> "Speaker"
                        AudioRouteType.Bluetooth -> callState.activeAudioRoute.name
                        AudioRouteType.WiredHeadset -> "Headset"
                        AudioRouteType.Earpiece -> "Earpiece"
                    },
                    color = when (callState.activeAudioRoute.type) {
                        AudioRouteType.Speaker -> Color(0xFF4CAF50)
                        AudioRouteType.Bluetooth -> Color(0xFF2196F3)
                        AudioRouteType.WiredHeadset -> Color(0xFF9C27B0)
                        AudioRouteType.Earpiece -> MaterialTheme.colorScheme.secondaryContainer
                    },
                    onClick = onAudioRouteClick
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

        // Audio Route Bottom Sheet
        if (callState.isAudioRouteSheetVisible) {
            ModalBottomSheet(
                onDismissRequest = onAudioRouteDismiss,
                sheetState = rememberModalBottomSheetState()
            ) {
                AudioRouteSheet(
                    routes = callState.availableAudioRoutes,
                    activeRoute = callState.activeAudioRoute,
                    onRouteSelected = { route ->
                        onAudioRouteSelected(route)
                        onAudioRouteDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun AudioRouteSheet(
    routes: List<AudioRoute>,
    activeRoute: AudioRoute,
    onRouteSelected: (AudioRoute) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp)
    ) {
        Text(
            text = "Audio Output",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
        )

        for (route in routes) {
            val isSelected = route.type == activeRoute.type && route.id == activeRoute.id

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onRouteSelected(route) }
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (route.type) {
                        AudioRouteType.Earpiece -> "EAR"
                        AudioRouteType.Speaker -> "SPK"
                        AudioRouteType.Bluetooth -> "BT"
                        AudioRouteType.WiredHeadset -> "HP"
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .wrapContentSize(Alignment.Center),
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = route.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = if (isSelected) "\u25CF" else "\u25CB",
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 18.sp
                )
            }
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
