package com.musornibak.pocketclaw.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class Provider(val label: String, val defaultBaseUrl: String, val defaultModel: String) {
    Groq("Groq (free, рекомендуется)", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"),
    OpenAI("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
    OpenRouter("OpenRouter", "https://openrouter.ai/api/v1", "meta-llama/llama-3.3-70b-instruct:free"),
    PocketQwal("PocketQwal Relay (HF)", "https://KorvusTheExplorer-pocketqwal-relay.hf.space/v1", "pocketqwal"),
    Custom("Custom", "https://", "");

    companion object {
        fun fromName(name: String?): Provider =
            entries.firstOrNull { it.name == name } ?: Groq
    }
}

enum class ConfirmLevel(val label: String) {
    None("Bypass (без подтверждений)"),
    OnlyBig("Только большие действия"),
    EveryAction("Каждое действие");

    companion object {
        fun fromName(name: String?): ConfirmLevel =
            entries.firstOrNull { it.name == name } ?: OnlyBig
    }
}

data class ApiSettings(
    val provider: Provider,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val systemPrompt: String,
    val confirmLevel: ConfirmLevel,
    val toolsPerSecond: Int,
    val maxHistoryMsgs: Int
)

private val Context.dataStore by preferencesDataStore(name = "pocketclaw_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val ctx: Context
) {
    private object Keys {
        val provider = stringPreferencesKey("provider")
        val baseUrl = stringPreferencesKey("base_url")
        val apiKey = stringPreferencesKey("api_key")
        val model = stringPreferencesKey("model")
        val systemPrompt = stringPreferencesKey("system_prompt")
        val confirmLevel = stringPreferencesKey("confirm_level")
        val toolsPerSecond = stringPreferencesKey("tools_per_second")
        val maxHistoryMsgs = stringPreferencesKey("max_history_msgs")
    }

    val flow: Flow<ApiSettings> = ctx.dataStore.data.map { p ->
        val provider = Provider.fromName(p[Keys.provider])
        ApiSettings(
            provider = provider,
            baseUrl = p[Keys.baseUrl]?.takeIf { it.isNotBlank() } ?: provider.defaultBaseUrl,
            apiKey = p[Keys.apiKey].orEmpty(),
            model = p[Keys.model]?.takeIf { it.isNotBlank() } ?: provider.defaultModel,
            systemPrompt = p[Keys.systemPrompt].orEmpty(),
            confirmLevel = ConfirmLevel.fromName(p[Keys.confirmLevel]),
            toolsPerSecond = p[Keys.toolsPerSecond]?.toIntOrNull() ?: 2,
            maxHistoryMsgs = p[Keys.maxHistoryMsgs]?.toIntOrNull() ?: 40
        )
    }

    suspend fun setProvider(p: Provider) {
        ctx.dataStore.edit {
            it[Keys.provider] = p.name
            it[Keys.baseUrl] = p.defaultBaseUrl
            it[Keys.model] = p.defaultModel
        }
    }

    suspend fun setBaseUrl(v: String) { ctx.dataStore.edit { it[Keys.baseUrl] = v } }
    suspend fun setApiKey(v: String) { ctx.dataStore.edit { it[Keys.apiKey] = v } }
    suspend fun setModel(v: String) { ctx.dataStore.edit { it[Keys.model] = v } }
    suspend fun setSystemPrompt(v: String) { ctx.dataStore.edit { it[Keys.systemPrompt] = v } }
    suspend fun setConfirmLevel(v: ConfirmLevel) { ctx.dataStore.edit { it[Keys.confirmLevel] = v.name } }
    suspend fun setToolsPerSecond(v: Int) { ctx.dataStore.edit { it[Keys.toolsPerSecond] = v.toString() } }
    suspend fun setMaxHistoryMsgs(v: Int) { ctx.dataStore.edit { it[Keys.maxHistoryMsgs] = v.toString() } }
}
