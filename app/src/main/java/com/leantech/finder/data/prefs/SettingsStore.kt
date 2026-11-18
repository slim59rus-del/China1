package com.leantech.finder.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.leantech.finder.domain.model.Marketplace
import com.leantech.finder.domain.model.SortOption

class SettingsStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "china_sourcing_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var resultLimit: Int
        get() = prefs.getInt("result_limit", 30)
        set(value) = prefs.edit().putInt("result_limit", value.coerceIn(10, 100)).apply()

    var sort: SortOption
        get() = SortOption.entries.firstOrNull { it.name == prefs.getString("sort", null) }
            ?: SortOption.SALES_DESC
        set(value) = prefs.edit().putString("sort", value.name).apply()

    var factoryOnly: Boolean
        get() = prefs.getBoolean("factory_only", false)
        set(value) = prefs.edit().putBoolean("factory_only", value).apply()

    var selectedSources: Set<Marketplace>
        get() = prefs.getStringSet("sources", null)
            ?.mapNotNull { key -> Marketplace.entries.firstOrNull { it.key == key } }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
            ?: setOf(Marketplace.ALIBABA_1688)
        set(value) = prefs.edit().putStringSet("sources", value.map { it.key }.toSet()).apply()

    var lastQuery: String
        get() = prefs.getString("last_query", "").orEmpty()
        set(value) = prefs.edit().putString("last_query", value).apply()

    var translatorEndpoint: String
        get() = prefs.getString("translator_endpoint", DEFAULT_TRANSLATOR).orEmpty()
        set(value) = prefs.edit().putString("translator_endpoint", value.trim()).apply()

    var proxyBaseUrl: String
        get() = prefs.getString("proxy_base_url", "").orEmpty()
        set(value) = prefs.edit().putString("proxy_base_url", value.trim()).apply()

    var translateTitles: Boolean
        get() = prefs.getBoolean("translate_titles", true)
        set(value) = prefs.edit().putBoolean("translate_titles", value).apply()

    var savedLogin: String
        get() = prefs.getString("saved_account_login", "").orEmpty()
        set(value) = prefs.edit().putString("saved_account_login", value).apply()

    var savedPassword: String
        get() = prefs.getString("saved_account_password", "").orEmpty()
        set(value) = prefs.edit().putString("saved_account_password", value).apply()

    fun saveCookiesBackup(domain: String, cookieString: String) {
        if (cookieString.isNotBlank()) {
            prefs.edit().putString("cookies_backup_$domain", cookieString).apply()
        }
    }

    fun getCookiesBackup(domain: String): String? =
        prefs.getString("cookies_backup_$domain", null)

    fun getAllCookieBackups(): Map<String, String> =
        prefs.all.filterKeys { it.startsWith("cookies_backup_") }
            .mapKeys { it.key.removePrefix("cookies_backup_") }
            .mapValues { it.value.toString() }

    fun markLoggedIn(marketplace: Marketplace, value: Boolean) =
        prefs.edit().putBoolean(loginKey(marketplace), value).apply()

    fun isMarkedLoggedIn(marketplace: Marketplace): Boolean =
        prefs.getBoolean(loginKey(marketplace), false)

    fun clearSession() {
        val editor = prefs.edit()
            .remove("login_taobao")
            .remove("login_1688")
            .remove("login_xianyu")
        prefs.all.keys.filter { it.startsWith("cookies_backup_") }.forEach { editor.remove(it) }
        editor.apply()
    }

    private fun loginKey(marketplace: Marketplace) = when (marketplace) {
        Marketplace.TAOBAO -> "login_taobao"
        Marketplace.ALIBABA_1688 -> "login_1688"
        Marketplace.XIANYU -> "login_xianyu"
    }

    companion object {
        const val DEFAULT_TRANSLATOR = "https://googleapis.com"
    }
}
