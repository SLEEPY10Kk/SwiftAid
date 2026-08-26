package com.example.policeapp.data.model

data class StationInfo(
    val stationName: String,
    val stationCode: String,
    val address: String,
    val jurisdiction: String,
    val masterName: String,
    val masterPhone: String,
    val contactNumbers: List<String>,
    val officerCount: Int,
    val currentShift: String,
    val district: String,
    val state: String
)
