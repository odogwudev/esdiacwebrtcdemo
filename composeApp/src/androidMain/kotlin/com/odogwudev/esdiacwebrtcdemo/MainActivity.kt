package com.odogwudev.esdiacwebrtcdemo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher =
        registerForActivityResult(RequestMultiplePermissions()) { _ ->
            setContent { App() }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AppContextHolder.initialize(applicationContext)

        val requiredPermissions = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        val missingPermissions = requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            setContent { App() }
        }
    }
}
