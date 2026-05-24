package com.example.swiftaidmobile.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pois")
data class PoiEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name:       String,
    val address:    String,
    val lat:        Double,
    val lon:        Double,
    val type:       String,
    val sources:    String,    // "mappls,osm,google"
    val distance_m: Int,
    val cached_at:  Long,
    val phone:      String? = null,  // from Google Places / OSM tags.phone
)