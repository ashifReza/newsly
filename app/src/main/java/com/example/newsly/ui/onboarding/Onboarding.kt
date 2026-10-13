package com.example.newsly.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.newsly.data.AppSettings
import com.example.newsly.data.Settings
import com.example.newsly.ui.components.PrimaryButton
import com.example.newsly.ui.theme.Newsly

/** First launch: the written wordmark, then theme choices, then the app. */
@Composable
fun Onboarding(settings: Settings, appSettings: AppSettings) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    AnimatedContent(
        targetState = step,
        transitionSpec = { (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut()) },
        label = "onboarding",
    ) { s ->
        when (s) {
            0 -> WelcomeStep(onGetStarted = { step = 1 })
            else -> ThemeStep(settings, appSettings)
        }
    }
}

@Composable
private fun WelcomeStep(onGetStarted: () -> Unit) {
    val c = Newsly.colors
    // Survives rotation so the wordmark isn't written twice.
    var written by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp, vertical = 32.dp),
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (written) {
                // Already played: show the finished wordmark without replaying.
                PenWordmark(c.ink, c.accent, Modifier.fillMaxWidth().height(140.dp), reducedOverride = true)
            } else {
                PenWordmark(c.ink, c.accent, Modifier.fillMaxWidth().height(140.dp), onFinished = { written = true })
            }
            Spacer(Modifier.height(18.dp))
            AnimatedVisibility(written, enter = fadeIn() + slideInVertically { it / 3 }) {
                Text(
                    "The world’s news and what’s happening around you, in one calm place.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 300.dp),
                )
            }
        }
        AnimatedVisibility(written, enter = fadeIn() + slideInVertically { it / 2 }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton("Get started", onGetStarted)
                Text(
                    "Stories from BBC, The Guardian, NYT, Al Jazeera and dozens of local outlets",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ThemeStep(settings: Settings, appSettings: AppSettings) {
    val c = Newsly.colors
    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Step 2 of 2", style = MaterialTheme.typography.labelMedium, color = c.muted, modifier = Modifier.weight(1f))
            TextButton(onClick = appSettings::finishOnboarding) {
                Text("Skip", style = MaterialTheme.typography.labelLarge, color = c.muted)
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Make it yours", style = MaterialTheme.typography.headlineLarge.copy(fontSize = MaterialTheme.typography.displaySmall.fontSize * 0.94f), color = c.ink)
            Text("You can change this any time from the settings button on Home.", style = MaterialTheme.typography.bodyMedium, color = c.muted)
            Spacer(Modifier.height(14.dp))
            ThemeChooser(settings, appSettings::setThemeMode, appSettings::setAccent, appSettings::setTextSize)
            Spacer(Modifier.height(20.dp))
        }
        Box(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp, top = 8.dp)) {
            PrimaryButton("Start reading", appSettings::finishOnboarding)
        }
    }
}
