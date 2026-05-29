package com.musornibak.pocketclaw.agent

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.musornibak.pocketclaw.data.ConfirmLevel
import com.musornibak.pocketclaw.data.SettingsRepository
import com.musornibak.pocketclaw.service.ClawA11yService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class ToolResult(val ok: Boolean, val observation: String, val imageB64: String? = null)

@Singleton
class Tools @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val gate: ConfirmGate,
    private val settings: SettingsRepository
) {

    private val bigTools = setOf("open_url", "launch_app", "type", "shell", "write_file", "http_fetch")

    val validToolNames: Set<String> = setOf(
        "open_url", "launch_app", "tap_text", "tap_xy", "tap_node", "tap_desc",
        "long_press", "long_press_node", "type",
        "scroll", "swipe", "press_back", "press_home", "press_recents",
        "open_notifications", "current_app", "wait_for_text", "read_screen",
        "shell", "http_fetch", "read_file", "write_file", "list_files",
        "clipboard_read", "clipboard_write", "wait", "done"
    )

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private fun resolvePath(path: String): File =
        if (path.startsWith("/")) File(path) else File(ctx.filesDir, path)

    val schemaJson: String = """
        [
          {"name":"open_url","desc":"Открыть URL в системном браузере","args":[["url","string"]]},
          {"name":"launch_app","desc":"Запустить приложение по package name","args":[["pkg","string"]]},
          {"name":"tap_text","desc":"Тапнуть по элементу с этим текстом","args":[["text","string"]]},
          {"name":"tap_xy","desc":"Тапнуть в координаты экрана","args":[["x","number"],["y","number"]]},
          {"name":"tap_node","desc":"Точный тап по индексу #i из последнего read_screen (рекомендуется когда сомневаешься)","args":[["i","number"]]},
          {"name":"tap_desc","desc":"Тапнуть по элементу с этим content-description (хорошо для иконок без текста)","args":[["desc","string"]]},
          {"name":"long_press","desc":"Долгий тап (~0.8с) в координаты","args":[["x","number"],["y","number"]]},
          {"name":"long_press_node","desc":"Долгий тап по индексу #i из последнего read_screen","args":[["i","number"]]},
          {"name":"type","desc":"Напечатать текст в текущее активное поле ввода","args":[["text","string"]]},
          {"name":"scroll","desc":"Прокрутить экран","args":[["dir","forward|back"]]},
          {"name":"swipe","desc":"Свайп от (x1,y1) до (x2,y2) в пикселях","args":[["x1","number"],["y1","number"],["x2","number"],["y2","number"]]},
          {"name":"press_back","desc":"Нажать системную кнопку Назад","args":[]},
          {"name":"press_home","desc":"Нажать кнопку Домой (свернуть)","args":[]},
          {"name":"press_recents","desc":"Открыть список недавних приложений","args":[]},
          {"name":"open_notifications","desc":"Открыть шторку уведомлений","args":[]},
          {"name":"current_app","desc":"Узнать package name приложения на переднем плане","args":[]},
          {"name":"wait_for_text","desc":"Подождать пока на экране появится текст (ms — таймаут)","args":[["text","string"],["ms","number"]]},
          {"name":"read_screen","desc":"Прочитать UI: размер экрана + список интерактивных узлов с координатами центра и индексом #i. Используй tap_node по индексу для точного попадания.","args":[]},
          {"name":"shell","desc":"Выполнить sh-команду в песочнице app (ограничено UID приложения). Полезно для ls/cat/echo в filesDir.","args":[["cmd","string"],["ms","number"]]},
          {"name":"http_fetch","desc":"HTTP-запрос (GET/POST). Возвращает status+тело (≤8k).","args":[["url","string"],["method","string"],["body","string"]]},
          {"name":"read_file","desc":"Прочитать текст файла. Относительные пути — внутри filesDir приложения.","args":[["path","string"]]},
          {"name":"write_file","desc":"Записать текст в файл (перезапишет). Относительные пути — внутри filesDir.","args":[["path","string"],["content","string"]]},
          {"name":"list_files","desc":"Список файлов в директории (относительной к filesDir или абсолютной).","args":[["path","string"]]},
          {"name":"clipboard_read","desc":"Прочитать системный буфер обмена","args":[]},
          {"name":"clipboard_write","desc":"Записать текст в системный буфер обмена","args":[["text","string"]]},
          {"name":"wait","desc":"Просто подождать N миллисекунд","args":[["ms","number"]]},
          {"name":"done","desc":"Задача выполнена, передать финальный ответ юзеру","args":[["summary","string"]]}
        ]
    """.trimIndent()

    suspend fun execute(toolName: String, args: Map<String, String>): ToolResult {
        if (toolName !in validToolNames) {
            return ToolResult(
                false,
                "Tool «$toolName» НЕ существует. Используй ТОЛЬКО: ${validToolNames.joinToString(", ")}. Повтори вызов с правильным именем."
            )
        }
        when (toolName) {
            "read_screen" -> {
                val svc = ClawA11yService.get() ?: return ToolResult(false, "AccessibilityService не запущен")
                val text = svc.snapshotScreen()
                val s = settings.flow.first()
                val img = if (s.visionMode) svc.takeScreenshotB64() else null
                val suffix = if (img != null) "\n[vision: скриншот прикреплён]" else ""
                return ToolResult(true, text + suffix, imageB64 = img)
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
            "tap_node" -> {
                val svc = ClawA11yService.get() ?: return ToolResult(false, "A11y не запущен")
                val i = args["i"]?.toIntOrNull() ?: return ToolResult(false, "i не число")
                val ok = svc.tapNode(i)
                ToolResult(ok, if (ok) "Тап по #$i" else "Нет узла #$i — сделай read_screen заново")
            }
            "tap_desc" -> {
                val svc = ClawA11yService.get() ?: return ToolResult(false, "A11y не запущен")
                val desc = args["desc"].orEmpty()
                if (desc.isBlank()) return ToolResult(false, "desc пустой")
                val ok = svc.tapDesc(desc)
                ToolResult(ok, if (ok) "Тап по desc=«$desc»" else "Не нашёл элемент с desc=«$desc»")
            }
            "long_press_node" -> {
                val svc = ClawA11yService.get() ?: return ToolResult(false, "A11y не запущен")
                val i = args["i"]?.toIntOrNull() ?: return ToolResult(false, "i не число")
                val ok = svc.longPressNode(i)
                ToolResult(ok, if (ok) "Долгий тап по #$i" else "Нет узла #$i")
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
            "shell" -> execShell(args["cmd"].orEmpty(), args["ms"]?.toLongOrNull() ?: 8000L)
            "http_fetch" -> httpFetch(
                args["url"].orEmpty(),
                (args["method"] ?: "GET").uppercase(),
                args["body"]
            )
            "read_file" -> readFile(args["path"].orEmpty())
            "write_file" -> writeFile(args["path"].orEmpty(), args["content"].orEmpty())
            "list_files" -> listFiles(args["path"].orEmpty().ifBlank { "." })
            "clipboard_read" -> {
                val cb = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = cb.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                ToolResult(true, if (text.isEmpty()) "(буфер пуст)" else text.take(2000))
            }
            "clipboard_write" -> {
                val cb = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val t = args["text"].orEmpty()
                cb.setPrimaryClip(ClipData.newPlainText("pocketclaw", t))
                ToolResult(true, "Записано в буфер (${t.length} симв.)")
            }
            else -> ToolResult(false, "Неизвестный tool: $toolName")
        }
    }

    private suspend fun execShell(cmd: String, timeoutMs: Long): ToolResult =
        withContext(Dispatchers.IO) {
            if (cmd.isBlank()) return@withContext ToolResult(false, "cmd пустой")
            runCatching {
                val p = ProcessBuilder("sh", "-c", cmd)
                    .directory(ctx.filesDir)
                    .redirectErrorStream(true)
                    .start()
                val finished = p.waitFor(timeoutMs.coerceIn(500L, 60000L), TimeUnit.MILLISECONDS)
                if (!finished) {
                    p.destroyForcibly()
                    return@runCatching ToolResult(false, "timeout ${timeoutMs}мс")
                }
                val out = p.inputStream.bufferedReader().readText().take(4000)
                val exit = p.exitValue()
                ToolResult(exit == 0, "[exit=$exit] cwd=${ctx.filesDir}\n$out".trim())
            }.getOrElse { ToolResult(false, "Shell error: ${it.message}") }
        }

    private suspend fun httpFetch(url: String, method: String, body: String?): ToolResult =
        withContext(Dispatchers.IO) {
            if (url.isBlank()) return@withContext ToolResult(false, "url пустой")
            runCatching {
                val req = Request.Builder().url(url).apply {
                    when (method) {
                        "GET", "" -> get()
                        "POST" -> post((body.orEmpty()).toRequestBody("application/json".toMediaTypeOrNull()))
                        "DELETE" -> delete()
                        else -> method(method, body?.toRequestBody("application/json".toMediaTypeOrNull()))
                    }
                }.build()
                http.newCall(req).execute().use { resp ->
                    val text = resp.body?.string()?.take(8000).orEmpty()
                    ToolResult(resp.isSuccessful, "[$method ${resp.code}]\n$text")
                }
            }.getOrElse { ToolResult(false, "HTTP error: ${it.javaClass.simpleName}: ${it.message}") }
        }

    private suspend fun readFile(path: String): ToolResult = withContext(Dispatchers.IO) {
        if (path.isBlank()) return@withContext ToolResult(false, "path пустой")
        runCatching {
            val f = resolvePath(path)
            if (!f.exists()) return@runCatching ToolResult(false, "Нет файла: ${f.absolutePath}")
            if (!f.canRead()) return@runCatching ToolResult(false, "Нет доступа: ${f.absolutePath}")
            val text = f.readText().take(8000)
            ToolResult(true, "[${f.absolutePath}] ${f.length()}b\n$text")
        }.getOrElse { ToolResult(false, "Ошибка чтения: ${it.message}") }
    }

    private suspend fun writeFile(path: String, content: String): ToolResult =
        withContext(Dispatchers.IO) {
            if (path.isBlank()) return@withContext ToolResult(false, "path пустой")
            runCatching {
                val f = resolvePath(path)
                f.parentFile?.mkdirs()
                f.writeText(content)
                ToolResult(true, "Записано ${content.length}b → ${f.absolutePath}")
            }.getOrElse { ToolResult(false, "Ошибка записи: ${it.message}") }
        }

    private suspend fun listFiles(path: String): ToolResult = withContext(Dispatchers.IO) {
        runCatching {
            val dir = resolvePath(path)
            if (!dir.exists()) return@runCatching ToolResult(false, "Нет директории: ${dir.absolutePath}")
            if (!dir.isDirectory) return@runCatching ToolResult(false, "Не директория: ${dir.absolutePath}")
            val entries = dir.listFiles()?.sortedBy { it.name }?.joinToString("\n") {
                val kind = if (it.isDirectory) "d" else "-"
                "$kind ${it.length()}\t${it.name}"
            } ?: "(пусто)"
            ToolResult(true, "[${dir.absolutePath}]\n$entries")
        }.getOrElse { ToolResult(false, "Ошибка: ${it.message}") }
    }

    private fun humanize(name: String, args: Map<String, String>): String = when (name) {
        "open_url" -> "Открыть ссылку: ${args["url"]}"
        "launch_app" -> "Запустить приложение: ${args["pkg"]}"
        "tap_text" -> "Тап по «${args["text"]}»"
        "tap_xy" -> "Тап в (${args["x"]}, ${args["y"]})"
        "tap_node" -> "Тап по #${args["i"]}"
        "tap_desc" -> "Тап по desc=«${args["desc"]}»"
        "long_press" -> "Долгий тап в (${args["x"]}, ${args["y"]})"
        "long_press_node" -> "Долгий тап по #${args["i"]}"
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
        "shell" -> "Shell: ${args["cmd"]?.take(60)}"
        "http_fetch" -> "${args["method"] ?: "GET"} ${args["url"]?.take(80)}"
        "read_file" -> "Прочитать ${args["path"]}"
        "write_file" -> "Записать ${args["path"]} (${args["content"]?.length ?: 0}b)"
        "list_files" -> "ls ${args["path"]}"
        "clipboard_read" -> "Прочитать буфер"
        "clipboard_write" -> "Записать в буфер (${args["text"]?.length ?: 0}b)"
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
