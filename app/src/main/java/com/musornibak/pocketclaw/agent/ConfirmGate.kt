package com.musornibak.pocketclaw.agent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class PendingAction(
    val id: Long,
    val toolName: String,
    val args: Map<String, String>,
    val human: String
)

@Singleton
class ConfirmGate @Inject constructor() {
    private val _pending = MutableStateFlow<PendingAction?>(null)
    val pending: StateFlow<PendingAction?> = _pending.asStateFlow()

    private var resolver: ((Boolean) -> Unit)? = null
    private var seq = 0L

    suspend fun ask(toolName: String, args: Map<String, String>, human: String): Boolean =
        suspendCancellableCoroutine { cont ->
            val id = ++seq
            resolver = { ok -> cont.resume(ok) }
            _pending.value = PendingAction(id, toolName, args, human)
            cont.invokeOnCancellation {
                if (_pending.value?.id == id) {
                    _pending.value = null
                    resolver = null
                }
            }
        }

    fun resolve(ok: Boolean) {
        val r = resolver
        resolver = null
        _pending.value = null
        r?.invoke(ok)
    }
}
