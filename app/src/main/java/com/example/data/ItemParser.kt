package com.example.data

import java.util.Locale

object ItemParser {
    // List of cleanable common helper words in Russian, Uzbek and English
    private val helperWords = setOf(
        "шт", "штук", "штуки", "штука", "штукa", "по", "сум", "сом", "сома", "сомов", "у.е.", "рублей", "руб", "метр", "метров", "метра", "м",
        "ta", "dan", "som", "so'm", "sum", "uzs", "pcs", "piece", "pieces", "meters", "meter", "m."
    )

    /**
     * Preprocesses text to convert slang numbers like:
     * - "5 тыщ", "5тыс", "5 тысяч", "5.5 тыс", "1.5т" -> 5000, 5500, 1500
     * - "3 ляма", "1.2 млн" -> 3000000, 1200000
     * - "50 минг", "7.5 ming" -> 50000, 7500
     * - "150к", "150 k" -> 150000
     * Correctly handles word boundaries so as to not corrupt technical metrics like meters (10м) etc.
     */
    fun normalizeSlangNumbers(text: String): String {
        // First convert dotted or comma thousands like 3.000, 25.000, 3,000 to clean integers (3000, 25000, 3000)
        val thousandsRegex = """\b(\d+)[.,](\d{3})\b""".toRegex()
        var normalized = thousandsRegex.replace(text) { matchResult ->
            matchResult.groupValues[1] + matchResult.groupValues[2]
        }

        // Regex to find patterns like: "5 тыс", "5.5тыщ", "10 минг", "3 ляма", "150к", "1.2 млн", etc.
        val slangRegex = """(?i)(\d+(?:[.,]\d+)?)\s*(тысяч[аи]?|тыс[яч]*|тыщ[аи]?|минг|ming|лям[аов]*|млн|миллион[аов]*|mln|million|[кkт])\b""".toRegex()
        
        return slangRegex.replace(normalized) { matchResult ->
            val numStr = matchResult.groupValues[1].replace(",", ".")
            val suffix = matchResult.groupValues[2].lowercase(Locale.ROOT)
            
            val value = numStr.toDoubleOrNull() ?: return@replace matchResult.value
            
            val multiplier = when {
                suffix.startsWith("тыс") ||
                suffix.startsWith("тысяч") ||
                suffix.startsWith("тыщ") ||
                suffix.startsWith("минг") ||
                suffix.startsWith("ming") ||
                suffix == "к" ||
                suffix == "k" ||
                suffix == "т" -> 1000.0

                suffix.startsWith("лям") ||
                suffix.startsWith("млн") ||
                suffix.startsWith("миллион") ||
                suffix.startsWith("mln") ||
                suffix.startsWith("million") -> 1000000.0

                else -> 1.0
            }
            
            val calculated = value * multiplier
            if (calculated % 1.0 == 0.0) {
                calculated.toLong().toString()
            } else {
                calculated.toString()
            }
        }
    }

    fun parseLine(line: String): ParsedItem? {
        val normalized = normalizeSlangNumbers(line)
        val trimmed = normalized.trim()
        if (trimmed.isEmpty()) return null

        // Preserve electrical specifications by masking them first.
        // This stops specifications like "3х2.5", "16А" or "220В" from being parsed as quantities/prices.
        val specsMap = mutableListOf<String>()
        var maskedLine = trimmed

        val specRegexes = listOf(
            // Cross Section (e.g. 3x2.5 or 3х1.5 or 3*6 or 3x1,5)
            """\b\d+(?:[.,]\d+)?\s*[xх*×]\s*\d+(?:[.,]\d+)?\b""".toRegex(RegexOption.IGNORE_CASE),
            // Voltage (e.g. 220В, 220B, 380V, 12v)
            """\b\d+\s*(?:v|b|в|V|B|В)\b""".toRegex(),
            // Amperage (e.g. 16А, 25A, 10а, 16a)
            """\b\d+\s*(?:a|а|A|А)\b""".toRegex(),
            // Phases (e.g. 3-фазный, 1-фазный)
            """\b\d+-?(?:фазны[йиае]|фаз|фаз\.)\b""".toRegex(RegexOption.IGNORE_CASE)
        )

        for (regex in specRegexes) {
            maskedLine = regex.replace(maskedLine) { match ->
                specsMap.add(match.value)
                "__SPEC_${specsMap.size - 1}__"
            }
        }

        // Try to find numbers inside the masked line
        val numberRegex = """\d+([.,]\d+)?""".toRegex()
        val matchResults = numberRegex.findAll(maskedLine).toList()

        val unitDetected = detectUnit(trimmed)

        if (matchResults.isEmpty()) {
            // Restore specifications back to name
            var restoredName = trimmed
            specsMap.forEachIndexed { idx, spec ->
                restoredName = restoredName.replace("__SPEC_${idx}__", spec)
            }
            val finalName = cleanName(restoredName)
            return ParsedItem(name = finalName, quantity = 1.0, price = 0.0, total = 0.0, unit = unitDetected)
        }

        val allNumbers = matchResults.map { match ->
            match.value.replace(",", ".").toDoubleOrNull() ?: 0.0
        }

        val nameWithoutNumbers = maskedLine.replace(numberRegex, " ").trim()

        // Distill name
        val cleanNameStr = cleanName(nameWithoutNumbers)

        var resultItem = when (allNumbers.size) {
            1 -> {
                val singleNum = allNumbers[0]
                val lower = trimmed.lowercase()
                val isPrice = lower.contains("по") || lower.contains("dan") || lower.contains("сум") || lower.contains("сом") || lower.contains("som") || lower.contains("so'm")
                if (isPrice) {
                    ParsedItem(name = cleanNameStr, quantity = 1.0, price = singleNum, total = singleNum, unit = unitDetected)
                } else {
                    ParsedItem(name = cleanNameStr, quantity = singleNum, price = 0.0, total = 0.0, unit = unitDetected)
                }
            }
            2 -> {
                val qty = allNumbers[0]
                val price = allNumbers[1]
                ParsedItem(name = cleanNameStr, quantity = qty, price = price, total = qty * price, unit = unitDetected)
            }
            else -> {
                val qty = allNumbers.first()
                val price = allNumbers.last()
                ParsedItem(name = cleanNameStr, quantity = qty, price = price, total = qty * price, unit = unitDetected)
            }
        }

        // Restore masked specifications into the final item name
        var finalItemName = resultItem.name
        specsMap.forEachIndexed { idx, originalSpec ->
            finalItemName = finalItemName.replace("__SPEC_${idx}__", originalSpec)
        }

        return resultItem.copy(name = finalItemName)
    }

