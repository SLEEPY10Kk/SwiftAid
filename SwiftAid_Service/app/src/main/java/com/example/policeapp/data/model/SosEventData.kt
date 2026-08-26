package com.example.policeapp.data.model

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.DocumentId
import java.util.Date

/**
 * Data model for SOS emergency events in Firestore
 */
@IgnoreExtraProperties
data class SosEventData(
    @DocumentId
    val id: String = "",
    val victimName: String = "",
    val victimPhone: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val address: String = "",
    val severity: String = "MEDIUM",
    val status: String = "ACTIVE",
    val sosType: String = "SELF",
    val speed: Double = 0.0,
    val createdAt: Date? = null,
    val updatedAt: Date? = null,
    val assignedPoliceId: String? = null,
    val assignedPoliceETA: Long? = null,
    val completedAt: Date? = null,
    val targetResponderIds: List<String> = emptyList(),
    val targetServiceTypes: List<String> = emptyList(),
    val nearestHospitalId: String = "",
    val nearestHospitalName: String = "",
    val nearestHospitalPhone: String = "",
    val nearestHospitalDistanceMeters: Double = 0.0,
    val nearestHospitalRouteUrl: String = "",
    val nearestPoliceId: String = "",
    val nearestPoliceName: String = "",
    val nearestPolicePhone: String = "",
    val nearestPoliceDistanceMeters: Double = 0.0,
    val nearestPoliceRouteUrl: String = "",
    val policeResponse: SosServiceResponse? = null,
    val hospitalResponse: SosServiceResponse? = null
)

@IgnoreExtraProperties
data class SosServiceResponse(
    val status: String = "",
    val responderId: String = "",
    val responderName: String = "",
    val responderPhone: String = "",
    val etaMinutes: Int = 0,
    val acceptedAt: Date? = null
)

fun SosEventData.toSosRequest(): SosRequest {
    return SosRequest(
        id = id,
        personName = victimName.ifBlank { "SwiftAid User" },
        phoneNumber = victimPhone,
        sosType = when (sosType.uppercase()) {
            "OTHER" -> SosType.OTHER
            "APP", "CRASH", "MANUAL" -> SosType.APP
            else -> SosType.SELF
        },
        latitude = latitude,
        longitude = longitude,
        timestamp = createdAt?.time ?: System.currentTimeMillis(),
        isCompleted = status.equals("COMPLETED", ignoreCase = true),
        completedTimestamp = completedAt?.time,
        address = address.ifBlank { "Location source: unknown" }
    )
}
