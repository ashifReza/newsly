package com.example.newsly

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.newsly.ui.NewsApp
import com.example.newsly.ui.NewsViewModel
import com.example.newsly.ui.onboarding.Onboarding
import com.example.newsly.ui.theme.Newsly
import com.example.newsly.ui.theme.NewslyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appSettings = (application as NewsApplication).container.appSettings
        setContent {
            val settings by appSettings.settings.collectAsStateWithLifecycle()
            // Created up front so feeds start loading while a first-time user is onboarding.
            val viewModel: NewsViewModel = viewModel(factory = NewsViewModel.Factory)
            NewslyTheme(settings) {
                // Status and navigation bar icons follow the app's theme, not just the phone's.
                val dark = Newsly.colors.isDark
                LaunchedEffect(dark) {
                    val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                }
                if (settings.onboardingDone) NewsApp(viewModel, appSettings) else Onboarding(settings, appSettings)
            }
        }
    }
}
