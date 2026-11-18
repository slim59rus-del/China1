package com.leantech.finder.data.source

import com.leantech.finder.domain.model.Marketplace
import com.leantech.finder.domain.model.Product
import com.leantech.finder.domain.model.Seller
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

object JsonCards {

    private val TITLE_KEYS = listOf(
        "raw_title", "title", "itemTitle", "item_title", "subject", "offerTitle",
        "itemName", "productTitle",
    )
    private val ID_KEYS = listOf(
        "nid", "itemId", "item_id", "itemid", "itemID", "auctionId", "offerId",
        "offer_id", "targetId", "id",
    )
    private val PRICE_KEYS = listOf(
        "view_price", "price", "priceShow", "priceText", "currentPrice", "reservePrice",
        "soldPrice", "priceInfo", "promotionPrice", "consignPrice", "priceRange",
    )
    private val SALES_KEYS = listOf(
        "view_sales", "sold", "soldCount", "soldQuantity", "sales", "salesCount",
        "tradeNum", "trade_count", "wantCount", "wantNum", "collectCount", "soldCnt",
        "monthSold", "dealCount", "volume",
    )
    private val IMAGE_KEYS = listOf(
        "pic_url", "picUrl", "image", "imageUrl", "pic", "mainPic", "img",
        "coverUrl", "pic_path", "imgUrl", "imagePath", "thumbUrl",
    )
    private val SELLER_NAME_KEYS = listOf(
        "nick", "sellerNick", "shopName", "companyName", "company", "sellerName",
        "loginId", "userNick", "memberId", "nickName", "userName", "seller_nick",
    )
    private val SELLER_ID_KEYS = listOf(
        "userId", "sellerId", "seller_id", "user_id", "memberId", "companyId", "shopId",
    )
    private val SHOP_URL_KEYS = listOf(
        "shopLink", "shopUrl", "shop_url", "sellerUrl", "companyUrl", "storeUrl",
        "homePage", "sellerUrlPC",
    )
    private val LOCATION_KEYS = listOf(
        "item_loc", "itemLoc", "location", "sellerLoc", "city", "province", "area",
        "companyAddress", "sellerLocation",
    )
    private val RATING_KEYS = listOf("sellerRate", "rating", "score", "creditLevel", "shopRate", "star")
    private val YEARS_KEYS = listOf("shopAge", "years", "openYears", "shopYears", "sellerYears")
    private val TAG_KEYS = listOf(
        "icons", "iconList", "tags", "tagList", "labels", "sellerTags", "badges",
        "shopTags", "serviceTags",
    )
    private val URL_KEYS = listOf("itemUrl", "detailUrl", "url", "link", "detail_url")

    private val FACTORY_MARKERS = listOf(
        "工厂", "厂家", "厂商", "源头", "生产", "制造", "制造商", "加工厂", "实业",
        "工贸", "实力商家", "超级工厂", "factory", "manufacturer", "oem", "odm",
        "定制", "批发",
    )

    private val RESELLER_MARKERS = listOf(
        "旗舰店", "专营店", "专卖店", "代购", "转卖", "二手", "闲置", "个人",
    )

    fun extract(root: JSONObject, marketplace: Marketplace, query: String): List<Product> {
        val candidates = ArrayList<JSONObject>()
        walk(root, candidates, depth = 0)

        val seen = HashSet<String>()
        val products = ArrayList<Product>(candidates.size)

        for (node in candidates) {
            val product = runCatching { normalize(node, marketplace, query) }.getOrNull() ?: continue
            if (product.title.isBlank()) continue
            val key = product.id.ifBlank { product.title + product.priceText }
            if (!seen.add(key)) continue
            products.add(product)
        }
        return products
    }

    private fun walk(node: Any?, out: MutableList<JSONObject>, depth: Int) {
        if (depth > 12) return
        when (node) {
            is JSONObject -> {
                if (looksLikeCard(node)) {
                    out.add(node)
                    return
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    walk(node.opt(keys.next()), out, depth + 1)
                }
            }
            is JSONArray -> {
                for (index in 0 until node.length()) {
                    walk(node.opt(index), out, depth + 1)
                }
            }
        }
    }

