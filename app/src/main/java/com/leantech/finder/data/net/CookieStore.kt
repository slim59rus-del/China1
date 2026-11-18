package com.leantech.finder.data.net

import android.net.Uri
import android.webkit.CookieManager
import com.leantech.finder.data.prefs.SettingsStore

class CookieStore(
    private val settings: SettingsStore,
    private val cookieManager: CookieManager = CookieManager.getInstance(),
) {

    private val knownDomains = listOf(
        "https://taobao.com",
        "https://m.taobao.com",
        "https://s.taobao.com",
        "https://h5api.m.taobao.com",
        "https://login.taobao.com",
        "https://1688.com",
        "https://m.1688.com",
        "https://s.1688.com",
        "https://h5api.m.1688.com",
        "https://login.1688.com",
        "https://goofish.com",
        "https://m.goofish.com",
        "https://h5api.m.goofish.com",
        "https://alipay.com",
        "https://auth.alipay.com",
    )

    init {
        cookieManager.setAcceptCookie(true)
        restoreAllFromBackup()
    }

    fun cookieHeader(url: String): String {
        var current = cookieManager.getCookie(url)
        if (current.isNullOrBlank()) {
            restoreAllFromBackup()
            current = cookieManager.getCookie(url)
        }

        val merged = linkedMapOf<String, String>()
        parseCookiePairs(current, merged)

        val rootUrl = rootDomainUrl(url)
        if (rootUrl != null && rootUrl != url) {
            parseCookiePairs(cookieManager.getCookie(rootUrl), merged)
        }

        return merged.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    private fun parseCookiePairs(raw: String?, target: MutableMap<String, String>) {
        if (raw.isNullOrBlank()) return
        for (part in raw.split(';')) {
            val trimmed = part.trim()
            val eqIdx = trimmed.indexOf('=')
            if (eqIdx > 0) {
                val name = trimmed.substring(0, eqIdx).trim()
                val value = trimmed.substring(eqIdx + 1).trim()
                if (name.isNotEmpty() && !target.containsKey(name)) {
                    target[name] = value
                }
            }
        }
    }

    private fun rootDomainUrl(url: String): String? {
        val host = Uri.parse(url).host?.lowercase() ?: return null
        return when {
            host.endsWith("1688.com") -> "https://1688.com"
            host.endsWith("taobao.com") -> "https://taobao.com"
            host.endsWith("goofish.com") -> "https://goofish.com"
            host.endsWith("alipay.com") -> "https://alipay.com"
            else -> null
        }
    }

    private fun rootCookieDomain(url: String): String? {
        val host = Uri.parse(url).host?.lowercase() ?: return null
        return when {
            host.endsWith("1688.com") -> ".1688.com"
            host.endsWith("taobao.com") -> ".taobao.com"
            host.endsWith("goofish.com") -> ".goofish.com"
            host.endsWith("alipay.com") -> ".alipay.com"
            else -> null
        }
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
            val rootDomain = rootCookieDomain(domain)
            for (part in cookieString.split(';')) {
                val trimmed = part.trim()
                if (trimmed.isNotEmpty()) {
                    cookieManager.setCookie(domain, trimmed)
                    if (rootDomain != null && !trimmed.contains("Domain=", ignoreCase = true)) {
                        cookieManager.setCookie(domain, "$trimmed; Domain=$rootDomain; Path=/")
                    }
                }
            }
        }
        cookieManager.flush()
    }

    fun clearDomains(domains: List<String>) {
        val expanded = LinkedHashSet(domains)
        if (domains.any { it.contains("1688.com") }) {
            expanded.addAll(knownDomains.filter { it.contains("1688.com") })
        }
        if (domains.any { it.contains("taobao.com") }) {
            expanded.addAll(knownDomains.filter { it.contains("taobao.com") })
        }
        if (domains.any { it.contains("goofish.com") }) {
            expanded.addAll(knownDomains.filter { it.contains("goofish.com") })
        }

        settings.removeCookieBackupsFor(expanded.toList())
        for (domain in expanded) {
            val rootDomain = rootCookieDomain(domain)
            val raw = cookieManager.getCookie(domain).orEmpty()
            for (part in raw.split(';')) {
                val cookieName = part.trim().substringBefore('=')
                if (cookieName.isNotEmpty()) {
                    cookieManager.setCookie(domain, "$cookieName=; Max-Age=0")
                    if (rootDomain != null) {
                        cookieManager.setCookie(domain, "$cookieName=; Domain=$rootDomain; Path=/; Max-Age=0")
                    }
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
