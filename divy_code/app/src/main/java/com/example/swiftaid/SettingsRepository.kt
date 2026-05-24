package com.example.swiftaid

class SettingsRepository(
    private val api: AuthApi
) {
    suspend fun load(accessToken: String): SettingsSnapshot =
        api.loadUserSettings(accessToken).toSnapshot()

    suspend fun saveUser(accessToken: String, request: UserUpdateRequest): SettingsSnapshot {
        api.updateUser(accessToken, request)
        return load(accessToken)
    }

    suspend fun createEmergencyContact(accessToken: String, request: EmergencyContactRequest): SettingsSnapshot {
        api.createEmergencyContact(accessToken, request)
        return load(accessToken)
    }

    suspend fun updateEmergencyContact(accessToken: String, contactId: String, request: EmergencyContactUpdateRequest): SettingsSnapshot {
        api.updateEmergencyContact(accessToken, contactId, request)
        return load(accessToken)
    }

    suspend fun deleteEmergencyContact(accessToken: String, contactId: String): SettingsSnapshot {
        api.deleteEmergencyContact(accessToken, contactId)
        return load(accessToken)
    }

    suspend fun createMedicalInfo(accessToken: String, request: MedicalInfoRequest): SettingsSnapshot {
        api.createMedicalInfo(accessToken, request)
        return load(accessToken)
    }

    suspend fun updateMedicalInfo(accessToken: String, request: MedicalInfoUpdateRequest): SettingsSnapshot {
        api.updateMedicalInfo(accessToken, request)
        return load(accessToken)
    }

    suspend fun deleteMedicalInfo(accessToken: String): SettingsSnapshot {
        api.deleteMedicalInfo(accessToken)
        return load(accessToken)
    }

    suspend fun createInsuranceInfo(accessToken: String, request: InsuranceInfoRequest): SettingsSnapshot {
        api.createInsuranceInfo(accessToken, request)
        return load(accessToken)
    }

    suspend fun updateInsuranceInfo(accessToken: String, insuranceId: String, request: InsuranceInfoUpdateRequest): SettingsSnapshot {
        api.updateInsuranceInfo(accessToken, insuranceId, request)
        return load(accessToken)
    }

    suspend fun deleteInsuranceInfo(accessToken: String, insuranceId: String): SettingsSnapshot {
        api.deleteInsuranceInfo(accessToken, insuranceId)
        return load(accessToken)
    }
}
