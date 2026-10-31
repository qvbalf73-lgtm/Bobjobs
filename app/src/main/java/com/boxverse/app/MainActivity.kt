package com.boxverse.app

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boxverse.app.data.prefs.AppSettings
import com.boxverse.app.data.prefs.ThemeMode
import com.boxverse.app.ui.components.BlindBoxArt
import com.boxverse.app.ui.components.ErrorState
import com.boxverse.app.ui.components.StarField
import com.boxverse.app.ui.navigation.BoxverseRoot
import com.boxverse.app.ui.navigation.LocalContainer
import com.boxverse.app.ui.theme.BoxverseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as BoxverseApp).container
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            val ready by container.ready.collectAsStateWithLifecycle()
            val initError by container.initError.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            LaunchedEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                    else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
                )
            }
            BoxverseTheme(settings.themeMode) {
                CompositionLocalProvider(LocalContainer provides container) {
                    when {
                        initError != null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                            ErrorState("数据初始化失败：$initError\n请尝试重启 App。")
                        }
                        !ready -> SplashContent()
                        else -> BoxverseRoot()
                    }
                }
            }
        }
    }
}

@Composable
private fun SplashContent() {
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFC6B6FF), Color(0xFFE7C6FF), Color(0xFFFFC9DF)))),
        contentAlignment = Alignment.Center,
    ) {
        StarField(Modifier.fillMaxSize())
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            BlindBoxArt(0xFFB9A8FF, 0xFFFFB8D6, "BOXVERSE", Modifier.size(150.dp))
            Spacer(Modifier.height(16.dp))
            Text("盒宇宙 BOXVERSE", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Spacer(Modifier.height(6.dp))
            Text("你的潮玩收藏宇宙，每一次拆盒都有惊喜", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
        }
    }
}
