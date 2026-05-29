package com.musornibak.pocketclaw.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musornibak.pocketclaw.agent.ChatMsg
import com.musornibak.pocketclaw.agent.LlmClient
import com.musornibak.pocketclaw.data.ApiSettings
import com.musornibak.pocketclaw.data.ConfirmLevel
import com.musornibak.pocketclaw.data.Provider
import com.musornibak.pocketclaw.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val llm: LlmClient
) : ViewModel() {

    val settings: StateFlow<ApiSettings?> = repo.flow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _testResult = MutableStateFlow<Pair<Boolean, String>?>(null)
    val testResult: StateFlow<Pair<Boolean, String>?> = _testResult.asStateFlow()

    fun setProvider(p: Provider) = viewModelScope.launch { repo.setProvider(p) }
    fun setBaseUrl(v: String) = viewModelScope.launch { repo.setBaseUrl(v) }
    fun setApiKey(v: String) = viewModelScope.launch { repo.setApiKey(v) }
    fun setModel(v: String) = viewModelScope.launch { repo.setModel(v) }
    fun setSystemPrompt(v: String) = viewModelScope.launch { repo.setSystemPrompt(v) }
    fun setConfirmLevel(v: ConfirmLevel) = viewModelScope.launch { repo.setConfirmLevel(v) }
    fun setToolsPerSecond(v: Int) = viewModelScope.launch { repo.setToolsPerSecond(v) }
    fun setMaxHistoryMsgs(v: Int) = viewModelScope.launch { repo.setMaxHistoryMsgs(v) }
    fun setBubbleEnabled(v: Boolean) = viewModelScope.launch { repo.setBubbleEnabled(v) }

    fun runTest(override: ApiSettings? = null) = viewModelScope.launch {
        _testResult.value = null
        val s = override ?: repo.flow.first()
        runCatching {
            llm.complete(s, listOf(ChatMsg("user", "ответь одним словом: ping")))
        }.fold(
            onSuccess = { _testResult.value = true to it.content.take(200) },
            onFailure = { e ->
                val cls = e.javaClass.simpleName
                val msg = e.message ?: "(нет сообщения)"
                val stack = e.stackTraceToString().lineSequence()
                    .take(8).joinToString("\n")
                val full = buildString {
                    append("URL: ").append(s.baseUrl).append('\n')
                    append("Model: ").append(s.model).append('\n')
                    append("Provider: ").append(s.provider.name).append('\n')
                    append("ApiKey: ").append(if (s.apiKey.isBlank()) "(пусто)" else "***${s.apiKey.takeLast(4)}").append("\n\n")
                    append("[$cls] ").append(msg).append("\n\n")
                    append(stack)
                }
                _testResult.value = false to full
            }
        )
    }
}
