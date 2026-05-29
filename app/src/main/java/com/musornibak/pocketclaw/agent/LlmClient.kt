package com.musornibak.pocketclaw.agent

import com.musornibak.pocketclaw.data.ApiSettings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ChatMsg(
    val role: String,
    val content: String,
    val imageB64: String? = null
)

data class LlmResponse(
    val content: String,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0
)

@Singleton
class LlmClient @Inject constructor() {
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val jsonMt = "application/json".toMediaType()

    suspend fun complete(settings: ApiSettings, messages: List<ChatMsg>): LlmResponse = withContext(Dispatchers.IO) {
        if (settings.baseUrl.isBlank()) error("base URL пуст")
        if (settings.model.isBlank()) error("модель не указана")
        val base = settings.baseUrl.trimEnd('/')
        val url = if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
        val lastImageIdx = messages.indexOfLast { it.imageB64 != null }
        val body = buildJsonObject {
            put("model", settings.model)
            put("temperature", 0.2)
            put("stream", false)
            putJsonArray("messages") {
                for ((i, m) in messages.withIndex()) {
                    add(buildJsonObject {
                        put("role", m.role)
                        if (m.imageB64 != null && i == lastImageIdx) {
                            putJsonArray("content") {
                                add(buildJsonObject {
                                    put("type", "text")
                                    put("text", m.content)
                                })
                                add(buildJsonObject {
                                    put("type", "image_url")
                                    putJsonObject("image_url") {
                                        put("url", "data:image/jpeg;base64,${m.imageB64}")
                                    }
                                })
                            }
                        } else {
                            put("content", m.content)
                        }
                    })
                }
            }
        }.toString().toRequestBody(jsonMt)

        val req = Request.Builder()
            .url(url)
            .post(body)
            .apply {
                if (settings.apiKey.isNotBlank()) {
                    header("Authorization", "Bearer ${settings.apiKey}")
                }
                header("Content-Type", "application/json")
            }
            .build()

        try {
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("HTTP ${resp.code} на $url\n${text.take(500)}")
                val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
                    ?: error("Битый JSON от $url: ${text.take(300)}")
                val choices = obj["choices"]?.jsonArray ?: error("Ответ без поля choices: ${text.take(300)}")
                val content = (choices[0] as JsonObject)["message"]?.jsonObject
                    ?.get("content")?.jsonPrimitive?.content ?: error("Ответ без content: ${text.take(300)}")
                val usage = obj["usage"]?.jsonObject
                LlmResponse(
                    content = content,
                    promptTokens = usage?.get("prompt_tokens")?.jsonPrimitive?.intOrNull ?: 0,
                    completionTokens = usage?.get("completion_tokens")?.jsonPrimitive?.intOrNull ?: 0
                )
            }
        } catch (e: java.net.UnknownHostException) {
            error("DNS: не могу найти хост ${e.message}")
        } catch (e: java.net.ConnectException) {
            error("Сеть: не могу подключиться — ${e.message}")
        } catch (e: javax.net.ssl.SSLException) {
            error("SSL: ${e.message}")
        } catch (e: java.io.IOException) {
            error("IO: ${e.message}")
        }
    }
}
