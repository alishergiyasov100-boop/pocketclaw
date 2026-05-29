package com.musornibak.pocketclaw.ui.chat

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.musornibak.pocketclaw.R
import com.musornibak.pocketclaw.ui.confirm.ConfirmBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onOpenDrawer: () -> Unit,
    vm: ChatViewModel = hiltViewModel()
) {
    val turns by vm.turns.collectAsState()
    val running by vm.running.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val cs = MaterialTheme.colorScheme

    LaunchedEffect(turns.size) {
        if (turns.isNotEmpty()) listState.animateScrollToItem(turns.size - 1)
    }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = cs.background,
                    titleContentColor = cs.onBackground
                ),
                title = { Text(stringResource(R.string.chat_title)) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Outlined.Menu, contentDescription = null, tint = cs.onSurface)
                    }
                }
            )
        }
    ) { padding: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (turns.isEmpty()) {
                EmptyHero(
                    onPick = { input = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    items(turns) { t -> TurnView(t) }
                    if (running) {
                        item { TypingIndicator() }
                    }
                }
            }

            ConfirmBar()

            ChatInputBar(
                value = input,
                onChange = { input = it },
                running = running,
                onSend = {
                    if (input.isNotBlank()) {
                        vm.send(input.trim())
                        input = ""
                    }
                },
                onStop = { vm.stop() }
            )
        }
    }
}

@Composable
private fun EmptyHero(
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val examples = listOf(
        "открой ютуб и найди lofi",
        "открой настройки и включи bluetooth",
        "найди в галерее последнее фото",
    )
    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "PocketClaw",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = cs.onSurface
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.chat_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        examples.forEach { ex ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, cs.outlineVariant, RoundedCornerShape(12.dp))
                    .clickable { onPick(ex) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    ex,
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurface
                )
            }
        }
    }
}

@Composable
private fun TurnView(t: ChatTurn) {
    when (t.kind) {
        TurnKind.User -> UserBubble(t.text)
        TurnKind.Thought -> ThoughtLine(t.text)
        TurnKind.ToolCall -> StepRow(t.text, ok = true, pending = true)
        TurnKind.Observation -> StepRow(
            text = t.text.trim().ifBlank { if (t.ok) "ok" else "fail" },
            ok = t.ok,
            pending = false
        )
        TurnKind.Final -> FinalBlock(t.text)
        TurnKind.Error -> ErrorLine(t.text)
    }
}

@Composable
private fun UserBubble(text: String) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .background(cs.primary, RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onPrimary
            )
        }
    }
}

@Composable
private fun ThoughtLine(text: String) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            "· $text",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
            fontStyle = FontStyle.Italic
        )
    }
}

@Composable
private fun StepRow(text: String, ok: Boolean, pending: Boolean) {
    val cs = MaterialTheme.colorScheme
    val dot = when {
        pending -> cs.onSurfaceVariant
        ok -> cs.primary
        else -> cs.error
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(cs.surfaceContainer, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(dot, CircleShape)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = if (ok) cs.onSurface else cs.error,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun FinalBlock(text: String) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, cs.primary, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Text(
            "готово",
            style = MaterialTheme.typography.labelSmall,
            color = cs.primary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurface
        )
    }
}

@Composable
private fun ErrorLine(text: String) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(cs.errorContainer, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(cs.error, CircleShape)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = cs.onErrorContainer,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TypingIndicator() {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(cs.onSurfaceVariant, CircleShape)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "думает…",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
            fontStyle = FontStyle.Italic
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatInputBar(
    value: String,
    onChange: (String) -> Unit,
    running: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val clip = LocalClipboardManager.current
    val ctx = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            placeholder = { Text(stringResource(R.string.chat_input_hint)) },
            modifier = Modifier.weight(1f),
            maxLines = 4,
            shape = RoundedCornerShape(22.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = cs.surfaceContainer,
                unfocusedContainerColor = cs.surfaceContainer,
                focusedIndicatorColor = cs.outlineVariant,
                unfocusedIndicatorColor = cs.outlineVariant
            ),
            trailingIcon = {
                IconButton(onClick = {
                    val pasted = clip.getText()?.text
                    if (!pasted.isNullOrEmpty()) {
                        onChange(if (value.isEmpty()) pasted else "$value$pasted")
                    } else {
                        Toast.makeText(ctx, "Буфер пуст", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Icon(Icons.Outlined.ContentPaste, contentDescription = "Вставить", tint = cs.onSurfaceVariant)
                }
            }
        )
        Spacer(Modifier.width(8.dp))
        if (running) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(cs.errorContainer, CircleShape)
                    .clickable { onStop() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Stop, contentDescription = stringResource(R.string.chat_stop), tint = cs.error)
            }
        } else {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(cs.primary, CircleShape)
                    .clickable(enabled = value.isNotBlank()) { onSend() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Send, contentDescription = stringResource(R.string.chat_send), tint = cs.onPrimary)
            }
        }
    }
}
