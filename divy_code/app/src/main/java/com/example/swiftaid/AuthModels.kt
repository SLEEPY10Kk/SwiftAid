package com.example.swiftaid

import android.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class TokenRequest(val idToken: String)

@Serializable
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val isComplete: Boolean? = null
)

@Serializable
data class ErrorResponse(val detail: String)

@Serializable
data class EmailLoginRequest(val email: String, val password: String)

@Serializable
data class RefreshTokenRequest(val refreshToken: String)

@Serializable
data class CompleteProfileRequest(
    val username: String,
    val fullName: String,
    val age: Int,
    val gender: String,
    val dialCode: String,
    val phone: String,
    val country: String,
    val state: String,
    val city: String,
    val area: String?,
    val password: String
)

@Serializable
data class UserResponse(
    val id: String,
    val username: String,
    val email: String,
    @SerialName("first_name") val firstName: String,
    @SerialName("last_name") val lastName: String,
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("phone_number") val phoneNumber: String,
    val age: Int? = null,
    val gender: String? = null,
    val country: String? = null,
    val city: String? = null,
    val state: String? = null,
    val area: String? = null,
    @SerialName("is_complete") val isComplete: Boolean
)

@Serializable
data class EmergencyContactRequest(
    @SerialName("user_id") val userId: String? = null,
    @SerialName("contact_name") val contactName: String? = null,
    @SerialName("contact_number") val contactNumber: String,
    val relationship: String,
    @SerialName("priority_order") val priorityOrder: Int
)

@Serializable
data class EmergencyContactResponse(
    @SerialName("contact_id") val contactId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("contact_name") val contactName: String? = null,
    @SerialName("contact_number") val contactNumber: String,
    val relationship: String,
    @SerialName("priority_order") val priorityOrder: Int
)

@Serializable
data class MedicalInfoRequest(
    @SerialName("user_id") val userId: String? = null,
    val bloodGroup: String,
    val allergies: String,
    val chronicConditions: String,
    val currentmedications: String
)

@Serializable
data class MedicalInfoResponse(
    @SerialName("medical_id") val medicalId: String,
    @SerialName("user_id") val userId: String,
    val bloodGroup: String,
    val allergies: String,
    val chronicConditions: String,
    val currentmedications: String
)

@Serializable
data class InsuranceInfoRequest(
    @SerialName("user_id") val userId: String? = null,
    @SerialName("insurance_type") val insuranceType: String,
    @SerialName("insurance_provider") val insuranceProvider: String,
    @SerialName("insurance_policy_number") val insurancePolicyNumber: String,
    @SerialName("policy_holder_name") val policyHolderName: String? = null,
    @SerialName("coverage_type") val coverageType: String? = null,
    @SerialName("expiry_date") val expiryDate: String? = null,
    @SerialName("coverage_amount") val coverageAmount: String? = null,
    @SerialName("document_uri") val documentUri: String? = null
)

@Serializable
data class InsuranceInfoResponse(
    @SerialName("insurance_id") val insuranceId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("insurance_type") val insuranceType: String,
    @SerialName("insurance_provider") val insuranceProvider: String,
    @SerialName("insurance_policy_number") val insurancePolicyNumber: String,
    @SerialName("policy_holder_name") val policyHolderName: String? = null,
    @SerialName("coverage_type") val coverageType: String? = null,
    @SerialName("expiry_date") val expiryDate: String? = null,
    @SerialName("coverage_amount") val coverageAmount: String? = null,
    @SerialName("document_uri") val documentUri: String? = null
)

data class SafetyCompletion(
    val basicComplete: Boolean = false,
    val emergencyComplete: Boolean = false,
    val medicalComplete: Boolean = false,
    val insuranceComplete: Boolean = false
) {
    val percent: Int
        get() = listOf(
            if (basicComplete) 40 else 0,
            if (emergencyComplete) 20 else 0,
            if (medicalComplete) 20 else 0,
            if (insuranceComplete) 20 else 0
        ).sum()
}

sealed class AuthResult {
    data class Success(
        val accessToken: String,
        val refreshToken: String,
        val isComplete: Boolean? = null
    ) : AuthResult()
    data class Failure(val message: String) : AuthResult()
    object UserAlreadyExists : AuthResult()
    object Error : AuthResult()
}

fun extractUserIdFromJwt(token: String): String? {
    val payload = token.split(".").getOrNull(1) ?: return null
    return runCatching {
        val decoded = String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
        Json.parseToJsonElement(decoded).jsonObject["sub"]?.jsonPrimitive?.content
    }.getOrNull()
}
