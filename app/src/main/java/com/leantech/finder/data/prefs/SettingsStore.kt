package com.leantech.finder.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.leantech.finder.domain.model.Marketplace
import com.leantech.finder.domain.model.SortOption

class SettingsStore(context: Context) {

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "china\_sourcing\_prefs",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256\_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256\_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256\_GCM,
    )

    var resultLimit: Int
        get() = prefs.getInt(KEY\_LIMIT, 30)
        set(value) = prefs.edit().putInt(KEY\_LIMIT, value.coerceIn(10, 100)).apply()

    var sort: SortOption
        get() = SortOption.entries.firstOrNull { it.name == prefs.getString(KEY\_SORT, null) }
            ?: SortOption.SALES\_DESC
        set(value) = prefs.edit().putString(KEY\_SORT, value.name).apply()

    var factoryOnly: Boolean
        get() = prefs.getBoolean(KEY\_FACTORY, false)
        set(value) = prefs.edit().putBoolean(KEY\_FACTORY, value).apply()

    var selectedSources: Set<Marketplace>
        get() = prefs.getStringSet(KEY\_SOURCES, null)
            ?.mapNotNull { key -> Marketplace.entries.firstOrNull { it.key == key } }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
            ?: setOf(Marketplace.ALIBABA\_1688)
        set(value) = prefs.edit().putStringSet(KEY\_SOURCES, value.map { it.key }.toSet()).apply()

    var lastQuery: String
        get() = prefs.getString(KEY\_LAST\_QUERY, "").orEmpty()
        set(value) = prefs.edit().putString(KEY\_LAST\_QUERY, value).apply()

    var translatorEndpoint: String
        get() = prefs.getString(KEY\_TRANSLATOR, DEFAULT\_TRANSLATOR).orEmpty()
        set(value) = prefs.edit().putString(KEY\_TRANSLATOR, value.trim()).apply()

    var proxyBaseUrl: String
        get() = prefs.getString(KEY\_PROXY, "").orEmpty()
        set(value) = prefs.edit().putString(KEY\_PROXY, value.trim()).apply()

    var translateTitles: Boolean
        get() = prefs.getBoolean(KEY\_TRANSLATE\_TITLES, true)
        set(value) = prefs.edit().putBoolean(KEY\_TRANSLATE\_TITLES, value).apply()

    var savedLogin: String
        get() = prefs.getString(KEY\_SAVED\_LOGIN, "").orEmpty()
        set(value) = prefs.edit().putString(KEY\_SAVED\_LOGIN, value).apply()

    var savedPassword: String
        get() = prefs.getString(KEY\_SAVED\_PASSWORD, "").orEmpty()
        set(value) = prefs.edit().putString(KEY\_SAVED\_PASSWORD, value).apply()

    fun saveCookiesBackup(domain: String, cookieString: String) {
        if (cookieString.isNotBlank()) {
            prefs.edit().putString("$KEY\_COOKIES\_PREFIX$domain", cookieString).apply()
        }
    }

    fun getCookiesBackup(domain: String): String? =
        prefs.getString("$KEY\_COOKIES\_PREFIX$domain", null)

    fun getAllCookieBackups(): Map<String, String> =
        prefs.all.filterKeys { it.startsWith(KEY\_COOKIES\_PREFIX) }
            .mapKeys { it.key.removePrefix(KEY\_COOKIES\_PREFIX) }
            .mapValues { it.value.toString() }

    fun markLoggedIn(marketplace: Marketplace, value: Boolean) =
        prefs.edit().putBoolean(loginKey(marketplace), value).apply()

    fun isMarkedLoggedIn(marketplace: Marketplace): Boolean =
        prefs.getBoolean(loginKey(marketplace), false)

    fun clearSession() {
        val editor = prefs.edit()
            .remove(KEY\_LOGIN\_TAOBAO)
            .remove(KEY\_LOGIN\_1688)
            .remove(KEY\_LOGIN\_XIANYU)
        prefs.all.keys.filter { it.startsWith(KEY\_COOKIES\_PREFIX) }.forEach { editor.remove(it) }
        editor.apply()
    }

    private fun loginKey(marketplace: Marketplace) = when (marketplace) {
        Marketplace.TAOBAO -> KEY\_LOGIN\_TAOBAO
        Marketplace.ALIBABA\_1688 -> KEY\_LOGIN\_1688
        Marketplace.XIANYU -> KEY\_LOGIN\_XIANYU
    }

    companion object {
        const val DEFAULT\_TRANSLATOR = "https://googleapis.com"

        private const val KEY\_LIMIT = "result\_limit"
        private const val KEY\_SORT = "sort"
        private const val KEY\_FACTORY = "factory\_only"
        private const val KEY\_SOURCES = "sources"
        private const val KEY\_LAST\_QUERY = "last\_query"
        private const val KEY\_TRANSLATOR = "translator\_endpoint"
        private const val KEY\_PROXY = "proxy\_base\_url"
        private const val KEY\_TRANSLATE\_TITLES = "translate\_titles"
        private const val KEY\_LOGIN\_TAOBAO = "login\_taobao"
        private const val KEY\_LOGIN\_1688 = "login\_1688"
        private const val KEY\_LOGIN\_XIANYU = "login\_xianyu"
        private const val KEY\_SAVED\_LOGIN = "saved\_account\_login"
        private const val KEY\_SAVED\_PASSWORD = "saved\_account\_password"
        private const val KEY\_COOKIES\_PREFIX = "cookies\_backup\_"
    }
}