    private fun looksLikeCard(node: JSONObject): Boolean {
        val title = firstString(node, TITLE_KEYS)
        if (title.isNullOrBlank()) return false
        val hasId = firstString(node, ID_KEYS) != null || firstString(node, URL_KEYS) != null
        val hasPrice = firstString(node, PRICE_KEYS) != null
        val hasSales = firstString(node, SALES_KEYS) != null
        return hasId || hasPrice || hasSales
    }

    private fun normalize(node: JSONObject, marketplace: Marketplace, query: String): Product {
        val id = firstString(node, ID_KEYS).orEmpty()
        val title = cleanHtml(firstString(node, TITLE_KEYS).orEmpty()).trim()
        val salesText = firstString(node, SALES_KEYS)
        val priceText = firstString(node, PRICE_KEYS).orEmpty()
        val sellerName = firstString(node, SELLER_NAME_KEYS).orEmpty()
        val sellerId = firstString(node, SELLER_ID_KEYS)
        val tags = collectTags(node)
        val years = parseCount(firstString(node, YEARS_KEYS))
        val factorySignals = detectFactorySignals(
            title = title,
            sellerName = sellerName,
            tags = tags,
            salesText = salesText,
            marketplace = marketplace,
        )

        return Product(
            id = id,
            marketplace = marketplace,
            title = title,
            priceCny = parsePrice(priceText),
            priceText = priceText.trim(),
            salesCount = parseCount(salesText),
            salesText = salesText,
            itemUrl = itemUrl(marketplace, id, node, query),
            imageUrl = normalizeImageUrl(extractImageUrl(node)),
            seller = Seller(
                id = sellerId?.takeIf { it.isNotBlank() } ?: id.takeIf { it.isNotBlank() },
                name = sellerName,
                shopUrl = shopUrl(marketplace, node, sellerName, sellerId),
                location = firstString(node, LOCATION_KEYS),
                rating = firstString(node, RATING_KEYS),
                yearsOnPlatform = years,
                tags = tags,
                isFactory = factorySignals.isNotEmpty(),
                factorySignals = factorySignals,
            ),
        )
    }
    private fun extractImageUrl(node: JSONObject): String? {
        firstString(node, IMAGE_KEYS)?.let { return it }
        val keys = node.keys()
        while (keys.hasNext()) {
            val child = node.opt(keys.next())
            if (child is JSONObject) {
                firstString(child, IMAGE_KEYS)?.let { return it }
            }
        }
        return null
    }

    private fun normalizeImageUrl(raw: String?): String? {
        var url = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
        if (url.startsWith("//")) {
            url = "https:$url"
        } else if (!url.startsWith("http")) {
            url = "https://alicdn.com{url.trimStart('/')}"
        }
        val webpSuffix = 95.toChar() + ".webp"
        if (url.endsWith(webpSuffix)) {
            url = url.removeSuffix(webpSuffix)
        }
        return url
    }

    private fun detectFactorySignals(
        title: String,
        sellerName: String,
        tags: List<String>,
        salesText: String?,
        marketplace: Marketplace,
    ): List<String> {
        val haystack = (title + " " + sellerName + " " + tags.joinToString(" ") + " " +
            salesText.orEmpty()).lowercase()

        val reseller = RESELLER_MARKERS.filter { haystack.contains(it.lowercase()) }
        val signals = FACTORY_MARKERS.filter { haystack.contains(it.lowercase()) }
        if (reseller.isNotEmpty() && signals.isEmpty()) return emptyList()

        if (signals.isEmpty() && marketplace == Marketplace.ALIBABA_1688) {
            return listOf("оптовое предложение 1688")
        }
        return signals
    }

