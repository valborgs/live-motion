package org.comon.studio.components

import androidx.annotation.StringRes
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import org.comon.studio.R

@Composable
internal fun DeleteConfirmDialog(
    count: Int,
    @StringRes titleResId: Int = R.string.dialog_delete_title,
    @StringRes messageResId: Int = R.string.dialog_delete_message,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(titleResId),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(stringResource(messageResId, count))
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.button_cancel))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color(0xFFE53935)
                )
            ) {
                Text(stringResource(R.string.button_delete))
            }
        }
    )
}
