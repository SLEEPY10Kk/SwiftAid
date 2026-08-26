package com.example.policeapp

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object ModeSelection : NavKey

@Serializable
data object Login : NavKey

@Serializable
data object Main : NavKey

@Serializable
data class SosDetail(val requestId: String) : NavKey
