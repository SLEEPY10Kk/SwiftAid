package com.example.swiftaidmobile.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "city_pois",
    indices = [
        Index(value = ["type"]),
        Index(value = ["lat", "lon"])
    ]
)
data class CityPoiEntity(
    @PrimaryKey val sourceId: String,
    val osmId: Long,
    val osmType: String,
    val city: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val type: String,
    val rawType: String,
    val address: String,
    val phone: String? = null,
    val openingHours: String? = null,
    val website: String? = null,
    val distanceM: Int, // distance from export center
    val generatedAt: Long
)
