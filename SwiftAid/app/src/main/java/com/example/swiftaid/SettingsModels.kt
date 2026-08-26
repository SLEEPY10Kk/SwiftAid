package com.example.swiftaid

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserSettingsResponse(
    val user: UserResponse,
    @SerialName("emergency_contacts") val emergencyContacts: List<EmergencyContactResponse> = emptyList(),
    @SerialName("medical_info") val medicalInfo: List<MedicalInfoResponse> = emptyList(),
    @SerialName("insurance_info") val insuranceInfo: List<InsuranceInfoResponse> = emptyList()
)

@Serializable
data class UserUpdateRequest(
    val username: String? = null,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("phone_number") val phoneNumber: String? = null,
    val age: Int? = null,
    val gender: String? = null,
    val country: String? = null,
    val city: String? = null,
    val state: String? = null,
    val area: String? = null
)

@Serializable
data class EmergencyContactUpdateRequest(
    @SerialName("contact_name") val contactName: String? = null,
    @SerialName("contact_number") val contactNumber: String? = null,
    val relationship: String? = null,
    @SerialName("priority_order") val priorityOrder: Int? = null
)

@Serializable
data class MedicalInfoUpdateRequest(
    @SerialName("bloodGroup") val bloodGroup: String? = null,
    val allergies: String? = null,
    @SerialName("chronicConditions") val chronicConditions: String? = null,
    @SerialName("currentmedications") val currentMedications: String? = null
)

@Serializable
data class InsuranceInfoUpdateRequest(
    @SerialName("insurance_type") val insuranceType: String? = null,
    @SerialName("insurance_provider") val insuranceProvider: String? = null,
    @SerialName("insurance_policy_number") val insurancePolicyNumber: String? = null,
    @SerialName("policy_holder_name") val policyHolderName: String? = null,
    @SerialName("coverage_type") val coverageType: String? = null,
    @SerialName("expiry_date") val expiryDate: String? = null,
    @SerialName("coverage_amount") val coverageAmount: String? = null,
    @SerialName("document_uri") val documentUri: String? = null
)

data class SettingsSnapshot(
    val user: UserResponse? = null,
    val emergencyContacts: List<EmergencyContactResponse> = emptyList(),
    val medicalInfo: MedicalInfoResponse? = null,
    val insuranceInfo: List<InsuranceInfoResponse> = emptyList()
)

val SettingsSnapshot.hasBasicProfile: Boolean
    get() = user?.isComplete == true

val SettingsSnapshot.hasEmergencyContacts: Boolean
    get() = emergencyContacts.isNotEmpty()

val SettingsSnapshot.hasMedicalInfo: Boolean
    get() = medicalInfo != null

val SettingsSnapshot.hasInsuranceInfo: Boolean
    get() = insuranceInfo.isNotEmpty()

fun UserSettingsResponse.toSnapshot(): SettingsSnapshot = SettingsSnapshot(
    user = user,
    emergencyContacts = emergencyContacts,
    medicalInfo = medicalInfo.firstOrNull(),
    insuranceInfo = insuranceInfo
)

fun SettingsSnapshot.toSafetyCompletion(): SafetyCompletion = SafetyCompletion(
    basicComplete = hasBasicProfile,
    emergencyComplete = hasEmergencyContacts,
    medicalComplete = hasMedicalInfo,
    insuranceComplete = hasInsuranceInfo
)

