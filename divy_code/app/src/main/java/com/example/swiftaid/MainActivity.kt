package com.example.swiftaid

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.tooling.preview.Preview
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.example.swiftaid.ui.theme.RoadSOSTheme
import io.ktor.client.call.body
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val tokenStorage = AndroidTokenStorage(applicationContext)
        val api = AuthApi()
        val credentialManager = CredentialManager.create(this)
        Log.d("AUTH_DEBUG", "GOOGLE_WEB_CLIENT_ID empty: ${BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()}")
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .build()
        val credentialRequest = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .build()
        val explicitGoogleRequest = GetCredentialRequest.Builder()
            .addCredentialOption(signInWithGoogleOption)
            .build()

        setContent {
            val scope = rememberCoroutineScope()

            val signInWithGoogle: ((AuthResult) -> Unit) -> Unit = { onResult ->
                scope.launch {
                    try {
                        val result = try {
                            credentialManager.getCredential(this@MainActivity, credentialRequest)
                        } catch (_: NoCredentialException) {
                            credentialManager.getCredential(this@MainActivity, explicitGoogleRequest)
                        }
                        val credential = result.credential
                        if (credential is CustomCredential &&
                            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                        ) {
                            val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                            val response = api.sendTokenForLogin(idToken)
                            if (response.status == HttpStatusCode.OK) {
                                val body = response.body<AuthResponse>()
                                tokenStorage.saveTokens(body.accessToken, body.refreshToken)
                                onResult(AuthResult.Success(body.accessToken, body.refreshToken, body.isComplete))
                            } else {
                                Log.d("AUTH_DEBUG", "Google login backend status: ${response.status}")
                                onResult(AuthResult.Failure("Google login failed: backend returned ${response.status.value}."))
                            }
                        } else {
                            Log.d("AUTH_DEBUG", "Google login returned unsupported credential: ${credential::class.simpleName}")
                            onResult(AuthResult.Failure("Google login did not return an ID token."))
                        }
                    } catch (e: Exception) {
                        Log.d("AUTH_DEBUG", "Google login exception", e)
                        onResult(AuthResult.Failure("Google login failed: ${e::class.java.simpleName}: ${e.message.orEmpty()}"))
                    }
                }
            }

            val signUpWithGoogle: ((AuthResult) -> Unit) -> Unit = { onResult ->
                scope.launch {
                    try {
                        val result = try {
                            credentialManager.getCredential(this@MainActivity, credentialRequest)
                        } catch (_: NoCredentialException) {
                            credentialManager.getCredential(this@MainActivity, explicitGoogleRequest)
                        }
                        val credential = result.credential
                        if (credential is CustomCredential &&
                            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                        ) {
                            val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                            val response = api.sendTokenForRegister(idToken)
                            when (response.status) {
                                HttpStatusCode.OK -> {
                                    val body = response.body<AuthResponse>()
                                    tokenStorage.saveTokens(body.accessToken, body.refreshToken)
                                    onResult(AuthResult.Success(body.accessToken, body.refreshToken, false))
                                }
                                HttpStatusCode.Conflict -> onResult(AuthResult.UserAlreadyExists)
                                else -> {
                                    Log.d("AUTH_DEBUG", "Google register backend status: ${response.status}")
                                    onResult(AuthResult.Failure("Google registration failed: backend returned ${response.status.value}."))
                                }
                            }
                        } else {
                            Log.d("AUTH_DEBUG", "Google register returned unsupported credential: ${credential::class.simpleName}")
                            onResult(AuthResult.Failure("Google registration did not return an ID token."))
                        }
                    } catch (e: Exception) {
                        Log.d("AUTH_DEBUG", "Google register exception", e)
                        onResult(AuthResult.Failure("Google registration failed: ${e::class.java.simpleName}: ${e.message.orEmpty()}"))
                    }
                }
            }

            RoadSOSTheme {
                App(
                    onGoogleSignInClick = signInWithGoogle,
                    onGoogleSignUpClick = signUpWithGoogle,
                    tokenStorage = tokenStorage,
                    api = api
                )
            }
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    RoadSOSTheme {
        App()
    }
}
