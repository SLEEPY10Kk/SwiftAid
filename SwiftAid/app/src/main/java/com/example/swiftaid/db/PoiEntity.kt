package com.example.swiftaid.db

data class PoiEntity(
    val id: Int = 0,
    val name:       String,
    val address:    String,
    val lat:        Double,
    val lon:        Double,
    val type:       String,
    val sources:    String,    // "mappls,osm,google"
    val distance_m: Int,
    val route_distance_m: Int? = null,
    val eta_seconds: Int? = null,
    val cached_at:  Long,
    val phone:      String? = null,
)
