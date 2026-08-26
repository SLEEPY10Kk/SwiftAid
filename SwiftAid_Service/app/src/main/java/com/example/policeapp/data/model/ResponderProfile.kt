package com.example.policeapp.data.model

import com.example.policeapp.AppMode
import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class ResponderProfile(
    val id: String = "",
    val serviceType: String = "",
    val name: String = "",
    val phoneNumber: String = "",
    val address: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val active: Boolean = true,
    val fcmToken: String = ""
) {
    val appMode: AppMode?
        get() = when (serviceType.uppercase()) {
            "POLICE" -> AppMode.POLICE
            "HOSPITAL" -> AppMode.HOSPITAL
            else -> null
        }
}