    fun detectUnit(line: String): String {
        val lower = line.lowercase(Locale.ROOT)
        return when {
            // Meters: e.g. 150м, 150 м, 150метров, meters, etc.
            lower.contains(Regex("""\d\s*(метр|метров|метра|м|meters|meter|m)\b""")) ||
            lower.contains(Regex("""\b(метр|метров|метра|meters|meter)\b""")) -> "м"

            // Tochek (Points): e.g. 10точек, 10 точек, etc.
            lower.contains(Regex("""\d\s*(точек|точка|точки|точ|point|points)\b""")) ||
            lower.contains(Regex("""\b(точек|точка|точки|точ|point|points)\b""")) -> "точ"

            // Boxes: e.g. 5коробки, 5кор, etc.
            lower.contains(Regex("""\d\s*(коробк[аио]?|кор|box|boxes)\b""")) ||
            lower.contains(Regex("""\b(коробк[аио]?|кор|box|boxes)\b""")) -> "кор"

            // Kits: e.g. 2комплект, kit, etc.
            lower.contains(Regex("""\d\s*(комплект[ы]?|компл|kit|kits)\b""")) ||
            lower.contains(Regex("""\b(комплект[ы]?|компл|kit|kits)\b""")) -> "компл"

            // Weight (Kilograms): e.g. 5кг, kg, etc.
            lower.contains(Regex("""\d\s*(кг|килограмм[аов]?|kg|kilogram|kilograms)\b""")) ||
            lower.contains(Regex("""\b(кг|килограмм[аов]?|kg|kilogram|kilograms)\b""")) -> "кг"

            // Pieces (Uzbek ta): e.g. 10ta, etc.
            lower.contains(Regex("""\d\s*(ta|dona)\b""")) ||
            lower.contains(Regex("""\b(ta|dona)\b""")) -> "ta"

            // Pieces (Russian/English): e.g. 10шт, pcs, etc.
            lower.contains(Regex("""\d\s*(штук[аио]?|шт|pcs|piece|pieces)\b""")) ||
            lower.contains(Regex("""\b(штук[аио]?|шт|pcs|piece|pieces)\b""")) -> "шт"

            else -> "шт"
        }
    }

    private fun cleanName(rawName: String): String {
        // Tokenize and filter out helper words
        val tokens = rawName.split(Regex("""\s+"""))
        val cleanTokens = tokens.map { it.replace(Regex("""[.,;:\-—]"""), "") }
            .filter { token ->
                val lowerToken = token.lowercase(Locale.ROOT)
                lowerToken.isNotEmpty() && !helperWords.contains(lowerToken) && lowerToken.length > 1
            }
        
        val joined = cleanTokens.joinToString(" ")
        val finalResult = if (joined.isBlank()) {
            rawName.replace(Regex("""[.,;:\-—\d]"""), " ").trim().replace(Regex("""\s+"""), " ")
        } else {
            joined
        }
        
        // Auto-capitalize first letter for professional appearance
        return finalResult.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    fun parseBulkText(text: String): List<ParsedItem> {
        // Split by lines, non-numeric commas, or semicolons
        val splitLines = text
            .replace("(?<!\\d),(?!\\d)".toRegex(), "\n")
            .replace(";", "\n")
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        return splitLines.mapNotNull { parseLine(it) }
    }
}
