package com.example.swiftaid.offline

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class OfflineEmergencyContact(
    val name: String,
    val type: String,
    val phone: String,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Double
)

data class OfflineEmergencyMatch(
    val district: String,
    val hospital: OfflineEmergencyContact?,
    val police: OfflineEmergencyContact?
)

class OfflineEmergencyDirectory(private val context: Context) {
    fun findNearest(latitude: Double, longitude: Double): OfflineEmergencyMatch {
        val districts = loadDistricts()
        val district = districts.firstOrNull { it.bounds.contains(latitude, longitude) }
            ?: districts.minByOrNull { it.bounds.centerDistanceTo(latitude, longitude) }

        val contacts = district?.contacts.orEmpty()
        return OfflineEmergencyMatch(
            district = district?.name ?: "Unknown district",
            hospital = contacts.nearest("hospital", latitude, longitude),
            police = contacts.nearest("police", latitude, longitude)
        )
    }

    private fun loadDistricts(): List<District> {
        val json = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        val root = JSONObject(json)
        val districts = root.getJSONArray("districts")
        return List(districts.length()) { index -> districts.getJSONObject(index).toDistrict() }
    }

    private fun JSONObject.toDistrict(): District {
        return District(
            name = getString("name"),
            bounds = getJSONObject("bounds").toBounds(),
            contacts = getJSONArray("contacts").toContacts()
        )
    }

    private fun JSONObject.toBounds(): Bounds {
        return Bounds(
            minLat = getDouble("minLat"),
            maxLat = getDouble("maxLat"),
            minLng = getDouble("minLng"),
            maxLng = getDouble("maxLng")
        )
    }

    private fun JSONArray.toContacts(): List<ContactRecord> {
        return List(length()) { index ->
            getJSONObject(index).run {
                ContactRecord(
                    name = getString("name"),
                    type = getString("type"),
                    phone = getString("phone"),
                    latitude = getDouble("lat"),
                    longitude = getDouble("lng")
                )
            }
        }
    }

    private fun List<ContactRecord>.nearest(
        type: String,
        latitude: Double,
        longitude: Double
    ): OfflineEmergencyContact? {
        return filter { it.type == type }
            .map { contact ->
                OfflineEmergencyContact(
                    name = contact.name,
                    type = contact.type,
                    phone = contact.phone,
                    latitude = contact.latitude,
                    longitude = contact.longitude,
                    distanceMeters = haversineMeters(latitude, longitude, contact.latitude, contact.longitude)
                )
            }
            .minByOrNull { it.distanceMeters }
    }

    private data class District(
        val name: String,
        val bounds: Bounds,
        val contacts: List<ContactRecord>
    )

    private data class Bounds(
        val minLat: Double,
        val maxLat: Double,
        val minLng: Double,
        val maxLng: Double
    ) {
        fun contains(latitude: Double, longitude: Double): Boolean {
            return latitude in minLat..maxLat && longitude in minLng..maxLng
        }

        fun centerDistanceTo(latitude: Double, longitude: Double): Double {
            return haversineMeters(latitude, longitude, (minLat + maxLat) / 2.0, (minLng + maxLng) / 2.0)
        }
    }

    private data class ContactRecord(
        val name: String,
        val type: String,
        val phone: String,
        val latitude: Double,
        val longitude: Double
    )

    companion object {
        private const val ASSET_NAME = "offline_emergency_contacts.json"
        private const val EARTH_RADIUS_METERS = 6_371_000.0

        fun haversineMeters(
            startLat: Double,
            startLng: Double,
            endLat: Double,
            endLng: Double
        ): Double {
            val dLat = Math.toRadians(endLat - startLat)
            val dLng = Math.toRadians(endLng - startLng)
            val lat1 = Math.toRadians(startLat)
            val lat2 = Math.toRadians(endLat)
            val a = sin(dLat / 2.0).pow(2.0) +
                cos(lat1) * cos(lat2) * sin(dLng / 2.0).pow(2.0)
            return EARTH_RADIUS_METERS * 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        }
    }
}
