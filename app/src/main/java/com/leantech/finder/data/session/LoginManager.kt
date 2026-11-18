package com.leantech.finder.data.session

import com.leantech.finder.data.net.CookieStore
import com.leantech.finder.data.prefs.SettingsStore
import com.leantech.finder.domain.model.Marketplace

class LoginManager(
    private val cookies: CookieStore,
    private val settings: SettingsStore,
) {

    fun loginUrl(marketplace: Marketplace): String = marketplace.loginUrl

    fun isLoggedIn(marketplace: Marketplace): Boolean {
        val host = marketplace.apiHost
        val u = 95.toChar()
        val mh5tk = "${u}m${u}h5${u}tk"

        val alive = when (marketplace) {
            Marketplace.TAOBAO -> cookies.has(host, "cookie2") && cookies.has(host, mh5tk)
            Marketplace.XIANYU -> cookies.has(host, "cookie2") || cookies.has(host, "unb")
            else -> cookies.has(host, "cookie2") || cookies.has(host, "tracknick")
        }
        if (alive) {
            settings.markLoggedIn(marketplace, true)
            cookies.backupDomain(host)
            cookies.backupKnownDomains()
        }
        return alive
    }

    fun refresh(): Map<Marketplace, Boolean> {
        cookies.flush()
        return Marketplace.entries.associateWith { isLoggedIn(it) }
    }

    fun logout(marketplace: Marketplace) {
        val domains = when (marketplace) {
            Marketplace.TAOBAO -> listOf("https://taobao.com", "https://taobao.com", "https://taobao.com")
            Marketplace.XIANYU -> listOf("https://goofish.com", "https://goofish.com")
            else -> listOf("https://1688.com", "https://1688.com", "https://1688.com")
        }
        cookies.clearDomains(domains)
        settings.markLoggedIn(marketplace, false)
    }

    fun logout() {
        cookies.clear()
        settings.clearSession()
    }
}
