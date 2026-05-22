package com.example.swiftaid.offline

data class OfflineSosPayload(
    val latitude: Double?,
    val longitude: Double?,
    val speedMetersPerSecond: Double?,
    val rawMessage: String,
    val sender: String?
) {
    val hasCoordinates: Boolean = latitude != null && longitude != null

    companion object {
        const val PREFIX = "SOS_APP_CRASH_DETECTED"
        private const val READABLE_PREFIX = "SWIFTAID SOS CRASH DETECTED"
        private const val COMPACT_LEGACY_PREFIX = "SOSAPPCRASHDETECTED"
        private const val COMPACT_READABLE_PREFIX = "SWIFTAIDSOSCRASHDETECTED"
        private val latitudeRegex = Regex("""\bLAT\s*[:=]\s*([-+]?\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
        private val longitudeRegex = Regex("""\b(?:LONG|LNG|LON)\s*[:=]\s*([-+]?\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
        private val speedRegex = Regex("""\bSPEED\s*[:=]\s*([-+]?\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)

        fun parse(message: String, sender: String?): OfflineSosPayload? {
            val normalizedMessage = message.replace("\r\n", "\n").trim()
            if (!looksLikeSwiftAidCrash(normalizedMessage)) return null

            return OfflineSosPayload(
                latitude = latitudeRegex.find(normalizedMessage)?.groupValues?.getOrNull(1)?.toDoubleOrNull(),
                longitude = longitudeRegex.find(normalizedMessage)?.groupValues?.getOrNull(1)?.toDoubleOrNull(),
                speedMetersPerSecond = speedRegex.find(normalizedMessage)?.groupValues?.getOrNull(1)?.toDoubleOrNull(),
                rawMessage = normalizedMessage,
                sender = sender
            )
        }

        private fun looksLikeSwiftAidCrash(message: String): Boolean {
            val compact = message
                .uppercase()
                .filter { it in 'A'..'Z' || it in '0'..'9' }
            return compact.contains(COMPACT_LEGACY_PREFIX) ||
                compact.contains(COMPACT_READABLE_PREFIX) ||
                message.contains(PREFIX, ignoreCase = true) ||
                message.contains(READABLE_PREFIX, ignoreCase = true)
        }
    }
}
