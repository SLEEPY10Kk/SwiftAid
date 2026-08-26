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

    @Test
    fun parse_extractsEmergencyServiceDialNumbers() {
        val payload = OfflineSosPayload.parse(
            """
            SWIFTAID SOS CRASH DETECTED
            LAT:23.024282|LONG:72.528945|SPEED:0.0
            HOSPITAL:VS General Hospital|PHONE:07926577621|DIST:0.2km
            POLICE:Ahmedabad Police Control Room|PHONE:100|DIST:90m
            Crash detected. Open in Google Maps: https://maps.google.com/?q=23.024282,72.528945
            """.trimIndent(),
            sender = "+919999999999"
        )

        assertNotNull(payload)
        assertEquals("VS General Hospital", payload?.hospitalName)
        assertEquals("07926577621", payload?.hospitalPhone)
        assertEquals("Ahmedabad Police Control Room", payload?.policeName)
        assertEquals("100", payload?.policePhone)
    }

    @Test
    fun parse_extractsRelayMetadata() {
        val payload = OfflineSosPayload.parse(
            """
            SWIFTAID SOS CRASH DETECTED
            EVENT:abc-123_def|RELAY:0
            USER_PHONE:+91 98765 43210
            LAT:23.024282|LONG:72.528945|SPEED:0.0
            RELAY:2
            """.trimIndent(),
            sender = "+919999999999"
        )

        assertNotNull(payload)
        assertEquals("abc-123_def", payload?.eventId)
        assertEquals(2, payload?.relayDepth)
        assertEquals("+919876543210", payload?.userPhone)
    }
}
