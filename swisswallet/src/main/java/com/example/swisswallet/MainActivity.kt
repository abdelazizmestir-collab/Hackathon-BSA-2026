package com.example.swisswallet

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.core.designsystem.SwissTheme
import com.example.swisswallet.ui.SwissWalletRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val container = (application as SwissWalletApplication).container
        setContent {
            SwissTheme {
                SwissWalletRoot(container = container, onLock = { finishAffinity() })
            }
        }
    }
}
