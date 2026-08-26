package com.example.policeapp.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SosRequest(
    val id: String,
    val personName: String,
    val phoneNumber: String,
    val sosType: SosType,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long,
    val isCompleted: Boolean = false,
    val completedTimestamp: Long? = null,
    val address: String = ""
)

@Serializable
enum class SosType {
    SELF,
    OTHER,
    APP
}
