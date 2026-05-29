package com.musornibak.pocketclaw.ui.chat

import androidx.lifecycle.ViewModel
import com.musornibak.pocketclaw.agent.ChatStore
import com.musornibak.pocketclaw.agent.ChatTurn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val store: ChatStore
) : ViewModel() {
    val turns: StateFlow<List<ChatTurn>> = store.turns
    val running: StateFlow<Boolean> = store.running
    val tokens: StateFlow<Pair<Int, Int>> = store.tokens

    fun send(task: String) = store.send(task)
    fun stop() = store.stop()
    fun clear() = store.clear()
}
