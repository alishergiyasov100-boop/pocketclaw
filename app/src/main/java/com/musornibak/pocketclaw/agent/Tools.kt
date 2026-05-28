package com.musornibak.pocketclaw.agent

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.musornibak.pocketclaw.service.ClawA11yService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

data class ToolResult(val ok: Boolean, val observation: String)

@Singleton
class Tools @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val gate: ConfirmGate
) {

    val schemaJson: String = """
        [
          {"name":"open_url","desc":"Открыть URL в системном браузере","args":[["url","string"]]},
          {"name":"launch_app","desc":"Запустить приложение по package name","args":[["pkg","string"]]},
          {"name":"tap_text","desc":"Тапнуть по элементу с этим текстом или description","args":[["text","string"]]},
          {"name":"tap_xy","desc":"Тапнуть в координаты экрана","args":[["x","number"],["y","number"]]},
          {"name":"type","desc":"Напечатать текст в текущее активное поле ввода","args":[["text","string"]]},
          {"name":"scroll","desc":"Прокрутить экран","args":[["dir","forward|back"]]},
          {"name":"read_screen","desc":"Прочитать UI текущего экрана (a11y дерево, кратко)","args":[]},
          {"name":"wait","desc":"Подождать перед следующим шагом","args":[["ms","number"]]},
          {"name":"done","desc":"Задача выполнена, передать финальный ответ юзеру","args":[["summary","string"]]}
        ]
    """.trimIndent()

    suspend fun execute(toolName: String, args: Map<String, String>): ToolResult {
        if (toolName == "read_screen") {
            val svc = ClawA11yService.get() ?: return ToolResult(false, "AccessibilityService не запущен")
            return ToolResult(true, svc.snapshotScreen())
        }
        if (toolName == "done") {
            return ToolResult(true, args["summary"].orEmpty())
        }

        val human = humanize(toolName, args)
        val allowed = gate.ask(toolName, args, human)
        if (!allowed) return ToolResult(false, "Юзер отказал: $human")

        return when (toolName) {
            "open_url" -> {
                val url = args["url"].orEmpty()
                if (url.isBlank()) return ToolResult(false, "url пустой")
                runCatching {
                    val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    ctx.startActivity(i)
                }.fold(
                    onSuccess = { ToolResult(true, "Открыт $url") },
                    onFailure = { ToolResult(false, "Ошибка: ${it.message}") }
                )
            }
            "launch_app" -> {
                val pkg = args["pkg"].orEmpty()
                val pm = ctx.packageManager
                val i = pm.getLaunchIntentForPackage(pkg)
                if (i == null) ToolResult(false, "Пакет не найден: $pkg")
                else {
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { ctx.startActivity(i) }
                        .fold({ ToolResult(true, "Запущен $pkg") },
                            { ToolResult(false, "Ошибка: ${it.message}") })
                }
            }
            "tap_text" -> {
                val svc = ClawA11yService.get() ?: return ToolResult(false, "A11y не запущен")
                val ok = svc.tapText(args["text"].orEmpty())
                ToolResult(ok, if (ok) "Тап выполнен" else "Не нашёл элемент")
            }
            "tap_xy" -> {
                val svc = ClawA11yService.get() ?: return ToolResult(false, "A11y не запущен")
                val x = args["x"]?.toFloatOrNull() ?: return ToolResult(false, "x не число")
                val y = args["y"]?.toFloatOrNull() ?: return ToolResult(false, "y не число")
                val ok = svc.tapXy(x, y)
                ToolResult(ok, if (ok) "Тап в ($x,$y)" else "Жест не прошёл")
            }
            "type" -> {
                val svc = ClawA11yService.get() ?: return ToolResult(false, "A11y не запущен")
                val ok = svc.typeInFocused(args["text"].orEmpty())
                ToolResult(ok, if (ok) "Напечатано" else "Нет активного поля")
            }
            "scroll" -> {
                val svc = ClawA11yService.get() ?: return ToolResult(false, "A11y не запущен")
                val forward = (args["dir"] ?: "forward").lowercase() != "back"
                val ok = svc.scrollAny(forward)
                ToolResult(ok, if (ok) "Прокручено" else "Нет скроллируемого элемента")
            }
            "wait" -> {
                val ms = args["ms"]?.toLongOrNull() ?: 500L
                delay(ms.coerceAtMost(5000))
                ToolResult(true, "Подождали ${ms}мс")
            }
            else -> ToolResult(false, "Неизвестный tool: $toolName")
        }
    }

    private fun humanize(name: String, args: Map<String, String>): String = when (name) {
        "open_url" -> "Открыть ссылку: ${args["url"]}"
        "launch_app" -> "Запустить приложение: ${args["pkg"]}"
        "tap_text" -> "Тап по «${args["text"]}»"
        "tap_xy" -> "Тап в (${args["x"]}, ${args["y"]})"
        "type" -> "Напечатать: «${args["text"]}»"
        "scroll" -> "Прокрутить ${args["dir"] ?: "forward"}"
        "wait" -> "Подождать ${args["ms"] ?: 500}мс"
        else -> "$name $args"
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun parseToolCall(text: String): Pair<String, Map<String, String>>? {
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            val raw = text.substring(start, end + 1)
            val obj = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
            val name = obj["tool"]?.jsonPrimitive?.contentOrNullSafe() ?: return null
            val argsObj = obj["args"] as? JsonObject ?: JsonObject(emptyMap())
            val args = argsObj.mapValues { (_, v) ->
                (v as? JsonPrimitive)?.content ?: v.toString()
            }
            return name to args
        }

        private fun JsonPrimitive.contentOrNullSafe(): String? =
            if (isString) content else content.takeIf { it.isNotBlank() }
    }
}
