package com.example.newsly.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.newsly.data.Country
import com.example.newsly.data.SortOrder
import com.example.newsly.ui.theme.Newsly

/** Small outlined pill that opens a picker: "🌐 All countries ⌄", "Sort: Latest ⌄". */
@Composable
fun FilterPill(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, emoji: String? = null) {
    val c = Newsly.colors
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier
            .heightIn(min = 36.dp)
            .clip(shape)
            .border(1.dp, c.line, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 12.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when {
            emoji != null -> Text(emoji, style = MaterialTheme.typography.labelLarge)
            icon != null -> Icon(icon, null, tint = c.ink, modifier = Modifier.size(16.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = c.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Icon(NewslyIcons.ChevronDown, null, tint = c.muted, modifier = Modifier.size(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortSheet(selected: SortOrder, onSelect: (SortOrder) -> Unit, onDismiss: () -> Unit) {
    val c = Newsly.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface,
        contentColor = c.ink,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            SheetTitle("Sort stories")
            SortOrder.entries.forEach { sort ->
                PickerRow(
                    selected = sort == selected,
                    onClick = {
                        onSelect(sort)
                        onDismiss()
                    },
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(sort.label, style = MaterialTheme.typography.titleMedium, color = c.ink)
                        Text(sort.description, style = MaterialTheme.typography.bodySmall, color = c.muted)
                    }
                }
            }
        }
    }
}

/** Every country, searchable, with "All countries" on top. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountrySheet(selected: Country?, onSelect: (Country?) -> Unit, onDismiss: () -> Unit) {
    val c = Newsly.colors
    var query by rememberSaveable { mutableStateOf("") }
    val matches = remember(query) {
        val q = query.trim()
        if (q.isEmpty()) Country.all
        else Country.all.filter { it.name.contains(q, ignoreCase = true) || it.code.equals(q, ignoreCase = true) }
    }
    fun pick(country: Country?) {
        onSelect(country)
        onDismiss()
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface,
        contentColor = c.ink,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.fillMaxHeight(0.92f).imePadding()) {
            SheetTitle("World news from")
            Row(
                Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .height(50.dp)
                    .background(c.soft, RoundedCornerShape(999.dp))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(NewslyIcons.Search, null, tint = c.muted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search countries", style = MaterialTheme.typography.bodyLarge, color = c.muted)
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.ink),
                        cursorBrush = SolidColor(c.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search countries" },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.weight(1f)) {
                if (query.isBlank()) {
                    item(key = "all") {
                        PickerRow(selected = selected == null, onClick = { pick(null) }) {
                            Icon(NewslyIcons.Globe, null, tint = c.ink, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("All countries", style = MaterialTheme.typography.titleMedium, color = c.ink)
                                Text("Top stories from outlets worldwide", style = MaterialTheme.typography.bodySmall, color = c.muted)
                            }
                        }
                        HorizontalDivider(color = c.line.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 20.dp))
                    }
                }
                items(matches, key = { it.code }) { country ->
                    PickerRow(selected = country == selected, onClick = { pick(country) }) {
                        Text(country.flag, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(14.dp))
                        Text(country.name, style = MaterialTheme.typography.bodyLarge, color = c.ink, modifier = Modifier.weight(1f))
                    }
                }
                if (matches.isEmpty()) {
                    item { Text("No countries match “$query”", color = c.muted, modifier = Modifier.padding(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SheetTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        color = Newsly.colors.ink,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
    )
}

@Composable
private fun PickerRow(selected: Boolean, onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
        if (selected) {
            Spacer(Modifier.width(12.dp))
            Icon(NewslyIcons.Check, "Selected", tint = Newsly.colors.accent, modifier = Modifier.size(20.dp))
        }
    }
}
