package dev.catsradar.presentation.regions

private const val REGIONAL_INDICATOR_A = 0x1F1E6
private const val SUPPLEMENTARY_START = 0x10000
private const val HIGH_SURROGATE_START = 0xD800
private const val LOW_SURROGATE_START = 0xDC00
private const val SURROGATE_BITS = 10
private const val LOW_SURROGATE_MASK = 0x3FF

/** The emoji flag of an ISO 3166 alpha-2 [countryCode]; null for anything but two letters. */
internal fun countryFlag(countryCode: String): String? {
    if (countryCode.length != 2 || countryCode.any { it !in 'A'..'Z' && it !in 'a'..'z' }) return null
    val code = countryCode.uppercase()
    return buildString {
        code.forEach { letter ->
            val codePoint = REGIONAL_INDICATOR_A + (letter - 'A') - SUPPLEMENTARY_START
            append((HIGH_SURROGATE_START + (codePoint shr SURROGATE_BITS)).toChar())
            append((LOW_SURROGATE_START + (codePoint and LOW_SURROGATE_MASK)).toChar())
        }
    }
}
