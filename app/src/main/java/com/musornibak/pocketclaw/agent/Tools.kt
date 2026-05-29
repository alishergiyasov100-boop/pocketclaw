package com.musornibak.pocketclaw.agent

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.musornibak.pocketclaw.data.ConfirmLevel
import com.musornibak.pocketclaw.data.SettingsRepository
import com.musornibak.pocketclaw.service.ClawA11yService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
    private val gate: ConfirmGate,
    private val settings: SettingsRepository
) {

    private val bigTools = setOf("open_url", "launch_app", "type")

    val schemaJson: String = """
        [
          {"name":"open_url","desc":"Открыть URL в системном браузере","args":[["url","string"]]},
          {"name":"launch_app","desc":"Запустить приложение по package name","args":[["pkg","string"]]},
          {"name":"tap_text","desc":"Тапнуть по элементу с этим текстом или description","args":[["text","string"]]},
          {"name":"tap_xy","desc":"Тапнуть в координаты экрана","args":[["x","number"],["y","number"]]},
          {"name":"long_press","desc":"Долгий тап (~0.8с) в координаты","args":[["x","number"],["y","number"]]},
          {"name":"type","desc":"Напечатать текст в текущее активное поле ввода","args":[["text","string"]]},
          {"name":"scroll","desc":"Прокрутить экран","args":[["dir","forward|back"]]},
          {"name":"swipe","desc":"Свайп от (x1,y1) до (x2,y2) в пикселях","args":[["x1","number"],["y1","number"],["x2","number"],["y2","number"]]},
          {"name":"press_back","desc":"Нажать системную кнопку Назад","args":[]},
          {"name":"press_home","desc":"Нажать кнопку Домой (свернуть)","args":[]},
          {"name":"press_recents","desc":"Открыть список недавних приложений","args":[]},
          {"name":"open_notifications","desc":"Открыть шторку уведомлений","args":[]},
          {"name":"current_app","desc":"Узнать package name приложения на переднем плане","args":[]},
          {"name":"wait_for_text","desc":"Подождать пока на экране появится текст (ms — таймаут)","args":[["text","string"],["ms","number"]]},
          {"name":"read_screen","desc":"Прочитать UI текущего экрана (a11y дерево, кратко)","args":[]},
          {"name":"wait","desc":"Просто подождать N миллисекунд","args":[["ms","number"]]},
          {"name":"done","desc":"Задача выполнена, передать финальный ответ юзеру","args":[["summary","string"]]}
        ]
    """.trimIndent()

    suspend fun execute(toolName: String, args: Map<String, String>): ToolResult {
        when (toolName) {
            "read_screen" -> {
                val svc = ClawA11yService.get() ?: return ToolResult(false, "AccessibilityService не запущен")
                return ToolResult(true, svc.snapshotScreen())
            }
            "current_app" -> {
                val svc = ClawA11yService.get() ?: return ToolResult(false, "A11y не запущен")
                return ToolResult(true, svc.currentApp())
            }
            "wait_for_text" -> {
                val svc = ClawA11yService.get() ?: return ToolResult(false, "A11y не запущен")
                val text = args["text"].orEmpty()
                if (text.isBlank()) return ToolResult(false, "text пустой")
                val ms = args["ms"]?.toLongOrNull() ?: 5000L
                val ok = svc.waitForText(text, ms.coerceAtMost(30000))
                return ToolResult(ok, if (ok) "Текст появился: $text" else "Текст не появился за ${ms}мс")
            }
            "wait" -> {
                val ms = args["ms"]?.toLongOrNull() ?: 500L
                delay(ms.coerceAtMost(10000))
                return ToolResult(true, "Подождали ${ms}мс")
            }
            "done" -> return ToolResult(true, args["summary"].orEmpty())
        }

        val human = humanize(toolName, args)
        val level = settings.flow.first().confirmLevel
        val needAsk = when (level) {
            ConfirmLevel.None -> false
            ConfirmLevel.OnlyBig -> toolName in bigTools
            ConfirmLevel.EveryAction -> true
        }
        if (needAsk) {
            val allowed = gate.ask(toolName, args, human)
            if (!allowed) return ToolResult(false, "Юзер отказал: $human")
        }

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
            "long_press" -> {
                val svc = ClawA11yService.get() ?: return ToolResult(false, "A11y не запущен")
                val x = args["x"]?.toFloatOrNull() ?: return ToolResult(false, "x не число")
                val y = args["y"]?.toFloatOrNull() ?: return ToolResult(false, "y не число")
                val ok = svc.longPressXy(x, y)
                ToolResult(ok, if (ok) "Долгий тап в ($x,$y)" else "Жест не прошёл")
            }
            "swipe" -> {
                val svc = ClawA11yService.get() ?: return ToolResult(false, "A11y не запущен")
                val x1 = args["x1"]?.toFloatOrNull() ?: return ToolResult(false, "x1 не число")
                val y1 = args["y1"]?.toFloatOrNull() ?: return ToolResult(false, "y1 не число")
                val x2 = args["x2"]?.toFloatOrNull() ?: return ToolResult(false, "x2 не число")
                val y2 = args["y2"]?.toFloatOrNull() ?: return ToolResult(false, "y2 не число")
                val dur = args["ms"]?.toLongOrNull() ?: 300L
                val ok = svc.swipe(x1, y1, x2, y2, dur.coerceIn(50L, 3000L))
                ToolResult(ok, if (ok) "Свайп ($x1,$y1)→($x2,$y2)" else "Жест не прошёл")
            }
            "press_back" -> {
                val svc = ClawA11yService.get() ?: return ToolResult(false, "A11y не запущен")
                ToolResult(svc.pressBack(), "Назад")
            }
            "press_home" -> {
                val svc = ClawA11yService.get() ?: return ToolResult(false, "A11y не запущен")
                ToolResult(svc.pressHome(), "Домой")
            }
            "press_recents" -> {
                val svc = ClawA11yService.get() ?: return ToolResult(false, "A11y не запущен")
                ToolResult(svc.pressRecents(), "Недавние приложения")
            }
            "open_notifications" -> {
                val svc = ClawA11yService.get() ?: return ToolResult(false, "A11y не запущен")
                ToolResult(svc.openNotifications(), "Шторка уведомлений")
            }
            else -> ToolResult(false, "Неизвестный tool: $toolName")
        }
    }

    private fun humanize(name: String, args: Map<String, String>): String = when (name) {
        "open_url" -> "Открыть ссылку: ${args["url"]}"
        "launch_app" -> "Запустить приложение: ${args["pkg"]}"
        "tap_text" -> "Тап по «${args["text"]}»"
        "tap_xy" -> "Тап в (${args["x"]}, ${args["y"]})"
        "long_press" -> "Долгий тап в (${args["x"]}, ${args["y"]})"
        "type" -> "Напечатать: «${args["text"]}»"
        "scroll" -> "Прокрутить ${args["dir"] ?: "forward"}"
        "swipe" -> "Свайп (${args["x1"]},${args["y1"]})→(${args["x2"]},${args["y2"]})"
        "press_back" -> "Назад"
        "press_home" -> "Домой"
        "press_recents" -> "Недавние"
        "open_notifications" -> "Открыть уведомления"
        "current_app" -> "Текущее приложение"
        "wait_for_text" -> "Ждать «${args["text"]}»"
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
