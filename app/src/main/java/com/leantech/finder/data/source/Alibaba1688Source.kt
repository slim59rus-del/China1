package com.leantech.finder.data.source

import com.leantech.finder.data.net.MtopClient
import com.leantech.finder.data.net.MtopException
import com.leantech.finder.data.prefs.SettingsStore
import com.leantech.finder.domain.model.Marketplace
import com.leantech.finder.domain.model.SearchRequest
import com.leantech.finder.domain.model.SortOption
import com.leantech.finder.domain.model.SourceProblem
import org.json.JSONObject

/**
 * 1688 — оптовая площадка фабрик и поставщиков, поэтому фильтр «только фабрики» здесь
 * наиболее осмыслен: площадка сама помечает продавцов метками 实力商家 / 超级工厂 / 源头工厂,
 * они приходят в тегах карточки и разбираются в JsonCards.
 */
class Alibaba1688Source(
    private val mtop: MtopClient,
    private val settings: SettingsStore,
) : MarketplaceSource {

    override val marketplace = Marketplace.ALIBABA_1688

    override suspend fun search(query: String, request: SearchRequest): SourceResult {
        return try {
            val primaryData = buildPcSearchPayload(query, request)
            val primaryResponse = mtop.call(
                host = marketplace.apiHost,
                api = SEARCH_API,
                version = "1.0",
                data = primaryData,
                proxyBaseUrl = settings.proxyBaseUrl,
            )
            var products = JsonCards.extract(primaryResponse, marketplace, query)

            if (products.isEmpty()) {
                val fallbackData = buildWapSearchPayload(query, request)
                val fallbackResponse = mtop.call(
                    host = marketplace.apiHost,
                    api = WAP_SEARCH_API,
                    version = "1.0",
                    data = fallbackData,
                    proxyBaseUrl = settings.proxyBaseUrl,
                )
                products = JsonCards.extract(fallbackResponse, marketplace, query)
                if (products.isEmpty()) {
                    val retInfo = extractRetCode(primaryResponse) ?: extractRetCode(fallbackResponse)
                    return SourceResult(problem = emptyProblem(query, retInfo))
                }
            }

            SourceResult(products)
        } catch (error: MtopException) {
            SourceResult(
                problem = SourceProblem(
                    marketplace = marketplace,
                    message = error.message ?: "1688 не ответил",
                    needsLogin = true,
                    webFallbackUrl = webSearchUrl(query),
                )
            )
        }
    }

    private fun buildPcSearchPayload(query: String, request: SearchRequest): JSONObject {
        val (sortField, descend) = sortParams(request)
        return JSONObject().apply {
            put("keywords", query)
            put("charset", "utf-8")
            put("beginPage", 1)
            put("pageSize", request.limitPerMarketplace)
            put("n", request.limitPerMarketplace)
            put("sortType", sortField)
            put("descendOrder", descend)
            put("async", "true")
            // Не передаем устаревший server-side filter, т.к. 1688 обнуляет выдачу;
            // фильтрация по фабрикам выполняется локально через Seller.isFactory
            put("filter", "")
        }
    }

    private fun buildWapSearchPayload(query: String, request: SearchRequest): JSONObject {
        val (sortField, descend) = sortParams(request)
        return JSONObject().apply {
            put("keywords", query)
            put("q", query)
            put("page", 1)
            put("beginPage", 1)
            put("pageSize", request.limitPerMarketplace)
            put("sortType", sortField)
            put("descendOrder", descend)
        }
    }

    private fun sortParams(request: SearchRequest): Pair<String, String> = when (request.sort) {
        SortOption.SALES_DESC -> "va_rmdarkgmv" to "true"
        SortOption.PRICE_ASC -> "price" to "false"
        SortOption.PRICE_DESC -> "price" to "true"
        SortOption.RELEVANCE -> "default" to "true"
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
        val detail = if (!retCode.isNullOrBlank()) {
            "1688 вернул пустой список ($retCode)."
        } else {
            "1688 вернул пустой список: чаще всего нужен вход в аккаунт или прохождение проверки в WebView."
        }
        return SourceProblem(
            marketplace = marketplace,
            message = detail,
            needsLogin = true,
            webFallbackUrl = webSearchUrl(query),
        )
    }

    companion object {
        const val SEARCH_API = "mtop.alibaba.pc.newsearch.searchoffers"
        const val WAP_SEARCH_API = "mtop.1688.wap.search.offer.search"
    }
}
