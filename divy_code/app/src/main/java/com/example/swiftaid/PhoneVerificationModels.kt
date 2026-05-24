package com.example.swiftaid

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PhoneOtpSendRequest(
    @SerialName("phone_number") val phoneNumber: String
)

@Serializable
data class PhoneOtpVerifyRequest(
    @SerialName("phone_number") val phoneNumber: String,
    @SerialName("otp_code") val otpCode: String
)

@Serializable
data class PhoneOtpResponse(
    val detail: String,
    @SerialName("phone_number") val phoneNumber: String,
    @SerialName("expires_in_seconds") val expiresInSeconds: Int,
    @SerialName("debug_code") val debugCode: String? = null
)
