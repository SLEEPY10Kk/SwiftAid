package com.example.swiftaid.emergency

import com.google.firebase.firestore.FirebaseFirestore
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class EmergencyResponderMatch(
    val id: String,
    val serviceType: String,
    val name: String,
    val phoneNumber: String,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Double,
    val routeUrl: String
) {
    fun toFirestoreMap(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "serviceType" to serviceType,
            "name" to name,
            "phoneNumber" to phoneNumber,
            "latitude" to latitude,
            "longitude" to longitude,
            "distanceMeters" to distanceMeters,
            "routeUrl" to routeUrl
        )
    }
}

object LiveEmergencyResponderDirectory {
    private const val EARTH_RADIUS_METERS = 6_371_000.0
    private const val TARGET_RESPONDER_RADIUS_METERS = 20_000.0

    fun findTargetResponders(
        latitude: Double,
        longitude: Double,
        onComplete: (List<EmergencyResponderMatch>) -> Unit
    ) {
        FirebaseFirestore.getInstance()
            .collection("responders")
            .whereEqualTo("active", true)
            .get()
            .addOnSuccessListener { snapshot ->
                val responders = snapshot.documents.mapNotNull { document ->
                    val serviceType = document.getString("serviceType")?.uppercase().orEmpty()
                    val name = document.getString("name").orEmpty()
                    val phone = document.getString("phoneNumber").orEmpty()
                    val responderLat = document.getDouble("latitude")
                    val responderLng = document.getDouble("longitude")
                    if (
                        serviceType !in setOf("POLICE", "HOSPITAL") ||
                        name.isBlank() ||
                        phone.isBlank() ||
                        responderLat == null ||
                        responderLng == null
                    ) {
                        null
                    } else {
                        val distance = haversineMeters(latitude, longitude, responderLat, responderLng)
                        EmergencyResponderMatch(
                            id = document.id,
                            serviceType = serviceType,
                            name = name,
                            phoneNumber = phone,
                            latitude = responderLat,
                            longitude = responderLng,
                            distanceMeters = distance,
                            routeUrl = routeUrl(latitude, longitude, responderLat, responderLng)
                        )
                    }
                }

                onComplete(
                    responders
                        .filter { it.distanceMeters <= TARGET_RESPONDER_RADIUS_METERS }
                        .sortedWith(compareBy<EmergencyResponderMatch> { it.serviceType }.thenBy { it.distanceMeters })
                )
            }
            .addOnFailureListener {
                onComplete(emptyList())
            }
    }

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

    private fun routeUrl(
        emergencyLat: Double,
        emergencyLng: Double,
        responderLat: Double,
        responderLng: Double
    ): String {
        return "https://www.google.com/maps/dir/?api=1" +
            "&origin=$responderLat,$responderLng" +
            "&destination=$emergencyLat,$emergencyLng" +
            "&travelmode=driving"
    }
}
