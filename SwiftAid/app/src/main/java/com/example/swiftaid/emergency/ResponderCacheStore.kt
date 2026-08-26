package com.example.swiftaid.emergency

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONArray
import org.json.JSONObject

data class CachedResponderSelection(
    val responders: List<EmergencyResponderMatch>,
    val cachedAtMillis: Long?
)

object ResponderCacheStore {
    private const val TAG = "ResponderCacheStore"
    private const val PREFS_NAME = "swift_aid_responder_cache"
    private const val KEY_RESPONDERS = "responders"
    private const val KEY_CACHED_AT = "cached_at"
    private const val RESPONDER_RADIUS_METERS = 20_000.0

    fun refreshFromFirestore(context: Context) {
        val appContext = context.applicationContext
        FirebaseFirestore.getInstance()
            .collection("responders")
            .whereEqualTo("active", true)
            .get()
            .addOnSuccessListener { snapshot ->
                val responders = snapshot.documents.mapNotNull { document ->
                    val serviceType = document.getString("serviceType")?.uppercase().orEmpty()
                    val name = document.getString("name").orEmpty()
                    val phoneNumber = document.getString("phoneNumber").orEmpty()
                    val latitude = document.getDouble("latitude")
                    val longitude = document.getDouble("longitude")
                    if (
                        serviceType !in setOf("POLICE", "HOSPITAL") ||
                        name.isBlank() ||
                        phoneNumber.isBlank() ||
                        latitude == null ||
                        longitude == null
                    ) {
                        null
                    } else {
                        CachedResponder(
                            id = document.id,
                            serviceType = serviceType,
                            name = name,
                            phoneNumber = phoneNumber,
                            latitude = latitude,
                            longitude = longitude
                        )
                    }
                }
                save(appContext, responders)
                Log.d(TAG, "Cached ${responders.size} registered responders")
            }
            .addOnFailureListener { throwable ->
                Log.w(TAG, "Could not refresh responder cache", throwable)
            }
    }

    fun nearestForSms(
        context: Context,
        latitude: Double?,
        longitude: Double?,
        limitPerType: Int = 1
    ): CachedResponderSelection {
        val appContext = context.applicationContext
        val responders = read(appContext)
        val cachedAtMillis = appContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_CACHED_AT, 0L)
            .takeIf { it > 0L }

        if (latitude == null || longitude == null) {
            return CachedResponderSelection(emptyList(), cachedAtMillis)
        }

        val matches = responders
            .map { responder ->
                val distanceMeters = LiveEmergencyResponderDirectory.haversineMeters(
                    latitude,
                    longitude,
                    responder.latitude,
                    responder.longitude
                )
                EmergencyResponderMatch(
                    id = responder.id,
                    serviceType = responder.serviceType,
                    name = responder.name,
                    phoneNumber = responder.phoneNumber,
                    latitude = responder.latitude,
                    longitude = responder.longitude,
                    distanceMeters = distanceMeters,
                    routeUrl = routeUrl(latitude, longitude, responder.latitude, responder.longitude)
                )
            }
            .filter { it.distanceMeters <= RESPONDER_RADIUS_METERS }
            .groupBy { it.serviceType }
            .flatMap { (_, group) -> group.sortedBy { it.distanceMeters }.take(limitPerType) }
            .sortedWith(compareBy<EmergencyResponderMatch> { it.serviceType }.thenBy { it.distanceMeters })

        return CachedResponderSelection(matches, cachedAtMillis)
    }

    private fun save(context: Context, responders: List<CachedResponder>) {
        val array = JSONArray()
        responders.forEach { responder ->
            array.put(
                JSONObject().apply {
                    put("id", responder.id)
                    put("serviceType", responder.serviceType)
                    put("name", responder.name)
                    put("phoneNumber", responder.phoneNumber)
                    put("latitude", responder.latitude)
                    put("longitude", responder.longitude)
                }
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RESPONDERS, array.toString())
            .putLong(KEY_CACHED_AT, System.currentTimeMillis())
            .apply()
    }

    private fun read(context: Context): List<CachedResponder> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_RESPONDERS, "[]")
            .orEmpty()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val responder = CachedResponder(
                        id = item.optString("id"),
                        serviceType = item.optString("serviceType").uppercase(),
                        name = item.optString("name"),
                        phoneNumber = item.optString("phoneNumber"),
                        latitude = item.optDouble("latitude"),
                        longitude = item.optDouble("longitude")
                    )
                    if (
                        responder.id.isNotBlank() &&
                        responder.serviceType in setOf("POLICE", "HOSPITAL") &&
                        responder.name.isNotBlank() &&
                        responder.phoneNumber.isNotBlank()
                    ) {
                        add(responder)
                    }
                }
            }
        }.getOrElse {
            emptyList()
        }
    }

    private fun routeUrl(
        emergencyLat: Double,
        emergencyLng: Double,
        responderLat: Double,
        responderLng: Double
    ): String {
        return "https://www.google.com/maps/dir/?api=1" +
            "&origin=$responderLat,$responderLng" +
            "&destination=$emergencyLat,$emergencyLng" +
            "&travelmode=driving"
    }
}

private data class CachedResponder(
    val id: String,
    val serviceType: String,
    val name: String,
    val phoneNumber: String,
    val latitude: Double,
    val longitude: Double
)
