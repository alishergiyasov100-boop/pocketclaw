package com.musornibak.pocketclaw.agent

import com.musornibak.pocketclaw.data.ApiSettings
import com.musornibak.pocketclaw.data.ConfirmLevel
import kotlinx.coroutines.delay
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
    data class Usage(val promptTokens: Int, val completionTokens: Int) : AgentEvent()
}

@Singleton
class ReActAgent @Inject constructor(
    private val llm: LlmClient,
    private val tools: Tools
) {
    private val _events = MutableSharedFlow<AgentEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<AgentEvent> = _events.asSharedFlow()

    suspend fun run(
        userTask: String,
        settings: ApiSettings,
        history: MutableList<ChatMsg>,
        maxSteps: Int = Int.MAX_VALUE
    ) {
        if (history.none { it.role == "system" }) {
            history += ChatMsg("system", systemPrompt(settings.systemPrompt, settings.confirmLevel))
            history += warmupShots()
        }
        history += ChatMsg("user", userTask)
        _events.emit(AgentEvent.Thought(
            "→ ${settings.provider.label} | ${settings.model} | ${settings.baseUrl}"
        ))

        val minGapMs = if (settings.toolsPerSecond > 0) 1000L / settings.toolsPerSecond else 0L
        var lastToolMs = 0L
        var step = 0
        while (step < maxSteps) {
            step++
            val resp = runCatching { llm.complete(settings, history) }
                .getOrElse { e ->
                    val cls = e.javaClass.simpleName
                    val msg = e.message ?: "(нет сообщения)"
                    _events.emit(AgentEvent.Error("[$cls] $msg"))
                    return
                }
            val reply = resp.content
            if (resp.promptTokens > 0 || resp.completionTokens > 0) {
                _events.emit(AgentEvent.Usage(resp.promptTokens, resp.completionTokens))
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
            if (minGapMs > 0) {
                val wait = minGapMs - (System.currentTimeMillis() - lastToolMs)
                if (wait > 0) delay(wait)
            }
            val human = humanize(name, args)
            _events.emit(AgentEvent.ToolCall(name, args, human))
            val result = tools.execute(name, args)
            lastToolMs = System.currentTimeMillis()
            _events.emit(AgentEvent.Observation(result.ok, result.observation))
            history += ChatMsg(
                "user",
                "Observation (${if (result.ok) "ok" else "fail"}): ${result.observation.take(2000)}"
            )
        }
    }

    private fun systemPrompt(custom: String, level: ConfirmLevel): String {
        val whitelist = tools.validToolNames.joinToString(", ")
        val toolsBlock = """
            === БЕЛЫЙ СПИСОК TOOLS (других НЕТ, вызов любого другого ОТКЛОНЯЕТСЯ) ===
            $whitelist

            Полные сигнатуры:
            ${tools.schemaJson}
        """.trimIndent()
        val confirmRule = when (level) {
            ConfirmLevel.None -> "- Подтверждения отключены: действуй уверенно, но осторожно"
            ConfirmLevel.OnlyBig -> "- Юзер подтверждает только большие действия (open_url, launch_app, type, shell, write_file, http_fetch); тапы/скролл/файл-чтение/буфер идут без спроса. Если отказали — выбери другой путь"
            ConfirmLevel.EveryAction -> "- Юзер видит и одобряет каждое действие, кроме read_screen/done/wait. Не пытайся обходить confirm — если отказали, выбери другой путь"
        }
        val rules = """
            Правила (СТРОГО):
            - НИКОГДА не выдумывай tool вне белого списка. Нет тулзы — используй комбинацию имеющихся или скажи done с честным summary что не можешь
            - Думай коротко на русском перед каждым tool-вызовом
            - Один tool-call за ответ. Формат строго:
              {"tool":"<имя_из_белого_списка>","args":{"ключ":"значение"}}
            - Никаких других имён. Никаких суффиксов/префиксов. Имя — буква в букву как в списке
            - После каждого действия читай Observation и решай следующий шаг
            - Если задача выполнена — вызови {"tool":"done","args":{"summary":"…"}}
            - Если не уверен какой элемент тапнуть — сначала read_screen
            $confirmRule
        """.trimIndent()
        return buildString {
            if (custom.isNotBlank()) appendLine(custom).appendLine()
            appendLine("Ты — PocketClaw, агент управляющий Android-телефоном юзера.")
            appendLine()
            appendLine(toolsBlock)
            appendLine()
            appendLine(rules)
        }
    }

    private fun warmupShots(): List<ChatMsg> = listOf(
        ChatMsg("user", "[пример 1] открой example.com"),
        ChatMsg(
            "assistant",
            "Открою сайт через open_url.\n{\"tool\":\"open_url\",\"args\":{\"url\":\"https://example.com\"}}"
        ),
        ChatMsg("user", "Observation (ok): Открыт https://example.com"),
        ChatMsg(
            "assistant",
            "Сайт открыт, задача выполнена.\n{\"tool\":\"done\",\"args\":{\"summary\":\"Открыл example.com\"}}"
        ),
        ChatMsg("user", "[пример 2] нажми кнопку «Войти»"),
        ChatMsg(
            "assistant",
            "Сначала прочитаю экран, чтобы убедиться что кнопка видна.\n{\"tool\":\"read_screen\",\"args\":{}}"
        ),
        ChatMsg("user", "Observation (ok): Button «Войти» (text), Button «Регистрация» (text)"),
        ChatMsg(
            "assistant",
            "Кнопка есть, тапаю.\n{\"tool\":\"tap_text\",\"args\":{\"text\":\"Войти\"}}"
        ),
        ChatMsg("user", "Observation (ok): Тап выполнен"),
        ChatMsg(
            "assistant",
            "Готово.\n{\"tool\":\"done\",\"args\":{\"summary\":\"Нажал «Войти»\"}}"
        ),
        ChatMsg(
            "user",
            "Хорошо, ты освоил формат. Теперь начнём настоящую задачу. Всегда отвечай одним JSON-блоком " +
            "{\"tool\":\"…\",\"args\":{…}} в конце каждого сообщения."
        ),
        ChatMsg("assistant", "Понял, жду задачу.")
    )

    private fun humanize(name: String, args: Map<String, String>): String = when (name) {
        "open_url" -> "Открыть ${args["url"]}"
        "launch_app" -> "Запустить ${args["pkg"]}"
        "tap_text" -> "Тап «${args["text"]}»"
        "tap_xy" -> "Тап (${args["x"]},${args["y"]})"
        "long_press" -> "Долгий тап (${args["x"]},${args["y"]})"
        "type" -> "Печать «${args["text"]}»"
        "scroll" -> "Скролл ${args["dir"] ?: "forward"}"
        "swipe" -> "Свайп (${args["x1"]},${args["y1"]})→(${args["x2"]},${args["y2"]})"
        "press_back" -> "Назад"
        "press_home" -> "Домой"
        "press_recents" -> "Недавние"
        "open_notifications" -> "Уведомления"
        "current_app" -> "Текущее приложение"
        "wait_for_text" -> "Ждать «${args["text"]}»"
        "wait" -> "Ждать ${args["ms"]}мс"
        "read_screen" -> "Прочитать экран"
        "shell" -> "Shell: ${args["cmd"]?.take(40)}"
        "http_fetch" -> "${args["method"] ?: "GET"} ${args["url"]?.take(60)}"
        "read_file" -> "Прочитать ${args["path"]}"
        "write_file" -> "Записать ${args["path"]}"
        "list_files" -> "ls ${args["path"]}"
        "clipboard_read" -> "Буфер →"
        "clipboard_write" -> "Буфер ←"
        else -> "$name"
    }
}
