package com.musornibak.pocketclaw.agent

import com.musornibak.pocketclaw.data.ApiSettings
import com.musornibak.pocketclaw.data.ConfirmLevel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed class AgentEvent {
    data class Thought(val text: String) : AgentEvent()
    data class ToolCall(val name: String, val args: Map<String, String>, val human: String) : AgentEvent()
    data class Observation(val ok: Boolean, val text: String) : AgentEvent()
    data class Final(val text: String) : AgentEvent()
    data class Error(val text: String) : AgentEvent()
}

@Singleton
class ReActAgent @Inject constructor(
    private val llm: LlmClient,
    private val tools: Tools
) {
    private val _events = MutableSharedFlow<AgentEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<AgentEvent> = _events.asSharedFlow()

    suspend fun run(userTask: String, settings: ApiSettings, maxSteps: Int = 12) {
        val sys = systemPrompt(settings.systemPrompt, settings.confirmLevel)
        val history = mutableListOf(
            ChatMsg("system", sys),
            ChatMsg("user", userTask)
        )
        _events.emit(AgentEvent.Thought(
            "→ ${settings.provider.label} | ${settings.model} | ${settings.baseUrl}"
        ))

        var step = 0
        while (step < maxSteps) {
            step++
            val reply = runCatching { llm.complete(settings, history) }
                .getOrElse { e ->
                    val cls = e.javaClass.simpleName
                    val msg = e.message ?: "(нет сообщения)"
                    _events.emit(AgentEvent.Error("[$cls] $msg"))
                    return
                }
            history += ChatMsg("assistant", reply)

            val thought = reply.substringBefore('{').trim().ifBlank { null }
            if (thought != null) _events.emit(AgentEvent.Thought(thought))

            val parsed = Tools.parseToolCall(reply)
            if (parsed == null) {
                _events.emit(AgentEvent.Final(reply.trim()))
                return
            }
            val (name, args) = parsed
            if (name == "done") {
                _events.emit(AgentEvent.Final(args["summary"].orEmpty().ifBlank { "Готово." }))
                return
            }
            val human = humanize(name, args)
            _events.emit(AgentEvent.ToolCall(name, args, human))
            val result = tools.execute(name, args)
            _events.emit(AgentEvent.Observation(result.ok, result.observation))
            history += ChatMsg(
                "user",
                "Observation (${if (result.ok) "ok" else "fail"}): ${result.observation.take(2000)}"
            )
        }
        _events.emit(AgentEvent.Error("Превышен лимит шагов ($maxSteps)"))
    }

    private fun systemPrompt(custom: String, level: ConfirmLevel): String {
        val tools = """
            Доступные tools (вызывай по одному, JSON-объектом в конце сообщения):
            ${this.tools.schemaJson}
        """.trimIndent()
        val confirmRule = when (level) {
            ConfirmLevel.None -> "- Подтверждения отключены: действуй уверенно, но осторожно"
            ConfirmLevel.OnlyBig -> "- Юзер подтверждает только большие действия (open_url, launch_app, type); тапы и скролл идут без спроса. Если отказали — выбери другой путь"
            ConfirmLevel.EveryAction -> "- Юзер видит и одобряет каждое действие, кроме read_screen/done/wait. Не пытайся обходить confirm — если отказали, выбери другой путь"
        }
        val rules = """
            Правила:
            - Думай коротко на русском перед каждым tool-вызовом
            - Один tool-call за ответ. Формат:
              {"tool":"имя","args":{"ключ":"значение"}}
            - После каждого действия читай Observation и решай следующий шаг
            - Если задача выполнена — вызови {"tool":"done","args":{"summary":"…"}}
            - Если не уверен какой элемент тапнуть — сначала read_screen
            $confirmRule
        """.trimIndent()
        return buildString {
            if (custom.isNotBlank()) appendLine(custom).appendLine()
            appendLine("Ты — PocketClaw, агент управляющий Android-телефоном юзера.")
            appendLine()
            appendLine(tools)
            appendLine()
            appendLine(rules)
        }
    }

    private fun humanize(name: String, args: Map<String, String>): String = when (name) {
        "open_url" -> "Открыть ${args["url"]}"
        "launch_app" -> "Запустить ${args["pkg"]}"
        "tap_text" -> "Тап «${args["text"]}»"
        "tap_xy" -> "Тап (${args["x"]},${args["y"]})"
        "type" -> "Печать «${args["text"]}»"
        "scroll" -> "Скролл ${args["dir"] ?: "forward"}"
        "wait" -> "Ждать ${args["ms"]}мс"
        "read_screen" -> "Прочитать экран"
        else -> "$name"
    }
}
