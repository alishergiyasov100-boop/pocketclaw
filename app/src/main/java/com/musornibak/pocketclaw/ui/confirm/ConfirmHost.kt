package com.musornibak.pocketclaw.ui.confirm

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.musornibak.pocketclaw.R

@Composable
fun ConfirmHost(vm: ConfirmViewModel = hiltViewModel()) {
    val pending by vm.pending.collectAsState()
    val p = pending ?: return

    AlertDialog(
        onDismissRequest = { vm.deny() },
        title = { Text(stringResource(R.string.confirm_title)) },
        text = {
            Column {
                Text(
                    p.human,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "tool: ${p.toolName}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                p.args.forEach { (k, v) ->
                    Text(
                        "  $k = $v",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.confirm_explain),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.allow() }) {
                Text(stringResource(R.string.confirm_allow))
            }
        },
        dismissButton = {
            TextButton(onClick = { vm.deny() }) {
                Text(stringResource(R.string.confirm_deny))
            }
        }
    )
}
