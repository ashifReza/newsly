package com.example.newsly.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// Material's bookmark icons live in the large icons-extended library; these two paths are all we need.
private fun materialIcon(name: String, pathData: String) = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).addPath(pathData = addPathNodes(pathData), fill = SolidColor(Color.Black)).build()

val BookmarkFilled = materialIcon(
    "Bookmark",
    "M17,3H7c-1.1,0 -1.99,0.9 -1.99,2L5,21l7,-3 7,3V5c0,-1.1 -0.9,-2 -2,-2z",
)

val BookmarkOutlined = materialIcon(
    "BookmarkBorder",
    "M17,3H7c-1.1,0 -1.99,0.9 -1.99,2L5,21l7,-3 7,3V5c0,-1.1 -0.9,-2 -2,-2zM17,18l-5,-2.18L7,18V5h10v13z",
)

@Composable
fun BookmarkButton(saved: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = if (saved) BookmarkFilled else BookmarkOutlined,
            contentDescription = if (saved) "Remove bookmark" else "Save story",
            tint = if (saved) MaterialTheme.colorScheme.primary else LocalContentColor.current,
        )
    }
}
