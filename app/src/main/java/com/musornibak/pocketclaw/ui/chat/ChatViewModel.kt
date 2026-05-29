package com.musornibak.pocketclaw.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musornibak.pocketclaw.agent.AgentEvent
import com.musornibak.pocketclaw.agent.ChatMsg
import com.musornibak.pocketclaw.agent.ReActAgent
import com.musornibak.pocketclaw.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class TurnKind { User, Thought, ToolCall, Observation, Final, Error }

data class ChatTurn(
    val kind: TurnKind,
    val text: String,
    val ok: Boolean = true
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val agent: ReActAgent,
    private val settings: SettingsRepository
) : ViewModel() {
    private val _turns = MutableStateFlow<List<ChatTurn>>(emptyList())
    val turns: StateFlow<List<ChatTurn>> = _turns.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private var job: Job? = null
    private val history = mutableListOf<ChatMsg>()

    init {
        viewModelScope.launch {
            agent.events.collect { ev ->
                when (ev) {
                    is AgentEvent.Thought -> append(ChatTurn(TurnKind.Thought, ev.text))
                    is AgentEvent.ToolCall -> append(ChatTurn(TurnKind.ToolCall, ev.human))
                    is AgentEvent.Observation -> append(ChatTurn(TurnKind.Observation, ev.text, ev.ok))
                    is AgentEvent.Final -> {
                        append(ChatTurn(TurnKind.Final, ev.text))
                        _running.value = false
                    }
                    is AgentEvent.Error -> {
                        append(ChatTurn(TurnKind.Error, ev.text, false))
                        _running.value = false
                    }
                }
            }
        }
    }

    fun send(task: String) {
        if (_running.value || task.isBlank()) return
        append(ChatTurn(TurnKind.User, task))
        _running.value = true
        job = viewModelScope.launch {
            val s = settings.flow.first()
            if (s.baseUrl.isBlank() || s.model.isBlank()) {
                append(ChatTurn(TurnKind.Error, "Сначала настрой API → раздел API в дровере", false))
                _running.value = false
                return@launch
            }
            agent.run(task, s, history)
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _running.value = false
        append(ChatTurn(TurnKind.Error, "Остановлено юзером", false))
    }

    fun clear() {
        _turns.value = emptyList()
        history.clear()
    }

    private fun append(t: ChatTurn) {
        _turns.value = _turns.value + t
    }
}
