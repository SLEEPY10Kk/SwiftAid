package com.example.policeapp.ui.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.policeapp.AppMode
import com.example.policeapp.AppState
import com.example.policeapp.data.model.ResponderProfile
import com.example.policeapp.data.model.StationInfo
import com.example.policeapp.data.model.toSosRequest
import com.example.policeapp.firebase.FirebasePoliceRepository
import com.example.policeapp.theme.BackgroundBlack
import com.example.policeapp.theme.CardBackground
import com.example.policeapp.theme.PrimaryBlue
import com.example.policeapp.theme.TextPrimary
import com.example.policeapp.theme.TextSecondary
import com.example.policeapp.ui.sos.RealTimeSosScreen

@Composable
fun MainScreen(
    onSosCardClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedMode = AppState.selectedMode

    when (selectedMode) {
        AppMode.POLICE -> PoliceMainScreenContent(
            onSosCardClick = onSosCardClick,
            modifier = modifier,
        )
        AppMode.HOSPITAL -> HospitalMainScreen(
            onEmergencyCardClick = onSosCardClick,
            modifier = modifier,
        )
        null -> {
            // Fallback to police if mode not selected
            PoliceMainScreenContent(
                onSosCardClick = onSosCardClick,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun PoliceMainScreenContent(
    onSosCardClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val responder = AppState.currentResponder
    val firebaseRepository = remember { FirebasePoliceRepository() }
    val activeSosEvents by firebaseRepository.activeSosEvents.observeAsState(initial = emptyList())
    val completedSosEvents by firebaseRepository.completedSosEvents.observeAsState(initial = emptyList())
    val completedRequests = remember(completedSosEvents) {
        completedSosEvents.map { it.toSosRequest() }
    }

    DisposableEffect(firebaseRepository, responder) {
        val responderId = responder?.id
        if (responderId.isNullOrBlank()) {
            firebaseRepository.stopListeningToSosEvents()
        } else {
            firebaseRepository.startListeningToSosEvents(responder)
        }
        onDispose { firebaseRepository.stopListeningToSosEvents() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundBlack)
            .statusBarsPadding(),
    ) {
        // Content area
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 80.dp), // Space for floating bar
        ) {
            AnimatedContent(
                targetState = selectedTab,
                label = "tabContent",
                transitionSpec = {
                    (fadeIn() + slideInHorizontally { if (targetState > initialState) it / 4 else -it / 4 })
                        .togetherWith(fadeOut() + slideOutHorizontally { if (targetState > initialState) -it / 4 else it / 4 })
                },
            ) { tab ->
                when (tab) {
                    0 -> RealTimeSosScreen(
                        onSosCardClick = onSosCardClick,
                        repository = firebaseRepository,
                        responderId = responder?.id,
                    )
                    1 -> CompletedSosScreen(
                        requests = completedRequests,
                        onCardClick = onSosCardClick,
                    )
                    2 -> StationInfoScreen(
                        stationInfo = responder.toStationInfo(),
                    )
                }
            }
        }

        // Floating Pill Navigation Bar
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp, start = 24.dp, end = 24.dp)
                .navigationBarsPadding()
        ) {
            PolicePillNavigationBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                pendingCount = activeSosEvents.size,
            )
        }
    }
}

private fun ResponderProfile?.toStationInfo(): StationInfo {
    val profile = this
    return StationInfo(
        stationName = profile?.name ?: "Unregistered service",
        stationCode = profile?.id ?: "NO-SERVICE",
        address = profile?.address ?: "Register service location",
        jurisdiction = "Nearest live SOS routing",
        masterName = profile?.name ?: "Service account",
        masterPhone = profile?.phoneNumber ?: "",
        contactNumbers = listOfNotNull(profile?.phoneNumber?.takeIf { it.isNotBlank() }),
        officerCount = 0,
        currentShift = if (profile?.active == true) "Live registration active" else "Inactive",
        district = "${profile?.latitude ?: 0.0}",
        state = "${profile?.longitude ?: 0.0}"
    )
}

@Composable
private fun PolicePillNavigationBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    pendingCount: Int,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(CardBackground.copy(alpha = 0.8f)) // 80% Opacity
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PillNavItem(
                icon = Icons.Default.Warning,
                label = "Pending",
                isSelected = selectedTab == 0,
                badgeCount = pendingCount,
                selectedColor = PrimaryBlue,
                onClick = { onTabSelected(0) },
            )
            PillNavItem(
                icon = Icons.Default.CheckCircle,
                label = "Completed",
                isSelected = selectedTab == 1,
                selectedColor = PrimaryBlue,
                onClick = { onTabSelected(1) },
            )
            PillNavItem(
                icon = Icons.Default.Home,
                label = "Station",
                isSelected = selectedTab == 2,
                selectedColor = PrimaryBlue,
                onClick = { onTabSelected(2) },
            )
        }
    }
}

@Composable
private fun PillNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit,
    badgeCount: Int = 0,
) {
    val backgroundColor = if (isSelected) selectedColor else Color.Transparent
    val contentColor = if (isSelected) Color.White else TextSecondary

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (badgeCount > 0 && !isSelected) {
            BadgedBox(
                badge = {
                    Badge(containerColor = PrimaryBlue, contentColor = Color.White) {
                        Text("$badgeCount", style = MaterialTheme.typography.labelSmall)
                    }
                },
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )
        }
        if (isSelected) {
            Text(
                text = label,
                color = contentColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}
