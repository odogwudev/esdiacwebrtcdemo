package com.odogwudev.esdiacwebrtcdemo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.odogwudev.esdiacwebrtcdemo.ui.CallScreen
import com.odogwudev.esdiacwebrtcdemo.ui.CallViewModel
import com.odogwudev.esdiacwebrtcdemo.ui.HomeScreen
import com.odogwudev.esdiacwebrtcdemo.ui.Screen

@Composable
fun App() {
    MaterialTheme {
        val viewModel = viewModel { CallViewModel() }
        val state by viewModel.uiState.collectAsState()

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (state.screen) {
                Screen.HOME -> HomeScreen(onMakeCall = viewModel::makeCall)
                Screen.IN_CALL -> CallScreen(
                    callState = state,
                    onMuteToggle = viewModel::toggleMute,
                    onSpeakerToggle = viewModel::toggleSpeaker,
                    onDialpadToggle = viewModel::toggleDialpad,
                    onDtmfDigit = viewModel::sendDtmf,
                    onHoldToggle = viewModel::toggleHold,
                    onEndCall = viewModel::endCall
                )
            }
        }
    }
}
