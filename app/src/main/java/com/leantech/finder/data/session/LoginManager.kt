package com.leantech.finder.data.session

import com.leantech.finder.data.net.CookieStore
import com.leantech.finder.data.prefs.SettingsStore
import com.leantech.finder.domain.model.Marketplace

class LoginManager(
    private val cookies: CookieStore,
    private val settings: SettingsStore,
) {

    fun loginUrl(marketplace: Marketplace): String = marketplace.loginUrl

    /\*\* Проверка по признакам живой сессии — конкретные имена cookie площадки. \*/
    fun isLoggedIn(marketplace: Marketplace): Boolean {
        val host = marketplace.apiHost
        val alive = when (marketplace) {
            Marketplace.TAOBAO -> cookies.has(host, "cookie2") && cookies.has(host, "\_m\_h5\_tk")
            Marketplace.ALIBABA\_1688 -> cookies.has(host, "cookie2") || cookies.has(host, "tracknick")
            Marketplace.XIANYU -> cookies.has(host, "cookie2") || cookies.has(host, "unb")
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

    fun logout() {
        cookies.clear()
        settings.clearSession()
    }
}
