package com.leantech.finder.data.translate

class GlossaryTranslator : Translator {

    override suspend fun toChinese(text: String): String? {
        var normalized = text.lowercase().trim()
        if (normalized.isEmpty()) return null

        for ((phrase, zh) in PHRASES) {
            if (normalized.contains(phrase)) {
                normalized = normalized.replace(phrase, " $zh ")
            }
        }

        val words = normalized.split(Regex("""[^\p{L}\p{N}]+""")).filter { it.isNotBlank() }
        if (words.isEmpty()) return null

        val result = mutableListOf<String>()
        for (word in words) {
            if (word in STOP_WORDS) continue
            val dictWord = DICTIONARY[word]
            if (dictWord != null) {
                result.add(dictWord)
            } else if (word.any { it.code in 0x4E00..0x9FFF } || word.all { it.isDigit() || it in 'a'..'z' }) {
                result.add(word)
            }
        }
        return result.joinToString(" ").trim().ifBlank { null }
    }

    override suspend fun toRussian(text: String): String? = null

    private companion object {
        val STOP_WORDS = setOf("для", "на", "с", "со", "по", "в", "во", "из", "от", "к", "и", "или", "под")

        val PHRASES = listOf(
            "датчики холла" to "霍尔传感器",
            "датчик холла" to "霍尔传感器",
            "мотор колесо" to "轮毂电机",
            "мотор-колесо" to "轮毂电机",
            "ручка газа" to "调速转把",
            "точечная сварка" to "点焊机",
            "паяльная станция" to "焊台",
            "блок питания" to "电源适配器",
            "повышающий преобразователь" to "升压模块",
            "понижающий преобразователь" to "降压模块",
        )

        val DICTIONARY = mapOf(
            "электровелосипед" to "电动车", "электровелосипеда" to "电动车",
            "электросамокат" to "电动滑板车", "электросамоката" to "电动滑板车",
            "велосипед" to "自行车", "самокат" to "滑板车",
            "контроллер" to "控制器", "дисплей" to "仪表", "экран" to "屏幕",
            "батарея" to "电池", "аккумулятор" to "锂电池", "ячейки" to "电芯",
            "плата" to "保护板", "бмс" to "BMS保护板", "bms" to "BMS保护板",
            "зарядное" to "充电器", "зарядка" to "充电器",
            "мотор" to "电机", "двигатель" to "电机", "редуктор" to "齿轮",
            "тормоз" to "刹车", "колодки" to "刹车片", "шина" to "轮胎", "покрышка" to "外胎", "камера" to "内胎",
            "фара" to "大灯", "фонарь" to "车灯", "поворотники" to "转向灯",
            "провод" to "电线", "кабель" to "数据线", "разъем" to "插头", "коннектор" to "接插件",
            "датчик" to "传感器", "холла" to "霍尔", "реле" to "继电器", "предохранитель" to "保险丝",
            "транзистор" to "场效应管", "мосфет" to "MOS管", "конденсатор" to "电容", "резистор" to "电阻",
            "телефон" to "手机", "смартфон" to "智能手机", "чехол" to "手机壳", "наушники" to "耳机",
            "часы" to "手表", "инструмент" to "工具", "дрель" to "电钻", "шуруповерт" to "电动螺丝刀",
            "запчасть" to "配件", "запчасти" to "配件", "деталь" to "零件",
            "опт" to "批发", "фабрика" to "工厂", "завод" to "工厂", "производитель" to "厂家",
        )
    }
}
