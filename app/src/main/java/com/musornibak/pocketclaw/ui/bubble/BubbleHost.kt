package com.musornibak.pocketclaw.ui.bubble

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.musornibak.pocketclaw.agent.ChatStore
import com.musornibak.pocketclaw.agent.ChatTurn
import com.musornibak.pocketclaw.agent.TurnKind

@Composable
fun BubbleHost(
    store: ChatStore,
    onOpenApp: () -> Unit,
    onClose: () -> Unit,
    onDrag: (Int, Int) -> Unit,
    onExpandStateChange: (Boolean) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val turns by store.turns.collectAsState()
    val running by store.running.collectAsState()

    LaunchedEffect(expanded) { onExpandStateChange(expanded) }

    Box(modifier = Modifier.padding(4.dp)) {
        AnimatedVisibility(
            visible = !expanded,
            enter = scaleIn(tween(180)) + fadeIn(tween(180)),
            exit = scaleOut(tween(140)) + fadeOut(tween(140))
        ) {
            BubbleCircle(
                running = running,
                onTap = { expanded = true },
                onDrag = onDrag
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = scaleIn(tween(220)) + fadeIn(tween(220)),
            exit = scaleOut(tween(160)) + fadeOut(tween(160))
        ) {
            BubblePanel(
                turns = turns,
                running = running,
                onCollapse = { expanded = false },
                onOpenApp = {
                    expanded = false
                    onOpenApp()
                },
                onClose = onClose
            )
        }
    }
}

@Composable
private fun BubbleCircle(
    running: Boolean,
    onTap: () -> Unit,
    onDrag: (Int, Int) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var moved by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(58.dp)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(cs.primary)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { moved = false },
                    onDragEnd = {
                        if (!moved) onTap()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (kotlin.math.abs(dragAmount.x) + kotlin.math.abs(dragAmount.y) > 2f) {
                            moved = true
                        }
                        onDrag(dragAmount.x.toInt(), dragAmount.y.toInt())
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Outlined.Chat,
            contentDescription = null,
            tint = cs.onPrimary,
            modifier = Modifier.size(26.dp)
        )
        if (running) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(10.dp)
                    .background(cs.tertiary, CircleShape)
            )
        }
    }
}

@Composable
private fun BubblePanel(
    turns: List<ChatTurn>,
    running: Boolean,
    onCollapse: () -> Unit,
    onOpenApp: () -> Unit,
    onClose: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    LaunchedEffect(turns.size) {
        if (turns.isNotEmpty()) listState.animateScrollToItem(turns.size - 1)
    }
    Surface(
        modifier = Modifier
            .width(290.dp)
            .heightIn(min = 180.dp, max = 380.dp)
            .shadow(14.dp, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        color = cs.surface,
        tonalElevation = 4.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cs.surfaceContainerHigh)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(if (running) cs.tertiary else cs.primary, CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (running) "думает…" else "PocketClaw",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onOpenApp) {
                    Icon(
                        Icons.Outlined.OpenInNew,
                        contentDescription = "Открыть приложение",
                        tint = cs.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onCollapse) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Свернуть",
                        tint = cs.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            if (turns.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Чат пуст. Открой приложение, чтобы начать.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(turns) { t -> CompactTurn(t) }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "сервис активен",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Закрыть бабл",
                        tint = cs.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactTurn(t: ChatTurn) {
    val cs = MaterialTheme.colorScheme
    when (t.kind) {
        TurnKind.User -> Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                modifier = Modifier
                    .background(cs.primary, RoundedCornerShape(12.dp, 12.dp, 4.dp, 12.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(t.text, style = MaterialTheme.typography.bodySmall, color = cs.onPrimary)
            }
        }
        TurnKind.Thought -> Text(
            "· ${t.text}",
            style = MaterialTheme.typography.labelSmall,
            color = cs.onSurfaceVariant,
            fontStyle = FontStyle.Italic
        )
        TurnKind.ToolCall, TurnKind.Observation -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(cs.surfaceContainer, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(if (t.ok) cs.primary else cs.error, CircleShape)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                t.text,
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurface,
                fontFamily = FontFamily.Monospace
            )
        }
        TurnKind.Final -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(cs.primaryContainer, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(t.text, style = MaterialTheme.typography.bodySmall, color = cs.onPrimaryContainer)
        }
        TurnKind.Error -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(cs.errorContainer, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(t.text, style = MaterialTheme.typography.labelSmall, color = cs.onErrorContainer)
        }
    }
}
