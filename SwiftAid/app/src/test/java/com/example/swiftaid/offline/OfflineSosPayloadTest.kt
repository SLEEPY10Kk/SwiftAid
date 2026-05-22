package com.example.swiftaid.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class OfflineSosPayloadTest {
    @Test
    fun parse_acceptsSamsungMessageWithMangledSeparators() {
        val payload = OfflineSosPayload.parse(
            """
            SOS?APP?CRASH?DETECTED?|
            LAT:23.024228?|LONG:72.529000?|
            SPEED:0.1
            Crash detected. Open in Google Maps:
            https://maps.google.com/?q=23.024228,72.529000
            """.trimIndent(),
            sender = "+919999999999"
        )

        assertNotNull(payload)
        assertEquals(23.024228, payload?.latitude ?: 0.0, 0.000001)
        assertEquals(72.529000, payload?.longitude ?: 0.0, 0.000001)
        assertEquals(0.1, payload?.speedMetersPerSecond ?: 0.0, 0.000001)
    }

    @Test
    fun parse_acceptsReadableSwiftAidPrefix() {
        val payload = OfflineSosPayload.parse(
            """
            SWIFTAID SOS CRASH DETECTED
            LAT:23.024282|LONG:72.528945|SPEED:0.0
            """.trimIndent(),
            sender = "+919999999999"
        )

        assertNotNull(payload)
        assertEquals(23.024282, payload?.latitude ?: 0.0, 0.000001)
        assertEquals(72.528945, payload?.longitude ?: 0.0, 0.000001)
    }
}
