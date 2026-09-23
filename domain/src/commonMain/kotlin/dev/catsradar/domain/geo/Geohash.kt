package dev.catsradar.domain.geo

private const val BASE32_ALPHABET = "0123456789bcdefghjkmnpqrstuvwxyz"
private const val BITS_PER_CHAR = 5
private const val MIN_PRECISION = 1
private const val MAX_PRECISION = 12

object Geohash {
    fun encode(lat: Double, lon: Double, precision: Int): String {
        require(precision in MIN_PRECISION..MAX_PRECISION) {
            "precision must be in $MIN_PRECISION..$MAX_PRECISION, was $precision"
        }
        require(lat in MIN_LATITUDE..MAX_LATITUDE) { "lat must be in [$MIN_LATITUDE, $MAX_LATITUDE], was $lat" }
        require(lon in MIN_LONGITUDE..MAX_LONGITUDE) { "lon must be in [$MIN_LONGITUDE, $MAX_LONGITUDE], was $lon" }

        var latMin = MIN_LATITUDE
        var latMax = MAX_LATITUDE
        var lonMin = MIN_LONGITUDE
        var lonMax = MAX_LONGITUDE
        var isLongitudeBit = true // geohash interleaves longitude into the even bit positions first
        var bitsInChar = 0
        var charIndex = 0
        val hash = StringBuilder(precision)

        while (hash.length < precision) {
            if (isLongitudeBit) {
                val mid = (lonMin + lonMax) / 2
                if (lon >= mid) {
                    charIndex = charIndex * 2 + 1
                    lonMin = mid
                } else {
                    charIndex *= 2
                    lonMax = mid
                }
            } else {
                val mid = (latMin + latMax) / 2
                if (lat >= mid) {
                    charIndex = charIndex * 2 + 1
                    latMin = mid
                } else {
                    charIndex *= 2
                    latMax = mid
                }
            }
            isLongitudeBit = !isLongitudeBit
            bitsInChar++
            if (bitsInChar == BITS_PER_CHAR) {
                hash.append(BASE32_ALPHABET[charIndex])
                bitsInChar = 0
                charIndex = 0
            }
        }
        return hash.toString()
    }

    fun decode(hash: String): BoundingBox {
        require(hash.isNotEmpty()) { "geohash must not be empty" }
        require(hash.length <= MAX_PRECISION) { "geohash length must be <= $MAX_PRECISION, was ${hash.length}" }

        var latMin = MIN_LATITUDE
        var latMax = MAX_LATITUDE
        var lonMin = MIN_LONGITUDE
        var lonMax = MAX_LONGITUDE
        var isLongitudeBit = true

        // Accept mixed-case input like other geohash implementations do; this app only ever
        // writes lowercase, but a hash pasted in from elsewhere may not be.
        for (char in hash.lowercase()) {
            val charIndex = BASE32_ALPHABET.indexOf(char)
            require(charIndex >= 0) { "invalid geohash character '$char'" }
            for (bit in BITS_PER_CHAR - 1 downTo 0) {
                val bitValue = (charIndex shr bit) and 1
                if (isLongitudeBit) {
                    val (min, max) = narrow(lonMin, lonMax, bitValue)
                    lonMin = min
                    lonMax = max
                } else {
                    val (min, max) = narrow(latMin, latMax, bitValue)
                    latMin = min
                    latMax = max
                }
                isLongitudeBit = !isLongitudeBit
            }
        }
        return BoundingBox(south = latMin, west = lonMin, north = latMax, east = lonMax)
    }

    private fun narrow(min: Double, max: Double, bitValue: Int): Pair<Double, Double> {
        val mid = (min + max) / 2
        return if (bitValue == 1) mid to max else min to mid
    }

    /** Whether [encode] could have written [hash] at [precision] — lowercase only, unlike what [decode] accepts. */
    fun isWellFormed(hash: String, precision: Int): Boolean {
        require(precision in MIN_PRECISION..MAX_PRECISION) {
            "precision must be in $MIN_PRECISION..$MAX_PRECISION, was $precision"
        }
        return hash.length == precision && hash.all { it in BASE32_ALPHABET }
    }

    // A geohash's prefix is itself a valid geohash at that precision - no re-encoding needed.
    fun prefix(hash: String, precision: Int): String {
        require(precision in MIN_PRECISION..MAX_PRECISION) {
            "precision must be in $MIN_PRECISION..$MAX_PRECISION, was $precision"
        }
        require(precision <= hash.length) { "cannot widen '$hash' to precision $precision" }
        return hash.take(precision)
    }
}