fun AppSharedState.applySettingsSnapshot(snapshot: SettingsSnapshot) {
    settingsSnapshot = snapshot
    settingsLoading = false
    settingsError = ""

    snapshot.user?.let { user ->
        userId = user.id
        username = user.username
        fullName = user.fullName ?: "${user.firstName} ${user.lastName}".trim()
        phone = user.phoneNumber
        age = user.age?.toString().orEmpty()
        gender = user.gender.orEmpty()
        country = user.country.orEmpty()
        city = user.city.orEmpty()
        state = user.state.orEmpty()
        exactArea = user.area.orEmpty()
    }

    emergencyContactName = snapshot.emergencyContacts.firstOrNull()?.contactName.orEmpty()
    emergencyContactPhone = snapshot.emergencyContacts.firstOrNull()?.contactNumber.orEmpty()

    if (snapshot.medicalInfo == null) {
        bloodGroup = ""
        allergies = ""
        chronicConditions = ""
        medicalHistory = ""
    } else {
        snapshot.medicalInfo.let { medical ->
            bloodGroup = medical.bloodGroup
            allergies = medical.allergies
            chronicConditions = medical.chronicConditions
            medicalHistory = medical.chronicConditions
        }
    }

    insurances.clear()
    snapshot.insuranceInfo.forEach { insurance ->
        insurances.add(
            Insurance(
                id = insurance.insuranceId,
                provider = insurance.insuranceProvider,
                policyNumber = insurance.insurancePolicyNumber,
                policyHolderName = insurance.policyHolderName.orEmpty(),
                coverageType = insurance.coverageType.orEmpty(),
                expiryDate = insurance.expiryDate.orEmpty(),
                coverageAmount = insurance.coverageAmount.orEmpty()
            )
        )
    }

    safetyProfilePercent = snapshot.toSafetyCompletion().percent
    isSafetyProfileComplete = safetyProfilePercent == 100
}

fun AppSharedState.clearSessionData() {
    username = ""
    fullName = ""
    phone = ""
    password = ""
    city = ""
    state = ""
    country = ""
    exactArea = ""
    age = ""
    gender = ""
    dob = ""
    residentialAddress = ""
    emergencyContactName = ""
    emergencyContactPhone = ""
    bloodGroup = ""
    allergies = ""
    chronicConditions = ""
    medicalHistory = ""
    isReportAdded = false
    reportUri = null
    medicalHistoryDocuments.clear()
    insurances.clear()
    claims.clear()
    settingsSnapshot = null
    settingsLoading = false
    settingsError = ""
    isSafetyProfileComplete = false
    safetyProfilePercent = 0
    accessToken = ""
    refreshToken = ""
    userId = null
}

enum class BasicProfileField(
    val label: String
) {
    Username("Username"),
    FullName("Full name"),
    Phone("Phone number"),
    Country("Country"),
    State("State"),
    City("City"),
    Area("Area")
}

enum class MedicalField(
    val label: String
) {
    BloodGroup("Blood group"),
    Allergies("Allergies"),
    ChronicConditions("Chronic conditions"),
    CurrentMedications("Current medications")
}

sealed interface SettingsEditorState {
    data class BasicFieldEditor(
        val field: BasicProfileField,
        val currentValue: String
    ) : SettingsEditorState

    data class MedicalFieldEditor(
        val field: MedicalField,
        val currentValue: String
    ) : SettingsEditorState

    data class EmergencyContactEditor(
        val contact: EmergencyContactResponse? = null
    ) : SettingsEditorState

    data class InsuranceEditor(
        val insurance: InsuranceInfoResponse? = null
    ) : SettingsEditorState
}

fun SettingsSnapshot.basicFieldValue(field: BasicProfileField): String = when (field) {
    BasicProfileField.Username -> user?.username.orEmpty()
    BasicProfileField.FullName -> user?.fullName?.takeIf { it.isNotBlank() }
        ?: listOfNotNull(user?.firstName, user?.lastName).joinToString(" ").trim()
    BasicProfileField.Phone -> user?.phoneNumber.orEmpty()
    BasicProfileField.Country -> user?.country.orEmpty()
    BasicProfileField.State -> user?.state.orEmpty()
    BasicProfileField.City -> user?.city.orEmpty()
    BasicProfileField.Area -> user?.area.orEmpty()
}

fun SettingsSnapshot.medicalFieldValue(field: MedicalField): String {
    val medical = medicalInfo ?: return ""
    return when (field) {
        MedicalField.BloodGroup -> medical.bloodGroup
        MedicalField.Allergies -> medical.allergies
        MedicalField.ChronicConditions -> medical.chronicConditions
        MedicalField.CurrentMedications -> medical.currentmedications
    }
}
