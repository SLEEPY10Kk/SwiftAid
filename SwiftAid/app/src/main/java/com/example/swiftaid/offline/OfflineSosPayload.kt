package com.example.swiftaid.offline

data class OfflineSosPayload(
    val eventId: String?,
    val relayDepth: Int,
    val userPhone: String?,
    val latitude: Double?,
    val longitude: Double?,
    val speedMetersPerSecond: Double?,
    val hospitalName: String?,
    val hospitalPhone: String?,
    val policeName: String?,
    val policePhone: String?,
    val rawMessage: String,
    val sender: String?
) {
    val hasCoordinates: Boolean = latitude != null && longitude != null

    companion object {
        const val PREFIX = "SOS_APP_CRASH_DETECTED"
        private const val READABLE_PREFIX = "SWIFTAID SOS CRASH DETECTED"
        private const val COMPACT_LEGACY_PREFIX = "SOSAPPCRASHDETECTED"
        private const val COMPACT_READABLE_PREFIX = "SWIFTAIDSOSCRASHDETECTED"
        private val eventIdRegex = Regex("""\b(?:EVENT|ID)\s*[:=]\s*([A-Za-z0-9._-]+)""", RegexOption.IGNORE_CASE)
        private val relayDepthRegex = Regex("""\bRELAY(?:_DEPTH)?\s*[:=]\s*(\d+)""", RegexOption.IGNORE_CASE)
        private val userPhoneRegex = Regex("""\b(?:USER_PHONE|VICTIM_PHONE|CALLBACK)\s*[:=]\s*([+()\d\s-]+)""", RegexOption.IGNORE_CASE)
        private val latitudeRegex = Regex("""\bLAT\s*[:=]\s*([-+]?\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
        private val longitudeRegex = Regex("""\b(?:LONG|LNG|LON)\s*[:=]\s*([-+]?\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
        private val speedRegex = Regex("""\bSPEED\s*[:=]\s*([-+]?\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
        private val hospitalRegex = serviceRegex("HOSPITAL")
        private val policeRegex = serviceRegex("POLICE")

        fun parse(message: String, sender: String?): OfflineSosPayload? {
            val normalizedMessage = message.replace("\r\n", "\n").trim()
            if (!looksLikeSwiftAidCrash(normalizedMessage)) return null
            val hospital = hospitalRegex.find(normalizedMessage)?.toServiceInfo()
            val police = policeRegex.find(normalizedMessage)?.toServiceInfo()

            return OfflineSosPayload(
                eventId = eventIdRegex.find(normalizedMessage)?.groupValues?.getOrNull(1),
                relayDepth = relayDepthRegex.findAll(normalizedMessage)
                    .lastOrNull()
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull()
                    ?: 0,
                userPhone = userPhoneRegex.find(normalizedMessage)?.groupValues?.getOrNull(1)?.normalizePhone(),
                latitude = latitudeRegex.find(normalizedMessage)?.groupValues?.getOrNull(1)?.toDoubleOrNull(),
                longitude = longitudeRegex.find(normalizedMessage)?.groupValues?.getOrNull(1)?.toDoubleOrNull(),
                speedMetersPerSecond = speedRegex.find(normalizedMessage)?.groupValues?.getOrNull(1)?.toDoubleOrNull(),
                hospitalName = hospital?.name,
                hospitalPhone = hospital?.phone,
                policeName = police?.name,
                policePhone = police?.phone,
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

        private fun serviceRegex(label: String): Regex {
            return Regex("""\b$label\s*[:=]\s*([^\n|]+)(?:\|[^\n]*)?\|\s*PHONE\s*[:=]\s*([+()\d\s-]+)""", RegexOption.IGNORE_CASE)
        }

        private fun MatchResult.toServiceInfo(): ServiceInfo? {
            val name = groupValues.getOrNull(1)?.trim().orEmpty()
            val phone = groupValues.getOrNull(2)
                ?.filter { it.isDigit() || it == '+' }
                .orEmpty()
            return if (phone.isBlank()) null else ServiceInfo(name = name.ifBlank { phone }, phone = phone)
        }

        private fun String.normalizePhone(): String {
            return filter { it.isDigit() || it == '+' }.takeIf { it.isNotBlank() }.orEmpty()
        }
    }
}

private data class ServiceInfo(
    val name: String,
    val phone: String
)
