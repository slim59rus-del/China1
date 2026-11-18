package com.leantech.finder.domain.seller

import com.leantech.finder.data.translate.Translator
import com.leantech.finder.domain.model.Marketplace
import com.leantech.finder.domain.model.Product
import com.leantech.finder.domain.model.Seller

class SellerSummarizer(
    private val translator: Translator,
    private val translateFields: Boolean = true,
) {

    suspend fun describe(product: Product): String {
        val seller = product.seller
        val lines = mutableListOf<String>()

        if (seller.name.isNotBlank()) {
            val translatedName = if (translateFields) runCatching { translator.toRussian(seller.name) }.getOrNull() else null
            lines += if (!translatedName.isNullOrBlank() && translatedName != seller.name) {
                "Продавец: $translatedName (${seller.name})"
            } else {
                "Продавец: ${seller.name}"
            }
        } else {
            lines += "Продавец: имя в карточке не указано"
        }

        seller.location?.takeIf { it.isNotBlank() }?.let { raw ->
            val translated = if (translateFields) runCatching { translator.toRussian(raw) }.getOrNull() else null
            lines += if (translated.isNullOrBlank()) "Регион: $raw" else "Регион: $translated ($raw)"
        }

        seller.yearsOnPlatform?.let { years ->
            if (years in 1..60) lines += "Стаж на площадке: $years г."
        }
        seller.rating?.takeIf { it.isNotBlank() }?.let { lines += "Рейтинг: $it" }

        product.salesText?.takeIf { it.isNotBlank() }?.let { sales ->
            val ruSales = if (translateFields) runCatching { translator.toRussian(sales) }.getOrNull() else null
            val formatted = if (!ruSales.isNullOrBlank() && ruSales != sales) "$ruSales ($sales)" else sales
            lines += if (product.marketplace == Marketplace.XIANYU) {
                "Спрос: $formatted (хотят купить)"
            } else {
                "Продажи: $formatted"
            }
        }

        if (seller.isFactory) {
            val translatedSignals = seller.factorySignals.map { sig ->
                val ru = if (translateFields) runCatching { translator.toRussian(sig) }.getOrNull() else null
                if (!ru.isNullOrBlank() && ru != sig) "$ru ($sig)" else sig
            }
            lines += "Тип: фабрика / производитель (${translatedSignals.joinToString(", ")})"
        } else {
            lines += "Тип: магазин или посредник (прямых меток фабрики нет)"
        }

        if (seller.tags.isNotEmpty()) {
            val tags = seller.tags.take(6).map { tag ->
                val ru = if (translateFields) runCatching { translator.toRussian(tag) }.getOrNull() else null
                if (ru.isNullOrBlank()) tag else ru
            }
            lines += "Метки: " + tags.joinToString(", ")
        }

        return lines.joinToString("\n")
    }

    suspend fun describeSeller(seller: Seller, marketplace: Marketplace = Marketplace.ALIBABA_1688): String =
        describe(
            Product(
                id = seller.id.orEmpty(),
                marketplace = marketplace,
                title = "",
                itemUrl = "",
                seller = seller,
            )
        )
}
