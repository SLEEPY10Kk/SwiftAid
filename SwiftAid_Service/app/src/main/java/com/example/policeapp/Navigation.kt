package com.example.policeapp

import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import com.example.policeapp.data.ResponderSession
import androidx.navigation3.ui.NavDisplay
import com.example.policeapp.ui.detail.SosDetailScreen
import com.example.policeapp.ui.login.LoginScreen
import com.example.policeapp.ui.main.MainScreen
import com.example.policeapp.ui.mode.ModeSelectionScreen

@Composable
fun MainNavigation(
    pendingSosDetailId: String? = null,
    onSosDetailConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val savedResponder = remember { ResponderSession.load(context) }
    LaunchedEffect(savedResponder) {
        savedResponder?.let(AppState::setResponder)
    }
    val startDestination = remember(savedResponder) {
        if (savedResponder == null) ModeSelection else Main
    }
    val backStack = rememberNavBackStack(startDestination)

    LaunchedEffect(pendingSosDetailId, savedResponder) {
        val sosId = pendingSosDetailId?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        if (savedResponder == null) return@LaunchedEffect
        backStack.removeAll { it is SosDetail }
        if (backStack.none { it is Main }) {
            backStack.add(Main)
        }
        backStack.add(SosDetail(requestId = sosId))
        onSosDetailConsumed()
    }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<ModeSelection> {
                ModeSelectionScreen(
                    onPoliceSelected = {
                        AppState.selectMode(AppMode.POLICE)
                        backStack.add(Login)
                    },
                    onHospitalSelected = {
                        AppState.selectMode(AppMode.HOSPITAL)
                        backStack.add(Login)
                    },
                )
            }

            entry<Login> {
                LoginScreen(
                    mode = AppState.selectedMode ?: AppMode.POLICE,
                    onAuthenticated = { profile ->
                        AppState.setResponder(profile)
                        backStack.removeLastOrNull()
                        backStack.add(Main)
                    },
                    modifier = Modifier.safeDrawingPadding(),
                )
            }

            entry<Main> {
                MainScreen(
                    onSosCardClick = { requestId ->
                        backStack.add(SosDetail(requestId = requestId))
                    },
                )
            }

            entry<SosDetail> { key ->
                SosDetailScreen(
                    requestId = key.requestId,
                    onBackClick = {
                        backStack.removeLastOrNull()
                    },
                    onMarkCompleted = {
                        backStack.removeLastOrNull()
                    },
                    modifier = Modifier.safeDrawingPadding(),
                )
            }
        },
    )
}
