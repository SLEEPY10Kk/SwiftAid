package com.example.policeapp.firebase

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.policeapp.AppMode
import com.example.policeapp.data.model.ResponderProfile
import com.example.policeapp.data.model.SosEventData
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.Locale

/**
 * Firebase repository for PoliceApp to listen to real-time SOS events
 */
class FirebasePoliceRepository {
    
    private val firestore = FirebaseFirestore.getInstance()
    private val _activeSosEvents = MutableLiveData<List<SosEventData>>()
    val activeSosEvents: LiveData<List<SosEventData>> = _activeSosEvents
    private val _completedSosEvents = MutableLiveData<List<SosEventData>>()
    val completedSosEvents: LiveData<List<SosEventData>> = _completedSosEvents
    
    private val _sosEventUpdates = MutableLiveData<SosEventData>()
    val sosEventUpdates: LiveData<SosEventData> = _sosEventUpdates
    
    private var sosEventsListener: ListenerRegistration? = null
    private var completedSosEventsListener: ListenerRegistration? = null

    suspend fun loginOrRegisterResponder(
        mode: AppMode,
        serviceCode: String,
        password: String,
        serviceName: String,
        phoneNumber: String,
        address: String,
        latitude: Double,
        longitude: Double
    ): Result<ResponderProfile> = withContext(Dispatchers.IO) {
        try {
            val id = serviceCode.trim().uppercase(Locale.US).replace(Regex("[^A-Z0-9_-]"), "-")
            if (id.isBlank()) return@withContext Result.failure(IllegalArgumentException("Service code is required"))
            if (password.isBlank()) return@withContext Result.failure(IllegalArgumentException("Password is required"))
            if (serviceName.isBlank()) return@withContext Result.failure(IllegalArgumentException("Service name is required"))
            if (phoneNumber.isBlank()) return@withContext Result.failure(IllegalArgumentException("Phone number is required"))
            if (latitude == 0.0 && longitude == 0.0) {
                return@withContext Result.failure(IllegalArgumentException("Set service latitude and longitude"))
            }

            val serviceType = when (mode) {
                AppMode.POLICE -> "POLICE"
                AppMode.HOSPITAL -> "HOSPITAL"
            }
            val docRef = firestore.collection("responders").document(id)
            val snapshot = docRef.get().await()
            val fcmToken = runCatching { FirebaseMessaging.getInstance().token.await() }.getOrDefault("")
            val passwordHash = hashPassword(id, password)

            if (snapshot.exists()) {
                val storedHash = snapshot.getString("passwordHash").orEmpty()
                if (storedHash != passwordHash) {
                    return@withContext Result.failure(IllegalArgumentException("Incorrect service password"))
                }
                val storedType = snapshot.getString("serviceType").orEmpty()
                if (storedType.isNotBlank() && storedType != serviceType) {
                    return@withContext Result.failure(IllegalArgumentException("This service code is registered as $storedType"))
                }
                val updates = mapOf(
                    "name" to serviceName.trim(),
                    "phoneNumber" to phoneNumber.trim(),
                    "address" to address.trim(),
                    "latitude" to latitude,
                    "longitude" to longitude,
                    "serviceType" to serviceType,
                    "active" to true,
                    "fcmToken" to fcmToken,
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "lastLoginAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updates).await()
            } else {
                docRef.set(
                    mapOf(
                        "serviceType" to serviceType,
                        "name" to serviceName.trim(),
                        "phoneNumber" to phoneNumber.trim(),
                        "address" to address.trim(),
                        "latitude" to latitude,
                        "longitude" to longitude,
                        "active" to true,
                        "passwordHash" to passwordHash,
                        "fcmToken" to fcmToken,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                        "lastLoginAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            Result.success(
                ResponderProfile(
                    id = id,
                    serviceType = serviceType,
                    name = serviceName.trim(),
                    phoneNumber = phoneNumber.trim(),
                    address = address.trim(),
                    latitude = latitude,
                    longitude = longitude,
                    active = true,
                    fcmToken = fcmToken
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Responder login/register failed", e)
            Result.failure(e)
        }
    }
    
    /**
     * Start listening to active SOS events
     */
    fun startListeningToSosEvents(responder: ResponderProfile?) {
        sosEventsListener?.remove()
        completedSosEventsListener?.remove()
        sosEventsListener = firestore.collection("sos_events")
            .whereEqualTo("status", "ACTIVE")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to SOS events", error)
                    return@addSnapshotListener
                }
                
                val events = snapshot?.documents
                    ?.filter { doc -> doc.isRelevantTo(responder) }
                    ?.mapNotNull { doc -> doc.toObject(SosEventData::class.java) }
                    ?.sortedByDescending { event ->
                    event.createdAt?.time ?: 0L
                } ?: emptyList()
                
                _activeSosEvents.postValue(events)
                Log.d(TAG, "Received ${events.size} active SOS events")
            }

        completedSosEventsListener = firestore.collection("sos_events")
            .whereEqualTo("status", "COMPLETED")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to completed SOS events", error)
                    return@addSnapshotListener
                }

                val events = snapshot?.documents
                    ?.filter { doc -> doc.isRelevantTo(responder) }
                    ?.mapNotNull { doc -> doc.toObject(SosEventData::class.java) }
                    ?.sortedByDescending { event ->
                        event.completedAt?.time ?: event.updatedAt?.time ?: 0L
                    } ?: emptyList()

                _completedSosEvents.postValue(events)
            }
    }
    
    /**
     * Listen to specific SOS event updates
     */
    fun listenToSosEvent(sosId: String): ListenerRegistration {
        return firestore.collection("sos_events").document(sosId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to SOS event: $sosId", error)
                    return@addSnapshotListener
                }
                
                snapshot?.toObject(SosEventData::class.java)?.let {
                    _sosEventUpdates.postValue(it)
                    Log.d(TAG, "SOS event updated: $sosId - Status: ${it.status}")
                }
            }
    }
    
