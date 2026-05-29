package com.musornibak.pocketclaw.ui.confirm

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun ConfirmBar(vm: ConfirmViewModel = hiltViewModel()) {
    val pending by vm.pending.collectAsState()
    val cs = MaterialTheme.colorScheme
    AnimatedVisibility(
        visible = pending != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        val p = pending ?: return@AnimatedVisibility
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(cs.surfaceContainerHigh, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(cs.tertiary, CircleShape)
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    "разрешить действие?",
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                p.human,
                style = MaterialTheme.typography.titleMedium,
                color = cs.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            if (p.args.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    p.toolName + p.args.entries.joinToString(prefix = "  ", separator = "  ") { "${it.key}=${it.value}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(
                    onClick = { vm.deny() },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("Отказать")
                }
                FilledTonalButton(
                    onClick = { vm.allow() },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("Разрешить")
                }
            }
        }
    }
}
