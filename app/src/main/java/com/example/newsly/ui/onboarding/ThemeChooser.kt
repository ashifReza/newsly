package com.example.newsly.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.newsly.data.Accent
import com.example.newsly.data.Settings
import com.example.newsly.data.TextSize
import com.example.newsly.data.ThemeMode
import com.example.newsly.ui.BookmarkFilled
import com.example.newsly.ui.components.Kicker
import com.example.newsly.ui.components.PillToggle
import com.example.newsly.ui.theme.Newsly
import com.example.newsly.ui.theme.color
import com.example.newsly.ui.theme.newslyColors

/**
 * Appearance, accent colour and text size, with a live preview. The whole app
 * is themed from these settings, so the screen restyles itself as you choose.
 */
@Composable
fun ThemeChooser(
    settings: Settings,
    onThemeMode: (ThemeMode) -> Unit,
    onAccent: (Accent) -> Unit,
    onTextSize: (TextSize) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Newsly.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Surface(shape = RoundedCornerShape(22.dp), color = c.surface, shadowElevation = if (c.isDark) 0.dp else 4.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { Kicker("Preview · Top story") }
                    Icon(BookmarkFilled, null, tint = c.accent, modifier = Modifier.size(18.dp))
                }
                Text(
                    "Who is Raphael Glucksmann, winner of the French left primary?",
                    style = MaterialTheme.typography.headlineSmall,
                    color = c.ink,
                )
                Text("France 24 · 7 min ago", style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
        }

        Section("Appearance") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val light = newslyColors(false, settings.accent)
                val dark = newslyColors(true, settings.accent)
                ModeCard("Light", settings.themeMode == ThemeMode.LIGHT, light.background, light.surface, Modifier.weight(1f)) { onThemeMode(ThemeMode.LIGHT) }
                ModeCard("Dark", settings.themeMode == ThemeMode.DARK, dark.background, dark.surface, Modifier.weight(1f)) { onThemeMode(ThemeMode.DARK) }
                ModeCard("System", settings.themeMode == ThemeMode.SYSTEM, light.background, dark.background, Modifier.weight(1f)) { onThemeMode(ThemeMode.SYSTEM) }
            }
        }

        Section("Accent colour · ${settings.accent.label()}") {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Accent.entries.forEach { accent ->
                    val on = accent == settings.accent
                    Box(
                        Modifier
                            .size(48.dp)
                            .then(if (on) Modifier.border(2.dp, c.ink, CircleShape) else Modifier)
                            .padding(5.dp)
                            .clip(CircleShape)
                            .background(accent.color())
                            .clickable(role = Role.RadioButton) { onAccent(accent) }
                            .semantics {
                                contentDescription = accent.label()
                                selected = on
                            },
                    )
                }
            }
        }

        Section("Text size") {
            PillToggle(
                options = listOf("Standard", "Large"),
                selected = if (settings.textSize == TextSize.LARGE) 1 else 0,
                onSelect = { onTextSize(if (it == 1) TextSize.LARGE else TextSize.STANDARD) },
            )
        }
    }
}

fun Accent.label() = name.lowercase().replaceFirstChar { it.uppercase() }

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = Newsly.colors.ink)
        content()
    }
}

@Composable
private fun ModeCard(label: String, selected: Boolean, left: Color, right: Color, modifier: Modifier, onClick: () -> Unit) {
    val c = Newsly.colors
    val border by animateColorAsState(if (selected) c.accent else Color.Transparent, label = "mode border")
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(c.surface)
            .border(2.dp, border, RoundedCornerShape(18.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(11.dp)).border(1.dp, c.line, RoundedCornerShape(11.dp)),
        ) {
            Spacer(Modifier.weight(1f).height(54.dp).background(left))
            Spacer(Modifier.weight(1f).height(54.dp).background(right))
        }
        Text(label, style = MaterialTheme.typography.titleSmall, color = c.ink)
    }
}
