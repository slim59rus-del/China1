package com.leantech.finder.data.source

import android.content.Context
import com.leantech.finder.data.net.MtopClient
import com.leantech.finder.data.net.MtopException
import com.leantech.finder.data.net.WebViewSearchFetcher
import com.leantech.finder.data.prefs.SettingsStore
import com.leantech.finder.di.ServiceLocator
import com.leantech.finder.domain.model.Marketplace
import com.leantech.finder.domain.model.SearchRequest
import com.leantech.finder.domain.model.SortOption
import com.leantech.finder.domain.model.SourceProblem
import org.json.JSONObject
import java.net.URLEncoder

/**
 * 闲鱼 (Idle Fish / Goofish) — площадка частных объявлений и заводских излишков.
 */
class XianyuSource(
    private val mtop: MtopClient,
    private val settings: SettingsStore,
    private val context: Context? = null,
) : MarketplaceSource {

    override val marketplace = Marketplace.XIANYU

    override suspend fun search(query: String, request: SearchRequest): SourceResult {
        // 1. Основной путь без прокси: скрытый WebView с авторизованными куками Goofish (обходит RGV587_ERROR)
        val ctx = context ?: runCatching { ServiceLocator.appContext }.getOrNull()
        if (ctx != null && settings.proxyBaseUrl.isBlank()) {
            val webJson = WebViewSearchFetcher.fetchJsonOrDom(
                context = ctx,
                searchUrl = buildGoofishSearchUrl(query),
            )
            if (webJson != null) {
                val webProducts = JsonCards.extract(webJson, marketplace, query)
                    .take(request.limitPerMarketplace)
                if (webProducts.isNotEmpty()) {
                    return SourceResult(webProducts)
                }
            }
        }

        // 2. Резервный путь: MTOP API
        val data = JSONObject().apply {
            put("pageNumber", 1)
            put("keyword", query)
            put("fromFilter", false)
            put("rowsPerPage", request.limitPerMarketplace)
            put("sortValue", sortValue(request))
            put("sortField", sortField(request))
            put("customDistance", "")
            put("gps", "")
            put("propValueStr", "{}")
            put("customGps", "")
            put("searchReqFromPage", "pcSearch")
            put("extraFilterValue", "{}")
            put("userPositionJson", "{}")
        }

        return try {
            val response = mtop.call(
                host = marketplace.apiHost,
                api = SEARCH_API,
                version = "1.0",
                data = data,
                proxyBaseUrl = settings.proxyBaseUrl,
                extraQuery = mapOf("appKey" to XIANYU_APP_KEY),
            )
            val products = JsonCards.extract(response, marketplace, query)
            if (products.isEmpty()) {
                val retCode = extractRetCode(response)
                SourceResult(problem = emptyProblem(query, retCode))
            } else {
                SourceResult(products.take(request.limitPerMarketplace))
            }
        } catch (error: MtopException) {
            SourceResult(
                problem = SourceProblem(
                    marketplace = marketplace,
                    message = error.message ?: "闲鱼 не ответил",
                    needsLogin = true,
                    webFallbackUrl = webSearchUrl(query),
                )
            )
        }
    }

    private fun buildGoofishSearchUrl(query: String): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        return "https://goofish.com" + encoded
    }

    private fun sortField(request: SearchRequest): String = when (request.sort) {
        SortOption.PRICE_ASC, SortOption.PRICE_DESC -> "price"
        else -> ""
    }

    private fun sortValue(request: SearchRequest): String = when (request.sort) {
        SortOption.PRICE_ASC -> "asc"
        SortOption.PRICE_DESC -> "desc"
        else -> ""
    }

    private fun extractRetCode(response: JSONObject): String? {
        val retArray = response.optJSONArray("ret") ?: return null
        val messages = buildList {
            for (i in 0 until retArray.length()) {
                val item = retArray.optString(i)
                if (item.isNotBlank() && !item.startsWith("SUCCESS")) {
                    add(item)
                }
            }
        }
        return messages.takeIf { it.isNotEmpty() }?.joinToString("; ")
    }

    private fun emptyProblem(query: String, retCode: String? = null): SourceProblem {
        val msg = if (!retCode.isNullOrBlank()) {
            "闲鱼 вернул пустой список (" + retCode + ")"
        } else {
            "闲鱼 вернул пустой список: обычно нужен вход в аккаунт."
        }
        return SourceProblem(
            marketplace = marketplace,
            message = msg,
            needsLogin = true,
            webFallbackUrl = webSearchUrl(query),
        )
    }

    companion object {
        const val SEARCH_API = "mtop.taobao.idlemtopsearch.search"
        const val XIANYU_APP_KEY = "34839810"
    }
}
