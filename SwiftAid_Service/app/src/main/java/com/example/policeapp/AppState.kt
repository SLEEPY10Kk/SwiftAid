package com.example.policeapp

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.example.policeapp.data.model.ResponderProfile

object AppState {
    var selectedMode by mutableStateOf<AppMode?>(null)
    var currentResponder by mutableStateOf<ResponderProfile?>(null)

    fun selectMode(mode: AppMode) {
        selectedMode = mode
    }

    fun setResponder(profile: ResponderProfile) {
        currentResponder = profile
        selectedMode = profile.appMode ?: selectedMode
    }

    fun resetMode() {
        selectedMode = null
        currentResponder = null
    }
}

enum class AppMode {
    POLICE,
    HOSPITAL
}
