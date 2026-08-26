package com.example.swiftaid

import androidx.compose.runtime.staticCompositionLocalOf

data class SwiftAidRuntimeState(
    val statusMessage: String = "Preparing SwiftAid",
    val emergencyContacts: List<String> = emptyList(),
    val isMonitoring: Boolean = false,
    val requiredPermissionsReady: Boolean = false
) {
    val emergencyContactCount: Int
        get() = emergencyContacts.size
}

class SwiftAidRuntimeActions(
    val startManualSos: () -> Unit = {},
    val manageEmergencyContacts: () -> Unit = {},
    val refreshMonitoring: () -> Unit = {},
    val saveTypedEmergencyContact: (String) -> Unit = {}
)

val LocalSwiftAidRuntimeState = staticCompositionLocalOf { SwiftAidRuntimeState() }
val LocalSwiftAidRuntimeActions = staticCompositionLocalOf { SwiftAidRuntimeActions() }
