package com.musornibak.pocketclaw.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.musornibak.pocketclaw.R
import com.musornibak.pocketclaw.data.ApiSettings
import com.musornibak.pocketclaw.data.ConfirmLevel
import com.musornibak.pocketclaw.data.Provider
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun SettingsScreen(
    onOpenDrawer: () -> Unit,
    vm: SettingsViewModel = hiltViewModel()
) {
    val s = vm.settings.collectAsState().value
    val test by vm.testResult.collectAsState()
    val cs = MaterialTheme.colorScheme

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = cs.background,
                    titleContentColor = cs.onBackground
                ),
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Outlined.Menu, contentDescription = null, tint = cs.onSurface)
                    }
                }
            )
        }
    ) { padding: PaddingValues ->
        if (s == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("…", color = cs.onSurfaceVariant)
            }
            return@Scaffold
        }

        var baseUrl by remember { mutableStateOf(s.baseUrl) }
        var apiKey by remember { mutableStateOf(s.apiKey) }
        var model by remember { mutableStateOf(s.model) }
        var systemPrompt by remember { mutableStateOf(s.systemPrompt) }

        LaunchedEffect(s.provider) {
            baseUrl = s.baseUrl
            model = s.model
        }

        LaunchedEffect(Unit) {
            snapshotFlow { baseUrl }.drop(1).debounce(400).collect {
                if (it != s.baseUrl) vm.setBaseUrl(it)
            }
        }
        LaunchedEffect(Unit) {
            snapshotFlow { apiKey }.drop(1).debounce(400).collect {
                if (it != s.apiKey) vm.setApiKey(it)
            }
        }
        LaunchedEffect(Unit) {
            snapshotFlow { model }.drop(1).debounce(400).collect {
                if (it != s.model) vm.setModel(it)
            }
        }
        LaunchedEffect(Unit) {
            snapshotFlow { systemPrompt }.drop(1).debounce(400).collect {
                if (it != s.systemPrompt) vm.setSystemPrompt(it)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        stringResource(R.string.settings_provider),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface
                    )
                    ProviderPicker(s.provider) { vm.setProvider(it) }

                    PasteField(
                        value = baseUrl,
                        onChange = { baseUrl = it },
                        label = stringResource(R.string.settings_base_url),
                        keyboard = KeyboardType.Uri
                    )
                    PasteField(
                        value = apiKey,
                        onChange = { apiKey = it },
                        label = stringResource(R.string.settings_api_key),
                        keyboard = KeyboardType.Text
                    )
                    PasteField(
                        value = model,
                        onChange = { model = it },
                        label = stringResource(R.string.settings_model),
                        keyboard = KeyboardType.Text
                    )
                    OutlinedTextField(
                        value = systemPrompt,
                        onValueChange = { systemPrompt = it },
                        label = { Text(stringResource(R.string.settings_system_prompt)) },
                        minLines = 3,
                        maxLines = 8,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedButton(
                        onClick = {
                            vm.runTest(
                                ApiSettings(
                                    provider = s.provider,
                                    baseUrl = baseUrl.trim(),
                                    apiKey = apiKey.trim(),
                                    model = model.trim(),
                                    systemPrompt = systemPrompt,
                                    confirmLevel = s.confirmLevel,
                                    toolsPerSecond = s.toolsPerSecond,
                                    maxHistoryMsgs = s.maxHistoryMsgs
                                )
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.settings_test))
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Подтверждения действий",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface
                    )
                    Text(
                        "Большие действия: open_url, launch_app, type. Маленькие: тапы и скролл.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                    ConfirmLevel.entries.forEach { lvl ->
                        OutlinedButton(
                            onClick = { vm.setConfirmLevel(lvl) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                (if (s.confirmLevel == lvl) "● " else "○ ") + lvl.label,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Лимит действий",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface
                    )
                    Text(
                        "Максимум tool-вызовов в секунду (защита от циклов).",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(1, 2, 5, 10, 0).forEach { rate ->
                            OutlinedButton(
                                onClick = { vm.setToolsPerSecond(rate) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (rate == 0) "∞" else "$rate")
                            }
                        }
                    }
                    Text(
                        "Текущий: " + if (s.toolsPerSecond == 0) "без лимита" else "${s.toolsPerSecond}/сек",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (s.toolsPerSecond == 0) cs.error else cs.primary
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Окно истории",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = cs.onSurface
                    )
                    Text(
                        "Сколько последних сообщений уходит в модель. Меньше — дешевле, но агент быстрее забывает.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(20, 40, 80, 160, 0).forEach { n ->
                            OutlinedButton(
                                onClick = { vm.setMaxHistoryMsgs(n) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (n == 0) "∞" else "$n")
                            }
                        }
                    }
                    Text(
                        "Текущее: " + if (s.maxHistoryMsgs == 0) "без обрезки (дорого)" else "${s.maxHistoryMsgs} сообщений",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (s.maxHistoryMsgs == 0) cs.error else cs.primary
                    )
                }
            }

            test?.let { (ok, msg) ->
                val ctx = LocalContext.current
                val clip = LocalClipboardManager.current
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (ok) cs.surfaceContainer else cs.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (ok) "OK" else "Ошибка",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (ok) cs.primary else cs.error,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(
                                onClick = {
                                    clip.setText(AnnotatedString(msg))
                                    Toast.makeText(ctx, "Скопировано", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) { Text("Копировать") }
                        }
                        SelectionContainer {
                            Text(
                                msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (ok) cs.onSurface else cs.onErrorContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PasteField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    keyboard: KeyboardType
) {
    val clip = LocalClipboardManager.current
    val ctx = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(label) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(
                autoCorrect = false,
                capitalization = KeyboardCapitalization.None,
                keyboardType = keyboard
            ),
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(6.dp))
        IconButton(onClick = {
            val pasted = clip.getText()?.text
            if (!pasted.isNullOrEmpty()) {
                onChange(pasted)
            } else {
                Toast.makeText(ctx, "Буфер пуст", Toast.LENGTH_SHORT).show()
            }
        }) {
            Icon(Icons.Outlined.ContentPaste, contentDescription = "Вставить", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ProviderPicker(current: Provider, onPick: (Provider) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(current.label, modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.ExpandMore, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Provider.entries.forEach { p ->
                DropdownMenuItem(
                    text = { Text(p.label) },
                    onClick = {
                        onPick(p)
                        expanded = false
                    }
                )
            }
        }
    }
}
