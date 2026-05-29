package com.musornibak.pocketclaw.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.musornibak.pocketclaw.R

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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.chat_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    items(turns) { t -> TurnRow(t) }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text(stringResource(R.string.chat_input_hint)) },
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(Modifier.width(8.dp))
                if (running) {
                    IconButton(onClick = { vm.stop() }) {
                        Icon(Icons.Outlined.Stop, contentDescription = stringResource(R.string.chat_stop), tint = cs.error)
                    }
                } else {
                    IconButton(onClick = {
                        if (input.isNotBlank()) {
                            vm.send(input.trim())
                            input = ""
                        }
                    }) {
                        Icon(Icons.Outlined.Send, contentDescription = stringResource(R.string.chat_send), tint = cs.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun TurnRow(t: ChatTurn) {
    val cs = MaterialTheme.colorScheme
    val (label, bg, fg) = when (t.kind) {
        TurnKind.User -> Triple("ты", cs.surfaceContainerHigh, cs.onSurface)
        TurnKind.Thought -> Triple("мысль", cs.surfaceContainer, cs.onSurfaceVariant)
        TurnKind.ToolCall -> Triple("→ действие", cs.surfaceContainerHighest, cs.onSurface)
        TurnKind.Observation -> Triple(
            if (t.ok) "← результат" else "← неудача",
            cs.surfaceContainer,
            if (t.ok) cs.onSurfaceVariant else cs.error
        )
        TurnKind.Final -> Triple("готово", cs.surfaceContainerHigh, cs.primary)
        TurnKind.Error -> Triple("ошибка", cs.surfaceContainer, cs.error)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = cs.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            t.text,
            style = MaterialTheme.typography.bodyMedium,
            color = fg,
            fontFamily = if (t.kind == TurnKind.Observation || t.kind == TurnKind.ToolCall) FontFamily.Monospace else FontFamily.Default
        )
    }
}
