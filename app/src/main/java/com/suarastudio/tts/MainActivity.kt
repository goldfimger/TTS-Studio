package com.suarastudio.tts

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.suarastudio.tts.ui.SplashScreen
import com.suarastudio.tts.ui.TtsPlaygroundScreen
import com.suarastudio.tts.ui.TtsViewModel
import com.suarastudio.tts.ui.theme.SuaraStudioTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { splashView ->
                splashView.remove()
            }
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: TtsViewModel = viewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            var showSplash by remember { mutableStateOf(true) }

            SuaraStudioTheme(
                themeMode = settings.themeMode,
                useDynamicMonet = settings.useDynamicMonet
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    TtsPlaygroundScreen(viewModel = viewModel)

                    if (showSplash) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(10f)
                        ) {
                            SplashScreen(onFinished = { showSplash = false })
                        }
                    }
                }
            }
        }
    }
}
