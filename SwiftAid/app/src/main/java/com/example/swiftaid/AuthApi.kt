package com.example.swiftaid

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class AuthApi(
    private val baseUrl: String = BuildConfig.API_BASE_URL
) {
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.bearer(accessToken: String) {
        header("Authorization", "Bearer $accessToken")
    }

    suspend fun sendTokenForLogin(idToken: String): HttpResponse =
        client.post("$baseUrl/auth/google/login") {
            contentType(ContentType.Application.Json)
            setBody(TokenRequest(idToken))
        }

    suspend fun sendTokenForRegister(idToken: String): HttpResponse =
        client.post("$baseUrl/auth/google/register") {
            contentType(ContentType.Application.Json)
            setBody(TokenRequest(idToken))
        }

    suspend fun emailLogin(email: String, password: String): HttpResponse =
        client.post("$baseUrl/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(EmailLoginRequest(email, password))
        }

    suspend fun emailRegister(request: EmailRegisterRequest): HttpResponse =
        client.post("$baseUrl/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    suspend fun validateSession(refreshToken: String): HttpResponse =
        client.post("$baseUrl/auth/validate") {
            contentType(ContentType.Application.Json)
            setBody(RefreshTokenRequest(refreshToken))
        }

    suspend fun refreshTokens(refreshToken: String): HttpResponse =
        client.post("$baseUrl/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody(RefreshTokenRequest(refreshToken))
        }

    suspend fun sendPhoneOtp(accessToken: String, phoneNumber: String): HttpResponse =
        client.post("$baseUrl/me/phone/send-otp") {
            contentType(ContentType.Application.Json)
            bearer(accessToken)
            setBody(PhoneOtpSendRequest(phoneNumber))
        }

    suspend fun verifyPhoneOtp(accessToken: String, phoneNumber: String, otpCode: String): HttpResponse =
        client.post("$baseUrl/me/phone/verify-otp") {
            contentType(ContentType.Application.Json)
            bearer(accessToken)
            setBody(PhoneOtpVerifyRequest(phoneNumber, otpCode))
        }

    suspend fun completeProfile(accessToken: String, request: CompleteProfileRequest): HttpResponse =
        client.post("$baseUrl/auth/complete-profile") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $accessToken")
            setBody(request)
        }

    suspend fun createEmergencyContact(accessToken: String, request: EmergencyContactRequest): HttpResponse =
        client.post("$baseUrl/me/emergency-contacts") {
            contentType(ContentType.Application.Json)
            bearer(accessToken)
            setBody(request)
        }

    suspend fun createMedicalInfo(accessToken: String, request: MedicalInfoRequest): HttpResponse =
        client.post("$baseUrl/me/medical-info") {
            contentType(ContentType.Application.Json)
            bearer(accessToken)
            setBody(request)
        }

    suspend fun createInsuranceInfo(accessToken: String, request: InsuranceInfoRequest): HttpResponse =
        client.post("$baseUrl/me/insurance-info") {
            contentType(ContentType.Application.Json)
            bearer(accessToken)
            setBody(request)
        }

    suspend fun loadInsuranceClaims(accessToken: String): HttpResponse =
        client.get("$baseUrl/me/insurance-claims") {
            bearer(accessToken)
        }

    suspend fun createInsuranceClaim(accessToken: String, request: InsuranceClaimRequest): HttpResponse =
        client.post("$baseUrl/me/insurance-claims") {
            contentType(ContentType.Application.Json)
            bearer(accessToken)
            setBody(request)
        }

    suspend fun loadUserSettings(accessToken: String): UserSettingsResponse {
        val response = client.get("$baseUrl/me/settings") {
            bearer(accessToken)
        }
        return response.body()
    }

    suspend fun updateUser(accessToken: String, request: UserUpdateRequest): HttpResponse =
        client.put("$baseUrl/me/profile") {
            contentType(ContentType.Application.Json)
            bearer(accessToken)
            setBody(request)
        }

    suspend fun updateEmergencyContact(accessToken: String, contactId: String, request: EmergencyContactUpdateRequest): HttpResponse =
        client.put("$baseUrl/me/emergency-contacts/$contactId") {
            contentType(ContentType.Application.Json)
            bearer(accessToken)
            setBody(request)
        }

    suspend fun deleteEmergencyContact(accessToken: String, contactId: String): HttpResponse =
        client.delete("$baseUrl/me/emergency-contacts/$contactId") {
            bearer(accessToken)
        }

    suspend fun updateMedicalInfo(accessToken: String, request: MedicalInfoUpdateRequest): HttpResponse =
        client.put("$baseUrl/me/medical-info") {
            contentType(ContentType.Application.Json)
            bearer(accessToken)
            setBody(request)
        }

    suspend fun deleteMedicalInfo(accessToken: String): HttpResponse =
        client.delete("$baseUrl/me/medical-info") {
            bearer(accessToken)
        }

    suspend fun updateInsuranceInfo(accessToken: String, insuranceId: String, request: InsuranceInfoUpdateRequest): HttpResponse =
        client.put("$baseUrl/me/insurance-info/$insuranceId") {
            contentType(ContentType.Application.Json)
            bearer(accessToken)
            setBody(request)
        }

    suspend fun deleteInsuranceInfo(accessToken: String, insuranceId: String): HttpResponse =
        client.delete("$baseUrl/me/insurance-info/$insuranceId") {
            bearer(accessToken)
        }

    suspend fun loadSafetyCompletion(accessToken: String): SafetyCompletion {
        val settings = runCatching { loadUserSettings(accessToken) }.getOrNull()
        return settings?.toSnapshot()?.toSafetyCompletion() ?: SafetyCompletion()
    }
}