    /**
     * Get SOS event by ID
     */
    suspend fun getSosEventById(sosId: String): Result<SosEventData?> = withContext(Dispatchers.IO) {
        try {
            val doc = firestore.collection("sos_events").document(sosId).get().await()
            val sosEvent = doc.toObject(SosEventData::class.java)
            Result.success(sosEvent)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting SOS event: $sosId", e)
            Result.failure(e)
        }
    }
    
    /**
     * Accept SOS event assignment
     */
    suspend fun acceptSosEvent(
        sosId: String,
        responderId: String,
        responderRole: String = "POLICE",
        responderName: String = "",
        responderPhone: String = "",
        estimatedTimeMinutes: Int = 0
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val serviceType = responderRole.trim().uppercase(Locale.US)
            val responseField = when (serviceType) {
                "POLICE" -> "policeResponse"
                "HOSPITAL" -> "hospitalResponse"
                else -> return@withContext Result.failure(IllegalArgumentException("Unsupported responder role: $responderRole"))
            }
            val cleanResponderId = responderId.trim()
            if (cleanResponderId.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Responder ID is required"))
            }

            val sosRef = firestore.collection("sos_events").document(sosId)
            val responseRef = firestore
                .collection("sos_responses")
                .document("${sosId}_$serviceType")

            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(sosRef)
                if (!snapshot.exists()) {
                    throw IllegalArgumentException("SOS request not found")
                }

                val existingResponse = snapshot.get(responseField) as? Map<*, *>
                val existingResponderId = existingResponse
                    ?.get("responderId")
                    ?.toString()
                    .orEmpty()
                    .trim()
                if (existingResponderId.isNotBlank()) {
                    throw SosAlreadyHandledException(serviceType, existingResponderId)
                }

                val serviceResponse = mapOf(
                    "status" to "ACCEPTED",
                    "responderId" to cleanResponderId,
                    "responderName" to responderName.trim(),
                    "responderPhone" to responderPhone.trim(),
                    "etaMinutes" to estimatedTimeMinutes,
                    "acceptedAt" to FieldValue.serverTimestamp()
                )

                transaction.update(
                    sosRef,
                    mapOf(
                        responseField to serviceResponse,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                transaction.set(
                    responseRef,
                    mapOf(
                        "sosEventId" to sosId,
                        "serviceType" to serviceType,
                        "responderId" to cleanResponderId,
                        "responderRole" to serviceType,
                        "responderName" to responderName.trim(),
                        "responderPhone" to responderPhone.trim(),
                        "status" to "ACCEPTED",
                        "estimatedTimeMinutes" to estimatedTimeMinutes,
                        "respondedAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                        "createdAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
            }.await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error accepting SOS event: $sosId", e)
            Result.failure(e)
        }
    }
    
    /**
     * Complete SOS event
     */
    suspend fun completeSosEvent(sosId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            firestore.collection("sos_events").document(sosId).update(mapOf(
                "status" to "COMPLETED",
                "completedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )).await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error completing SOS event: $sosId", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get nearby SOS events
     */
    suspend fun getNearbyActiveSosEvents(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double = 5000.0
    ): Result<List<SosEventData>> = withContext(Dispatchers.IO) {
        try {
            val snapshot = firestore.collection("sos_events")
                .whereEqualTo("status", "ACTIVE")
                .get()
                .await()
            
            val events = snapshot.documents.mapNotNull { doc ->
                doc.toObject(SosEventData::class.java)
            }.filter { event ->
                val dx = event.latitude - latitude
                val dy = event.longitude - longitude
                val distance = Math.sqrt((dx * dx + dy * dy).toDouble()) * 111000 // rough approximation
                distance <= radiusMeters
            }.sortedBy { event ->
                val dx = event.latitude - latitude
                val dy = event.longitude - longitude
                Math.sqrt((dx * dx + dy * dy).toDouble())
            }
            
            Result.success(events)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting nearby SOS events", e)
            Result.failure(e)
        }
    }
    
    /**
     * Stop listening to SOS events
     */
    fun stopListeningToSosEvents() {
        sosEventsListener?.remove()
        sosEventsListener = null
        completedSosEventsListener?.remove()
        completedSosEventsListener = null
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.isRelevantTo(responder: ResponderProfile?): Boolean {
        val profile = responder ?: return false
        val responderId = profile.id.takeIf { it.isNotBlank() } ?: return false

        val targets = get("targetResponderIds") as? List<*> ?: return false
        return targets.any { it?.toString()?.equals(responderId, ignoreCase = true) == true }
    }

    private fun hashPassword(serviceCode: String, password: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest("$serviceCode:$password".toByteArray())
            .joinToString(separator = "") { "%02x".format(it) }
    }
    
    companion object {
        private const val TAG = "FirebasePoliceRepo"
    }
}

class SosAlreadyHandledException(
    serviceType: String,
    responderId: String
) : IllegalStateException("$serviceType SOS response already handled by $responderId")
