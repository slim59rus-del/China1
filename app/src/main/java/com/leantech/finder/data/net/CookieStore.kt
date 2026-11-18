package com.leantech.finder.data.net

import android.webkit.CookieManager
import com.leantech.finder.data.prefs.SettingsStore

class CookieStore(
    private val settings: SettingsStore,
    private val cookieManager: CookieManager = CookieManager.getInstance(),
) {

    private val knownDomains = listOf(
        "https://taobao.com",
        "https://taobao.com",
        "https://1688.com",
        "https://1688.com",
        "https://goofish.com",
        "https://goofish.com",
        "https://alipay.com",
    )

    init {
        cookieManager.setAcceptCookie(true)
        restoreAllFromBackup()
    }

    fun cookieHeader(url: String): String {
        val current = cookieManager.getCookie(url)
        if (!current.isNullOrBlank()) return current

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
        for (domain in knownDomains) {
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
        for (entry in backups) {
            val domain = entry.key
            val cookieString = entry.value
            for (part in cookieString.split(';')) {
                val trimmed = part.trim()
                if (trimmed.isNotEmpty()) {
                    cookieManager.setCookie(domain, trimmed)
                }
            }
        }
        cookieManager.flush()
    }

    fun clearDomains(domains: List<String>) {
        settings.removeCookieBackupsFor(domains)
        for (domain in domains) {
            val raw = cookieManager.getCookie(domain).orEmpty()
            for (part in raw.split(';')) {
                val cookieName = part.trim().substringBefore('=')
                if (cookieName.isNotEmpty()) {
                    cookieManager.setCookie(domain, "$cookieName=; Max-Age=0")
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
}
