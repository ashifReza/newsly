package com.example.newsly.data

import java.util.Locale

/** A country for the World tab's filter. [name] is in English, as used to search for its news. */
data class Country(val code: String, val name: String) {
    /** The country's flag emoji, built from its two-letter code. */
    val flag: String
        get() = code.uppercase().map { Character.toChars(0x1F1E6 + (it - 'A')).concatToString() }.joinToString("")

    companion object {
        /** Every country Android knows, A to Z, under the names news outlets use. */
        val all: List<Country> by lazy {
            Locale.getISOCountries()
                .map { Country(it, nameFor(it, Locale("", it).getDisplayCountry(Locale.ENGLISH))) }
                .filter { it.name.isNotBlank() && it.name != it.code }
                .sortedBy { it.name }
        }

        /**
         * Words that mark a story as being about the country: its name plus, for countries
         * often in the news, the usual short forms, adjectives and capital
         * ("Japan", "Japanese", "Tokyo"). Matched case-sensitively as whole words.
         */
        fun searchTerms(code: String, name: String): List<String> =
            (listOf(name) + ALIASES[code.uppercase()].orEmpty()).distinct()

        /** Android's English names, tidied into the form headlines use. */
        private fun nameFor(code: String, android: String): String =
            NAMES[code] ?: android.replace(" & ", " and ").replace("St. ", "Saint ")

        private val NAMES = mapOf(
            "HK" to "Hong Kong", "MO" to "Macau", "MM" to "Myanmar", "CD" to "DR Congo",
            "CG" to "Republic of the Congo", "CI" to "Ivory Coast", "PS" to "Palestine",
            "TR" to "Turkey", "UM" to "US Outlying Islands", "VI" to "US Virgin Islands",
        )

        private val ALIASES: Map<String, List<String>> = mapOf(
            "US" to listOf("U.S.", "US", "America", "American", "Americans"),
            "GB" to listOf("UK", "U.K.", "Britain", "British", "England", "Scotland", "Wales", "London"),
            "CN" to listOf("Chinese", "Beijing"),
            "HK" to listOf("Hong Kong's"),
            "TW" to listOf("Taiwanese", "Taipei"),
            "JP" to listOf("Japanese", "Tokyo"),
            "KR" to listOf("South Korean", "Seoul"),
            "KP" to listOf("North Korean", "Pyongyang"),
            "IN" to listOf("Indian", "New Delhi", "Delhi", "Mumbai"),
            "PK" to listOf("Pakistani", "Islamabad"),
            "BD" to listOf("Bangladeshi", "Dhaka"),
            "RU" to listOf("Russian", "Moscow", "Kremlin"),
            "UA" to listOf("Ukrainian", "Kyiv"),
            "FR" to listOf("French", "Paris"),
            "DE" to listOf("German", "Berlin"),
            "IT" to listOf("Italian", "Rome"),
            "ES" to listOf("Spanish", "Madrid"),
            "BR" to listOf("Brazilian", "Brasilia"),
            "AR" to listOf("Argentine", "Argentinian", "Buenos Aires"),
            "MX" to listOf("Mexican"),
            "CA" to listOf("Canadian", "Ottawa"),
            "AU" to listOf("Australian", "Canberra"),
            "NZ" to listOf("Kiwi"),
            "ZA" to listOf("South African"),
            "NG" to listOf("Nigerian", "Abuja", "Lagos"),
            "KE" to listOf("Kenyan", "Nairobi"),
            "EG" to listOf("Egyptian", "Cairo"),
            "IL" to listOf("Israeli"),
            "PS" to listOf("Palestinian", "Palestinians", "Gaza", "West Bank"),
            "IR" to listOf("Iranian", "Tehran"),
            "IQ" to listOf("Iraqi", "Baghdad"),
            "SA" to listOf("Saudi", "Riyadh"),
            "AE" to listOf("UAE", "Emirati", "Dubai", "Abu Dhabi"),
            "TR" to listOf("Türkiye", "Turkish", "Ankara", "Istanbul"),
            "SY" to listOf("Syrian", "Damascus"),
            "AF" to listOf("Afghan", "Kabul", "Taliban"),
            "PH" to listOf("Philippine", "Filipino", "Manila"),
            "ID" to listOf("Indonesian", "Jakarta"),
            "MY" to listOf("Malaysian", "Kuala Lumpur"),
            "SG" to listOf("Singaporean"),
            "TH" to listOf("Thai", "Bangkok"),
            "VN" to listOf("Vietnamese", "Hanoi"),
            "LK" to listOf("Sri Lankan", "Colombo"),
            "NP" to listOf("Nepali", "Nepalese", "Kathmandu"),
            "ET" to listOf("Ethiopian", "Addis Ababa"),
            "GH" to listOf("Ghanaian", "Accra"),
            "VE" to listOf("Venezuelan", "Caracas"),
            "CO" to listOf("Colombian", "Bogotá", "Bogota"),
            "CL" to listOf("Chilean", "Santiago"),
            "PE" to listOf("Peruvian", "Lima"),
            "CU" to listOf("Cuban", "Havana"),
            "PL" to listOf("Polish", "Warsaw"),
            "NL" to listOf("Dutch", "Amsterdam"),
            "SE" to listOf("Swedish", "Stockholm"),
            "NO" to listOf("Norwegian", "Oslo"),
            "IE" to listOf("Irish", "Dublin"),
            "LB" to listOf("Lebanese", "Beirut", "Hezbollah"),
            "YE" to listOf("Yemeni", "Houthi", "Houthis"),
            "QA" to listOf("Qatari", "Doha"),
            "SD" to listOf("Sudanese", "Khartoum"),
            "MM" to listOf("Burma", "Burmese"),
        )
    }
}