    private fun itemUrl(marketplace: Marketplace, id: String, node: JSONObject, query: String): String {
        firstString(node, URL_KEYS)?.let { direct ->
            val clean = direct.trim()
            if (clean.startsWith("//")) return "https:$clean"
            if (clean.startsWith("http")) return clean
        }
        val numericId = id.filter { it.isDigit() }
        if (numericId.isNotEmpty()) {
            return when (marketplace) {
                Marketplace.TAOBAO -> "https://taobao.com"
                Marketplace.ALIBABA_1688 -> "https://1688.com"
                Marketplace.XIANYU -> "https://goofish.com"
            }
        }
        return marketplace.searchUrlTemplate.format(encode(query))
    }

    private fun shopUrl(
        marketplace: Marketplace,
        node: JSONObject,
        sellerName: String,
        sellerId: String?,
    ): String? {
        firstString(node, SHOP_URL_KEYS)?.let { direct ->
            val clean = direct.trim()
            if (clean.startsWith("//")) return "https:$clean"
            if (clean.startsWith("http")) return clean
        }
        val cleanSellerId = sellerId?.filter { it.isDigit() }.orEmpty()
        if (cleanSellerId.isNotEmpty()) {
            return when (marketplace) {
                Marketplace.TAOBAO -> "https://shop$://taobao.com"
                Marketplace.ALIBABA_1688 -> "https://1688.com"
                Marketplace.XIANYU -> "https://goofish.com"
            }
        }
        if (sellerName.isNotBlank()) {
            val encodedSeller = encode(sellerName)
            return when (marketplace) {
                Marketplace.TAOBAO -> "https://taobao.com"
                Marketplace.ALIBABA_1688 -> "https://1688.com"
                Marketplace.XIANYU -> "https://goofish.com"
            }
        }
        return null
    }

    private fun firstString(node: JSONObject, keys: List<String>): String? {
        for (key in keys) {
            if (!node.has(key)) continue
            val value = node.opt(key) ?: continue
            when (value) {
                is String -> if (value.isNotBlank() && value != "null") return value
                is Number -> return value.toString()
                is JSONObject -> {
                    val nested = value.optString("text")
                        .ifBlank { value.optString("value") }
                        .ifBlank { value.optString("price") }
                        .ifBlank { value.optString("name") }
                    if (nested.isNotBlank()) return nested
                }
            }
        }
        return null
    }

    private fun collectTags(node: JSONObject): List<String> {
        val result = LinkedHashSet<String>()
        for (key in TAG_KEYS) {
            val raw = node.opt(key) ?: continue
            when (raw) {
                is JSONArray -> {
                    for (i in 0 until raw.length()) {
                        val el = raw.opt(i)
                        when (el) {
                            is String -> if (el.isNotBlank()) result.add(el.trim())
                            is JSONObject -> {
                                val text = el.optString("text")
                                    .ifBlank { el.optString("title") }
                                    .ifBlank { el.optString("name") }
                                    .ifBlank { el.optString("value") }
                                if (text.isNotBlank()) result.add(text.trim())
                            }
                        }
                    }
                }
                is String -> {
                    raw.split(',', ';', '|')
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .forEach { result.add(it) }
                }
            }
        }
        return result.toList()
    }

    private fun parsePrice(raw: String): Double? {
        val match = Regex("""\d+(?:[.,]\d+)?""").find(raw) ?: return null
        return match.value.replace(',', '.').toDoubleOrNull()
    }

    private fun parseCount(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val clean = raw.trim()
        val match = Regex("""(\d+(?:[.,]\d+)?)\s*(万|w|W)?""").find(clean) ?: return null
        val num = match.groupValues.getOrNull(1)?.replace(',', '.')?.toDoubleOrNull() ?: return null
        val suffix = match.groupValues.getOrNull(2).orEmpty()
        val multiplier = if (suffix.isNotEmpty()) 10_000.0 else 1.0
        return (num * multiplier).toInt()
    }

    private fun cleanHtml(text: String): String =
        text.replace(Regex("<[^>]*>"), "").replace("&amp;", "&").replace("&quot;", "\"")

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
