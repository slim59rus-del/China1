package com.leantech.finder.data.net

import android.webkit.CookieManager
import com.leantech.finder.data.prefs.SettingsStore

class CookieStore(
    private val settings: SettingsStore,
    private val cookieManager: CookieManager = CookieManager.getInstance(),
) {

    init {
        cookieManager.setAcceptCookie(true)
        restoreAllFromBackup()
    }

    fun cookieHeader(url: String): String {
        val current = cookieManager.getCookie(url)
        if (!current.isNullOrBlank()) return current

        // Если системный менеджер пуст после перезапуска, пробуем восстановить из резервной копии
        restoreAllFromBackup()
        return cookieManager.getCookie(url).orEmpty()
    }

    fun read(url: String, name: String): String? = cookieHeader(url)
        .split(';')
        .asSequence()
        .map { it.trim() }
        .firstOrNull { it.startsWith("$name=") }
        ?.substringAfter('=')

    fun has(url: String, name: String): Boolean = read(url, name) != null

    fun backupDomain(url: String) {
        cookieManager.flush()
        val raw = cookieManager.getCookie(url)
        if (!raw.isNullOrBlank()) {
            settings.saveCookiesBackup(url, raw)
        }
    }

    fun backupKnownDomains() {
        cookieManager.flush()
        KNOWN\_DOMAINS.forEach { domain ->
            val raw = cookieManager.getCookie(domain)
            if (!raw.isNullOrBlank()) {
                settings.saveCookiesBackup(domain, raw)
            }
        }
    }

    fun restoreAllFromBackup() {
        val backups = settings.getAllCookieBackups()
        if (backups.isEmpty()) return

        cookieManager.setAcceptCookie(true)
        backups.forEach { (domain, cookieString) ->
            cookieString.split(';').forEach { part ->
                val trimmed = part.trim()
                if (trimmed.isNotEmpty()) {
                    cookieManager.setCookie(domain, trimmed)
                }
            }
        }
        cookieManager.flush()
    }

    fun clear() {
        cookieManager.removeAllCookies(null)
        cookieManager.flush()
    }

    fun flush() {
        cookieManager.flush()
        backupKnownDomains()
    }

    companion object {
        private val KNOWN\_DOMAINS = listOf(
            "https://taobao.com",
            "https://taobao.com",
            "https://1688.com",
            "https://1688.com",
            "https://goofish.com",
            "https://goofish.com",
            "https://alipay.com",
        )
    }
}
