package com.musornibak.pocketclaw.ui.confirm

import androidx.lifecycle.ViewModel
import com.musornibak.pocketclaw.agent.ConfirmGate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class ConfirmViewModel @Inject constructor(
    private val gate: ConfirmGate
) : ViewModel() {
    val pending: StateFlow<com.musornibak.pocketclaw.agent.PendingAction?> = gate.pending

    fun allow() = gate.resolve(true)
    fun deny() = gate.resolve(false)
}
