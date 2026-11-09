package com.hasanbucak.harcamatakip.domain.parser

import java.util.Locale

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

    private val keywordRules: List<Pair<List<String>, String>> = listOf(
        listOf(
            "MIGROS", "BIM ", "BİM ", "BIM BIRLESIK", "A101", "SOK MARKET", "ŞOK MARKET",
            "CARREFOUR", "MACROCENTER", "FILE MARKET", "FİLE MARKET", "GETIR", "GETİR",
            "ISTEGELSIN", "METRO GROSS", "CAGRI MARKET", "MOPAS", "ONUR MARKET", "HAKMAR"
        ) to MARKET,
        listOf(
            "YEMEKSEPETI", "TRENDYOL YEMEK", "STARBUCKS", "KAHVE DUNYASI", "ESPRESSOLAB",
            "MCDONALD", "BURGER KING", "POPEYES", "DOMINO", "KFC", "TAVUK DUNYASI",
            "RESTORAN", "RESTAURANT", "CAFE", "KAFE", "PASTANESI", "FIRIN", "KEBAP", "DONER"
        ) to FOOD_CAFE,
        listOf(
            "OPET", "SHELL", "PETROL OFISI", "BP PETROL", "TOTAL ENERGIES", "AYTEMIZ",
            "TURKIYE PETROLLERI", "OTOPARK", "ISPARK", "HGS", "OGS", "ISTANBULKART",
            "BELBIM", "TAKSI", "BITAKSI", "UBER", "MARTI", "THY ", "PEGASUS", "AJET", "TCDD"
        ) to FUEL_TRANSPORT,
        listOf(
            "TRENDYOL", "HEPSIBURADA", "AMAZON", "N11", "PAZARAMA", "CICEKSEPETI",
            "SAHIBINDEN", "DOLAP", "TEMU", "ALIEXPRESS", "IYZICO", "PAYTR", "PARAM"
        ) to ONLINE_SHOPPING,
        listOf(
            "TURKCELL", "VODAFONE", "TURK TELEKOM", "SUPERONLINE", "TURKNET",
            "ISKI", "IGDAS", "CK BOGAZICI", "ENERJISA", "AYDEM", "ASKI", "IZSU",
            "NETFLIX", "SPOTIFY", "YOUTUBE", "GOOGLE ONE", "APPLE.COM/BILL", "ICLOUD",
            "OPENAI", "ANTHROPIC", "CLAUDE", "DISNEY", "GAIN", "EXXEN", "TOD ", "BLUETV"
        ) to BILLS_SUBSCRIPTION,
        listOf(
            "LC WAIKIKI", "LCW", "DEFACTO", "KOTON", "MAVI ", "ZARA", "BERSHKA",
            "PULL&BEAR", "STRADIVARIUS", "BOYNER", "DECATHLON", "NIKE", "ADIDAS",
            "FLO ", "IN STREET", "GRATIS", "WATSONS", "ROSSMANN", "SEPHORA"
        ) to CLOTHING,
        listOf(
            "VATAN BILGISAYAR", "MEDIAMARKT", "MEDIA MARKT", "TEKNOSA", "ITOPYA",
            "SINERJI", "INCEHESAP", "APPLE STORE", "SAMSUNG"
        ) to TECH,
        listOf(
            "ECZANE", "HASTANE", "MEDIKAL", "OPTIK", "DIS POLIKLINIK", "ACIBADEM",
            "MEMORIAL", "MEDICAL PARK", "LIV HOSPITAL"
        ) to HEALTH,
        listOf(
            "STEAM", "PLAYSTATION", "SONY ENT", "XBOX", "EPIC GAMES", "RIOT GAMES",
            "PARIBU CINEVERSE", "SINEMA", "BILETIX", "PASSO", "BUBILET"
        ) to ENTERTAINMENT
    )

    fun normalizeMerchantKey(merchant: String): String {
        return toAsciiUpper(merchant)
            .replace(Regex("[^A-Z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun classify(merchant: String): String {
        val normalized = " ${normalizeMerchantKey(merchant)} "
        for ((keywords, category) in keywordRules) {
            if (keywords.any { kw -> normalized.contains(toAsciiUpper(kw)) }) {
                return category
            }
        }
        return OTHER
    }

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
                else -> ch.uppercaseChar()
            }
            sb.append(mapped)
        }
        return sb.toString().uppercase(Locale.US)
    }
}
