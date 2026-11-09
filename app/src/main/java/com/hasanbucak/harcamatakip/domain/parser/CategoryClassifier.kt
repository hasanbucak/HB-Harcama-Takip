package com.hasanbucak.harcamatakip.domain.parser

object CategoryClassifier {

    const val MARKET = "Market"
    const val FOOD_CAFE = "Yemek & Kafe"
    const val FUEL_TRANSPORT = "Akaryakıt & Ulaşım"
    const val ONLINE_SHOPPING = "Online Alışveriş"
    const val BILLS_SUBSCRIPTION = "Fatura & Abonelik"
    const val CLOTHING = "Giyim & Mağaza"
    const val TECH = "Teknoloji"
    const val HEALTH = "Sağlık & Eczane"
    const val ENTERTAINMENT = "Eğlence & Dijital"
    const val OTHER = "Diğer"

    val ALL_CATEGORIES: List<String> = listOf(
        MARKET,
        FOOD_CAFE,
        FUEL_TRANSPORT,
        ONLINE_SHOPPING,
        BILLS_SUBSCRIPTION,
        CLOTHING,
        TECH,
        HEALTH,
        ENTERTAINMENT,
        OTHER
    )

    private val rawKeywordRules: List<Pair<List<String>, String>> = listOf(
        listOf(
            "MIGROS", "BIM", "BIM BIRLESIK", "A101", "SOK MARKET", "SOK",
            "CARREFOUR", "CARREFOURSA", "MACROCENTER", "FILE MARKET", "GETIR",
            "ISTEGELSIN", "METRO GROSS", "CAGRI MARKET", "MOPAS", "ONUR MARKET", "HAKMAR"
        ) to MARKET,
        listOf(
            "YEMEKSEPETI", "TRENDYOL YEMEK", "STARBUCKS", "KAHVE DUNYASI", "ESPRESSOLAB",
            "MCDONALD", "MCDONALDS", "BURGER KING", "POPEYES", "DOMINO", "DOMINOS", "KFC",
            "TAVUK DUNYASI", "RESTORAN", "RESTAURANT", "CAFE", "KAFE", "PASTANESI",
            "FIRIN", "KEBAP", "DONER"
        ) to FOOD_CAFE,
        listOf(
            "OPET", "SHELL", "PETROL OFISI", "BP PETROL", "BP", "TOTAL ENERGIES", "AYTEMIZ",
            "TURKIYE PETROLLERI", "OTOPARK", "ISPARK", "HGS", "OGS", "ISTANBULKART",
            "BELBIM", "TAKSI", "BITAKSI", "UBER", "MARTI", "THY", "PEGASUS", "AJET", "TCDD"
        ) to FUEL_TRANSPORT,
        listOf(
            "TRENDYOL", "HEPSIBURADA", "AMAZON", "N11", "PAZARAMA", "CICEKSEPETI",
            "SAHIBINDEN", "DOLAP", "TEMU", "ALIEXPRESS", "IYZICO", "PAYTR", "PARAM"
        ) to ONLINE_SHOPPING,
        listOf(
            "TURKCELL", "VODAFONE", "TURK TELEKOM", "SUPERONLINE", "TURKNET",
            "ISKI", "IGDAS", "CK BOGAZICI", "ENERJISA", "AYDEM", "ASKI", "IZSU",
            "BUSKI", "BASKENTGAZ", "FATURA", "NETFLIX", "SPOTIFY", "YOUTUBE",
            "GOOGLE ONE", "APPLE COM BILL", "ICLOUD", "OPENAI", "ANTHROPIC",
            "CLAUDE", "DISNEY", "GAIN", "EXXEN", "TOD", "BLUETV"
        ) to BILLS_SUBSCRIPTION,
        listOf(
            "LC WAIKIKI", "LCW", "DEFACTO", "KOTON", "MAVI", "ZARA", "BERSHKA",
            "PULL BEAR", "STRADIVARIUS", "BOYNER", "DECATHLON", "NIKE", "ADIDAS",
            "FLO", "IN STREET", "GRATIS", "WATSONS", "ROSSMANN", "SEPHORA"
        ) to CLOTHING,
        listOf(
            "VATAN BILGISAYAR", "MEDIAMARKT", "MEDIA MARKT", "TEKNOSA", "ITOPYA",
            "SINERJI", "INCEHESAP", "APPLE STORE", "SAMSUNG"
        ) to TECH,
        listOf(
            "ECZANE", "ECZANESI", "HASTANE", "HASTANESI", "MEDIKAL", "OPTIK",
            "DIS POLIKLINIK", "ACIBADEM", "MEMORIAL", "MEDICAL PARK", "LIV HOSPITAL"
        ) to HEALTH,
        listOf(
            "STEAM", "PLAYSTATION", "SONY ENT", "XBOX", "EPIC GAMES", "RIOT GAMES",
            "PARIBU CINEVERSE", "SINEMA", "BILETIX", "PASSO", "BUBILET"
        ) to ENTERTAINMENT
    )

    private val compiledKeywordRules: List<Pair<List<Regex>, String>> = rawKeywordRules.map { (keywords, category) ->
        val regexes = keywords.map { kw ->
            val clean = normalizeMerchantKey(kw)
            Regex("""\b${Regex.escape(clean)}\b""")
        }
        regexes to category
    }

    fun normalizeMerchantKey(merchant: String): String {
        return toAsciiUpper(merchant)
            .replace(Regex("[^A-Z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun classify(merchant: String): String {
        val normalized = normalizeMerchantKey(merchant)
        if (normalized.isEmpty()) return OTHER
        for ((regexList, category) in compiledKeywordRules) {
            if (regexList.any { it.containsMatchIn(normalized) }) {
                return category
            }
        }
        return OTHER
    }

    /**
     * Strict 1:1 length-preserving Turkish -> ASCII uppercase folding.
     * Guarantees input.length == output.length so regex match ranges map 1:1 onto original text.
     */
    fun toAsciiUpper(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            val mapped = when (ch) {
                'ı', 'I', 'i', 'İ' -> 'I'
                'ğ', 'Ğ' -> 'G'
                'ü', 'Ü' -> 'U'
                'ş', 'Ş' -> 'S'
                'ö', 'Ö' -> 'O'
                'ç', 'Ç' -> 'C'
                in 'a'..'z' -> (ch.code - 32).toChar()
                else -> ch
            }
            sb.append(mapped)
        }
        return sb.toString()
    }
}
