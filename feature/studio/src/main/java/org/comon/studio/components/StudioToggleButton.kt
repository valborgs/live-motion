package org.comon.studio.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.comon.ui.theme.LiveMotionTheme

@Composable
internal fun StudioToggleButton(
    text: String,
    emoji: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    activeColor: Color
) {
    val backgroundColor = if (checked) activeColor else MaterialTheme.colorScheme.surfaceVariant

    Surface(
        onClick = { onCheckedChange(!checked) },
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        shadowElevation = if (checked) 6.dp else 2.dp,
        modifier = Modifier.height(44.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = emoji,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = text,
                color = if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Preview(name = "Light Mode", showBackground = true)
@Preview(
    name = "Dark Mode",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun StudioToggleButtonPreview() {
    LiveMotionTheme {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            StudioToggleButton(
                text = "GPU",
                emoji = "🚀",
                checked = true,
                onCheckedChange = {},
                activeColor = MaterialTheme.colorScheme.primary
            )
            StudioToggleButton(
                text = "확대",
                emoji = "🔍",
                checked = false,
                onCheckedChange = {},
                activeColor = MaterialTheme.colorScheme.secondary
            )
        }
    }
}
