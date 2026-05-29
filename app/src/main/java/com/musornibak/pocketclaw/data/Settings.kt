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

data class ApiSettings(
    val provider: Provider,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val systemPrompt: String
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
    }

    val flow: Flow<ApiSettings> = ctx.dataStore.data.map { p ->
        val provider = Provider.fromName(p[Keys.provider])
        ApiSettings(
            provider = provider,
            baseUrl = p[Keys.baseUrl]?.takeIf { it.isNotBlank() } ?: provider.defaultBaseUrl,
            apiKey = p[Keys.apiKey].orEmpty(),
            model = p[Keys.model]?.takeIf { it.isNotBlank() } ?: provider.defaultModel,
            systemPrompt = p[Keys.systemPrompt].orEmpty()
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
}
