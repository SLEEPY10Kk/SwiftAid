package com.example.swiftaidmobile.db

import android.content.Context
import android.util.Log
import com.example.swiftaidmobile.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

class PoiRepository(context: Context) {

    private val dao = AppDatabase.getInstance(context).poiDao()
    private val CACHE_TTL_MS = 30 * 60 * 1000L  // 30 min

    companion object {
        private val SERVER_IP   = BuildConfig.SERVER_IP
        private const val SERVER_PORT = 8000
        private val BASE_URL    = "http://$SERVER_IP:$SERVER_PORT"

        // Canonical type map — normalizes all 3 API naming differences
        val TYPE_ALIASES = mapOf(
            // Medical
            "hospital"                to "hospital",
            "clinic"                  to "hospital",
            "doctors"                 to "hospital",
            "healthcare"              to "hospital",
            "HLTHSP"                  to "hospital",
            "health"                  to "hospital",
            "nursing_home"            to "hospital",
            "medical_center"          to "hospital",
            // Police
            "police"                  to "police",
            "PLCSTN"                  to "police",
            // Fire
            "fire_station"            to "fire_station",
            "FIRSTN"                  to "fire_station",
            // Pharmacy
            "pharmacy"                to "pharmacy",
            "chemist"                 to "pharmacy",
            "MEDST"                   to "pharmacy",
            "drugstore"               to "pharmacy",
            // Schools
            "school"                  to "school",
            "primary_school"          to "school",
            "secondary_school"        to "school",
            "SCHOOL"                  to "school",
            // Banks / ATM
            "bank"                    to "bank",
            "atm"                     to "bank",
            "BANK"                    to "bank",
            // Fuel
            "fuel"                    to "fuel",
            "gas_station"             to "fuel",
            "PETROL"                  to "fuel",
            // Government
            "local_government_office" to "government",
            "government"              to "government",
            "townhall"                to "government",
            "GOVOFF"                  to "government",
            // Post
            "post_office"             to "post_office",
            "POSOFF"                  to "post_office",
        )

        // Types surfaced in crash response and passed to Routes API
        val EMERGENCY_TYPES = setOf(
            "hospital",
            "police",
            "fire_station",
            "pharmacy",
            "fuel",
        )

        fun normalizeType(raw: String): String =
            TYPE_ALIASES[raw.trim()] ?: raw.lowercase().trim()
    }

    suspend fun isCacheValid(): Boolean {
        val lastCached = dao.getLastCachedAt() ?: return false
        return (System.currentTimeMillis() - lastCached) < CACHE_TTL_MS
    }

    suspend fun getNearestPois(limit: Int = 5): List<PoiEntity> =
        dao.getNearest(limit)

    /**
     * Returns one nearest POI per emergency type.
     * Each entry includes phone number if available.
     * This map is passed directly to the Routes API on crash.
     */
    suspend fun getNearestByEmergencyType(): Map<String, PoiEntity> {
        val result = mutableMapOf<String, PoiEntity>()
        EMERGENCY_TYPES.forEach { type ->
            dao.getNearestOfType(type)?.let { result[type] = it }
        }
        return result
    }

    suspend fun syncFromServer(lat: Double, lon: Double) = withContext(Dispatchers.IO) {
        try {
            val url  = URL("$BASE_URL/nearby/merged?lat=$lat&lon=$lon")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout    = 15_000

            val code = conn.responseCode
            if (code != 200) {
                if (BuildConfig.DEBUG) Log.e("RoadSOS", "Server returned HTTP $code")
                return@withContext
            }

            val json = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            if (BuildConfig.DEBUG) {
                Log.d("RoadSOS", "Response length: ${json.length}")
                Log.d("RoadSOS", "Response preview: ${json.take(300)}")
            }

            val arr = JSONArray(json)
            if (BuildConfig.DEBUG) Log.d("RoadSOS", "POIs in response: ${arr.length()}")

            if (arr.length() == 0) {
                if (BuildConfig.DEBUG) Log.w("RoadSOS", "Server returned empty array — check API keys and coordinates")
                return@withContext
            }

            val now  = System.currentTimeMillis()
            val pois = mutableListOf<PoiEntity>()

            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)

                val sources = obj.optJSONArray("sources")
                    ?.let { src -> (0 until src.length()).map { src.getString(it) }.joinToString(",") }
                    ?: "unknown"

                // Phone — present when Google or OSM supplied it, null otherwise
                val phone = obj.optString("phone", "").ifEmpty { null }

                pois.add(PoiEntity(
                    name       = obj.optString("name", "Unknown"),
                    address    = obj.optString("address", ""),
                    lat        = obj.optDouble("lat", 0.0),
                    lon        = obj.optDouble("lon", 0.0),
                    type       = normalizeType(
                                    obj.optString("type",
                                    obj.optString("amenity",
                                    obj.optString("category", "unknown")))
                                 ),
                    distance_m = obj.optInt("distance_m", 0),
                    sources    = sources,
                    cached_at  = now,
                    phone      = phone,
                ))
            }

            dao.clearAll()
            dao.insertAll(pois)

            if (BuildConfig.DEBUG) {
                Log.d("RoadSOS", "Saved ${pois.size} POIs to Room DB")
                val verify = dao.getNearest(5)
                Log.d("RoadSOS", "Verification read: ${verify.size} POIs in DB")
            }

        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e("RoadSOS", "Sync failed: ${e.javaClass.simpleName} — ${e.message}")
            }
        }
    }
}