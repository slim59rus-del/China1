package com.leantech.finder.data.source

import com.leantech.finder.data.net.CookieStore
import com.leantech.finder.data.net.MtopClient
import com.leantech.finder.data.net.MtopException
import com.leantech.finder.data.prefs.SettingsStore
import com.leantech.finder.di.ServiceLocator
import com.leantech.finder.domain.model.Marketplace
import com.leantech.finder.domain.model.Product
import com.leantech.finder.domain.model.SearchRequest
import com.leantech.finder.domain.model.SortOption
import com.leantech.finder.domain.model.SourceProblem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class Alibaba1688Source(
    private val mtop: MtopClient,
    private val settings: SettingsStore,
    private val cookies: CookieStore? = null,
) : MarketplaceSource {

    override val marketplace = Marketplace.ALIBABA_1688

    private val webClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private fun resolveCookies(): CookieStore = cookies ?: ServiceLocator.cookies

    override suspend fun search(query: String, request: SearchRequest): SourceResult {
        var lastError: String? = null

        // 1. Актуальные мобильные MTOP API 1688
        for (apiName in MOBILE_SEARCH_APIS) {
            try {
                val response = mtop.call(
                    host = marketplace.apiHost,
                    api = apiName,
                    version = "1.0",
                    data = buildMobilePayload(query, request),
                    proxyBaseUrl = settings.proxyBaseUrl,
                )
                val products = JsonCards.extract(response, marketplace, query)
                if (products.isNotEmpty()) {
                    return SourceResult(products.take(request.limitPerMarketplace))
                }
                val ret = extractRetCode(response)
                if (!ret.isNullOrBlank() && !ret.contains("API_NOT_FOUNDED")) {
                    lastError = ret
                }
            } catch (e: MtopException) {
                lastError = e.message
            }
        }

        // 2. Прямой JSON-эндпоинт ://1688.com с авторизованными куками
        val jsonServiceProducts = fetchFromSearchService(query, request)
        if (jsonServiceProducts.isNotEmpty()) {
            return SourceResult(jsonServiceProducts)
        }

        // 3. Мобильная страница поиска ://1688.com (извлечение window.__INIT_DATA)
        val htmlProducts = fetchFromMobileSearchPage(query, request)
        if (htmlProducts.isNotEmpty()) {
            return SourceResult(htmlProducts)
        }

        val errorMsg = if (!lastError.isNullOrBlank()) {
            "1688 вернул пустой список (" + lastError + ")"
        } else {
            "1688 вернул пустой список: попробуйте обновить вход или открыть поиск на сайте."
        }

        return SourceResult(
            problem = SourceProblem(
                marketplace = marketplace,
                message = errorMsg,
                needsLogin = true,
                webFallbackUrl = webSearchUrl(query),
            )
        )
    }

    private fun buildMobilePayload(query: String, request: SearchRequest): JSONObject {
        val (sortField, descend) = sortParams(request)
        return JSONObject().apply {
            put("keywords", query)
            put("q", query)
            put("beginPage", 1)
            put("page", 1)
            put("pageSize", request.limitPerMarketplace)
            put("sortType", sortField)
            put("descendOrder", descend)
            put("charset", "utf-8")
        }
    }
    private suspend fun fetchFromSearchService(
        query: String,
        request: SearchRequest,
    ): List<Product> = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val sortSuffix = when (request.sort) {
                SortOption.SALES_DESC -> "&sortType=va_rmdarkgmv&descendOrder=true"
                SortOption.PRICE_ASC -> "&sortType=price&descendOrder=false"
                SortOption.PRICE_DESC -> "&sortType=price&descendOrder=true"
                SortOption.RELEVANCE -> ""
            }
            val url = "https://1688.com" +
                "?beginPage=1&pageSize=" + request.limitPerMarketplace +
                "&keywords=" + encoded + sortSuffix

            val cookieStr = resolveCookies().cookieHeader(url)
            val reqBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", MtopClient.MOBILE_UA)
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .header("Referer", "https://1688.com")

            if (cookieStr.isNotBlank()) {
                reqBuilder.header("Cookie", cookieStr)
            }

            webClient.newCall(reqBuilder.build()).execute().use { resp ->
                val body = resp.body?.string()?.trim().orEmpty()
                if (body.isBlank() || !body.startsWith("{")) return@use emptyList()
                val json = JSONObject(body)
                JsonCards.extract(json, marketplace, query).take(request.limitPerMarketplace)
            }
        }.getOrDefault(emptyList())
    }

    private suspend fun fetchFromMobileSearchPage(
        query: String,
        request: SearchRequest,
    ): List<Product> = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val sortParam = when (request.sort) {
                SortOption.SALES_DESC -> "&sortType=va_rmdarkgmv&descendOrder=true"
                SortOption.PRICE_ASC -> "&sortType=price&descendOrder=false"
                SortOption.PRICE_DESC -> "&sortType=price&descendOrder=true"
                SortOption.RELEVANCE -> ""
            }
            val url = "https://1688.comoffer_search/-6D7033.html?keywords=" + encoded + sortParam
            val cookieStr = resolveCookies().cookieHeader(url)

            val reqBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", MtopClient.MOBILE_UA)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .header("Referer", "https://1688.com")

            if (cookieStr.isNotBlank()) {
                reqBuilder.header("Cookie", cookieStr)
            }

            webClient.newCall(reqBuilder.build()).execute().use { resp ->
                val html = resp.body?.string().orEmpty()
                if (html.isBlank()) return@use emptyList()

                val initJson = extractInitDataJson(html) ?: return@use emptyList()
                JsonCards.extract(initJson, marketplace, query).take(request.limitPerMarketplace)
            }
        }.getOrDefault(emptyList())
    }

    private fun extractInitDataJson(html: String): JSONObject? {
        val markers = listOf("window.__INIT_DATA=", "window.__INIT_DATA =")
        for (marker in markers) {
            val start = html.indexOf(marker)
            if (start == -1) continue
            val jsonStart = html.indexOf('{', start + marker.length)
            if (jsonStart == -1) continue

            var depth = 0
            var inString = false
            var escape = false
            for (i in jsonStart until html.length) {
                val c = html[i]
                if (escape) {
                    escape = false
                    continue
                }
                if (c == '\\' && inString) {
                    escape = true
                    continue
                }
                if (c == '"') {
                    inString = !inString
                    continue
                }
                if (!inString) {
                    if (c == '{') depth++
                    else if (c == '}') {
                        depth--
                        if (depth == 0) {
                            val rawJson = html.substring(jsonStart, i + 1)
                            return runCatching { JSONObject(rawJson) }.getOrNull()
                        }
                    }
                }
            }
        }
        return null
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

    companion object {
        val MOBILE_SEARCH_APIS = listOf(
            "mtop.1688.mobile.search.offer.search",
            "mtop.alibaba.wireless.search.offer.search",
        )
    }
}
