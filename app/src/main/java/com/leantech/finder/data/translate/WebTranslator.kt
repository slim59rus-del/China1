package com.leantech.finder.data.translate

import com.leantech.finder.data.net.MtopClient
import com.leantech.finder.data.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class WebTranslator(
    private val settings: SettingsStore,
    private val http: OkHttpClient = defaultClient(),
) : Translator {

    override suspend fun toChinese(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        if (isMostlyChinese(trimmed)) return trimmed

        val primary = translateGoogle(trimmed, source = "ru", target = "zh-CN")
        if (!primary.isNullOrBlank()) return primary

        return translateMyMemory(trimmed, source = "ru", target = "zh-CN")
    }

    override suspend fun toRussian(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val primary = translateGoogle(trimmed, source = "zh-CN", target = "ru")
        if (!primary.isNullOrBlank()) return primary
        return translateMyMemory(trimmed, source = "zh-CN", target = "ru")
    }

    private suspend fun translateGoogle(text: String, source: String, target: String): String? =
        withContext(Dispatchers.IO) {
            val endpoint = settings.translatorEndpoint.ifBlank { SettingsStore.DEFAULT_TRANSLATOR }
            val separator = if (endpoint.contains('?')) '&' else '?'
            val url = endpoint.trimEnd('/') + separator +
                "client=gtx&sl=" + source + "&tl=" + target + "&dt=t&q=" +
                URLEncoder.encode(text, "UTF-8")

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", MtopClient.MOBILE_UA)
                .build()

            runCatching {
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val body = response.body?.string().orEmpty()
                    parseGoogle(body)
                }
            }.getOrNull()
        }

    private suspend fun translateMyMemory(text: String, source: String, target: String): String? =
        withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(text, "UTF-8")
            val url = "https://translated.net|$target"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", MtopClient.MOBILE_UA)
                .build()

            runCatching {
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val body = response.body?.string().orEmpty()
                    val json = JSONObject(body)
                    val translated = json.optJSONObject("responseData")?.optString("translatedText").orEmpty().trim()
                    if (translated.isBlank() || translated.contains("MYMEMORY WARNING", ignoreCase = true)) null else translated
                }
            }.getOrNull()
        }

    private fun parseGoogle(body: String): String? {
        val root = runCatching { JSONArray(body) }.getOrNull() ?: return null
        val segments = root.optJSONArray(0) ?: return null
        val builder = StringBuilder()
        for (index in 0 until segments.length()) {
            val segment = segments.optJSONArray(index) ?: continue
            val piece = segment.optString(0)
            if (piece.isNotBlank()) builder.append(piece)
        }
        return builder.toString().trim().ifBlank { null }
    }

    fun isMostlyChinese(text: String): Boolean {
        val han = text.count { it.code in 0x4E00..0x9FFF }
        return han > 0 && han >= text.count { !it.isWhitespace() } / 2
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
