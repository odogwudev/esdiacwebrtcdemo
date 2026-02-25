package com.odogwudev.esdiacwebrtcdemo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(onMakeCall: (destinationNumber: String) -> Unit) {
    var destinationNumber by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "WebRTC Audio Demo",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(48.dp))

        OutlinedTextField(
            value = destinationNumber,
            onValueChange = { input ->

                var cleaned = input.replace(Regex("[^0-9+]"), "")

                if (cleaned.contains("+")) {
                    cleaned = "+" + cleaned.replace("+", "")
                }

                destinationNumber = cleaned
            },
            label = { Text("Destination Number") },
            placeholder = { Text("Enter number to call") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    if (destinationNumber.isNotBlank()) {
                        onMakeCall(destinationNumber.trim())
                    }
                }
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { onMakeCall(destinationNumber.trim()) },
            enabled = destinationNumber.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Call")
        }
    }
}
