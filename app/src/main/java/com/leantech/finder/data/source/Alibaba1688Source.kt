package com.leantech.finder.data.source

import android.content.Context
import android.net.Uri
import com.leantech.finder.data.net.CookieStore
import com.leantech.finder.data.net.MtopClient
import com.leantech.finder.data.net.WebViewSearchFetcher
import com.leantech.finder.data.prefs.SettingsStore
import com.leantech.finder.domain.model.Marketplace
import com.leantech.finder.domain.model.SearchRequest
import com.leantech.finder.domain.model.SortOption
import com.leantech.finder.domain.model.SourceProblem
import java.net.URLEncoder

class Alibaba1688Source(
    private val mtop: MtopClient,
    private val settings: SettingsStore,
    private val cookies: CookieStore? = null,
    private val context: Context? = null,
) : MarketplaceSource {

    override val marketplace = Marketplace.ALIBABA_1688

    override suspend fun search(query: String, request: SearchRequest): SourceResult {
        val airSearchUrl = buildAirMobileSearchUrl(query, request)
        val desktopSearchUrl = buildDesktopSearchUrl(query, request)
        var diagInfo: String? = null

        if (context != null) {
            for (targetUrl in listOf(airSearchUrl, desktopSearchUrl)) {
                val webJson = WebViewSearchFetcher.fetchJsonOrDom(
                    context = context,
                    searchUrl = targetUrl,
                    timeoutMs = 13_000L,
                )
                if (webJson != null) {
                    val products = JsonCards.extract(webJson, marketplace, query)
                        .take(request.limitPerMarketplace)
                    if (products.isNotEmpty()) {
                        return SourceResult(products)
                    }
                    val dbgUrl = webJson.optString("__debugUrl")
                    val dbgTitle = webJson.optString("__debugTitle")
                    val dbgText = webJson.optString("__debugText")
                    val host = runCatching { Uri.parse(dbgUrl).host }.getOrNull().orEmpty()
                    val summary = listOf(host, dbgTitle, dbgText)
                        .filter { it.isNotBlank() }
                        .joinToString(" | ")
                    if (summary.isNotBlank()) {
                        diagInfo = summary
                    }
                }
            }
        }

        return SourceResult(
            problem = SourceProblem(
                marketplace = marketplace,
                message = if (!diagInfo.isNullOrBlank()) {
                    "1688: " + diagInfo
                } else {
                    "1688 вернул пустой список: попробуйте обновить вход или открыть поиск на сайте."
                },
                needsLogin = true,
                webFallbackUrl = airSearchUrl,
            )
        )
    }

    private fun buildAirMobileSearchUrl(query: String, request: SearchRequest): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val sortParam = when (request.sort) {
            SortOption.SALES_DESC -> "&sortType=va_rmdarkgmv&descendOrder=true"
            SortOption.PRICE_ASC -> "&sortType=price&descendOrder=false"
            SortOption.PRICE_DESC -> "&sortType=price&descendOrder=true"
            SortOption.RELEVANCE -> ""
        }
        return "https://1688.com" + encoded + sortParam
    }

    private fun buildDesktopSearchUrl(query: String, request: SearchRequest): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val sortParam = when (request.sort) {
            SortOption.SALES_DESC -> "&sortType=va_rmdarkgmv&descendOrder=true"
            SortOption.PRICE_ASC -> "&sortType=price&descendOrder=false"
            SortOption.PRICE_DESC -> "&sortType=price&descendOrder=true"
            SortOption.RELEVANCE -> ""
        }
        return "https://1688.com" + encoded + sortParam
    }
}
