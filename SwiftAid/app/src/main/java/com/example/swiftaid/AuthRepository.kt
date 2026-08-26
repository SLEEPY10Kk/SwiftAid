package com.example.swiftaid

import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: AuthApi,
    private val tokenStorage: TokenStorage
) {
    suspend fun completeGoogleAuth(idToken: String, register: Boolean): AuthResult {
        return try {
            if (register) {
                mapGoogleAuthResponse(api.sendTokenForRegister(idToken), register = true)
            } else {
                val loginResponse = api.sendTokenForLogin(idToken)
                when (loginResponse.status) {
                    HttpStatusCode.NotFound -> {
                        val registerResponse = api.sendTokenForRegister(idToken)
                        when (registerResponse.status) {
                            HttpStatusCode.Conflict ->
                                mapGoogleAuthResponse(api.sendTokenForLogin(idToken), register = false)
                            else -> mapGoogleAuthResponse(registerResponse, register = false)
                        }
                    }
                    else -> mapGoogleAuthResponse(loginResponse, register = false)
                }
            }
        } catch (exception: Exception) {
            AuthResult.Failure(
                "Cannot reach SwiftAid server at ${BuildConfig.API_BASE_URL}. " +
                    "Check that the backend or public tunnel is running and reachable from this device. " +
                    "(${exception.message.orEmpty().ifBlank { "network error" }})"
            )
        }
    }

    private suspend fun mapGoogleAuthResponse(
        response: HttpResponse,
        register: Boolean
    ): AuthResult = when {
        response.status == HttpStatusCode.OK -> {
            val body = response.body<AuthResponse>()
            tokenStorage.saveTokens(body.accessToken, body.refreshToken)
            AuthResult.Success(body.accessToken, body.refreshToken, body.isComplete)
        }
        register && response.status == HttpStatusCode.Conflict -> AuthResult.UserAlreadyExists
        response.status == HttpStatusCode.Unauthorized -> {
            val detail = response.errorDetail("GOOGLE_TOKEN_INVALID")
            AuthResult.Failure(
                when (detail) {
                    "GOOGLE_TOKEN_INVALID" ->
                        "Google sign-in failed. Please try again after checking your device time, Firebase config, and backend connectivity."
                    "GOOGLE_EMAIL_NOT_VERIFIED" ->
                        "Verify the email on your Google account, then try again."
                    else -> detail
                }
            )
        }
        response.status == HttpStatusCode.Conflict && !register -> {
            AuthResult.Failure(
                response.errorDetail(
                    "This email already has a SwiftAid account. Sign in with email/password, or use the same Google account you registered with."
                )
            )
        }
        response.status == HttpStatusCode.NotFound -> {
            AuthResult.Failure("No SwiftAid account found for this Google user.")
        }
        response.status.value == 500 -> {
            val detail = response.errorDetail("DATABASE_ERROR")
            AuthResult.Failure(
                if (detail == "DATABASE_ERROR") {
                    "Server database error. Restart the Mac backend (database was updated); then try Google sign-in again."
                } else {
                    detail
                }
            )
        }
        else -> {
            AuthResult.Failure(
                response.errorDetail("Google auth failed with HTTP ${response.status.value}.")
            )
        }
    }

    private suspend fun HttpResponse.errorDetail(fallback: String): String {
        return runCatching { body<ErrorResponse>().detail }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: fallback
    }
}
