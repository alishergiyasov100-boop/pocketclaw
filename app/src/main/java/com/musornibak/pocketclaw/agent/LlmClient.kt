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
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ChatMsg(val role: String, val content: String)

@Singleton
class LlmClient @Inject constructor() {
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val jsonMt = "application/json".toMediaType()

    suspend fun complete(settings: ApiSettings, messages: List<ChatMsg>): String {
        val base = settings.baseUrl.trimEnd('/')
        val url = "$base/chat/completions"
        val body = buildJsonObject {
            put("model", settings.model)
            put("temperature", 0.2)
            put("stream", false)
            putJsonArray("messages") {
                for (m in messages) {
                    add(buildJsonObject {
                        put("role", m.role)
                        put("content", m.content)
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

        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("HTTP ${resp.code}: ${text.take(300)}")
            val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
                ?: error("Bad JSON: ${text.take(200)}")
            val choices = obj["choices"]?.jsonArray ?: error("no choices")
            val content = (choices[0] as JsonObject)["message"]?.jsonObject
                ?.get("content")?.jsonPrimitive?.content ?: error("no content")
            return content
        }
    }
}
