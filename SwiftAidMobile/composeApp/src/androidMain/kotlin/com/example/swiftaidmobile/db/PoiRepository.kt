package com.example.swiftaidmobile.db

import android.content.Context
import android.location.Location
import android.util.Log
import com.example.swiftaidmobile.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

class PoiRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val dao = db.poiDao()
    private val cityDao = db.cityPoiDao()
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
            // Mechanic
            "car_repair"              to "mechanic",
            "mechanic"                to "mechanic"
        )

        // Types surfaced in crash response and passed to Routes API
        val EMERGENCY_TYPES = setOf(
            "hospital",
            "police",
            "fire_station",
            "pharmacy",
            "fuel",
            "mechanic"
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
    suspend fun getNearestByEmergencyType(userLat: Double? = null, userLon: Double? = null): Map<String, PoiEntity> {
        val result = mutableMapOf<String, PoiEntity>()
        
        // 1. Try local transient cache first (synced nearby)
        EMERGENCY_TYPES.forEach { type ->
            dao.getNearestOfType(type)?.let { result[type] = it }
        }

        // 2. If we have coordinates, check city offline database for gaps
        if (userLat != null && userLon != null) {
            EMERGENCY_TYPES.forEach { type ->
                if (!result.containsKey(type)) {
                    val cityPois = cityDao.getByType(type)
                    val nearest = cityPois.minByOrNull { poi ->
                        val res = FloatArray(1)
                        Location.distanceBetween(userLat, userLon, poi.lat, poi.lon, res)
                        res[0]
                    }
                    nearest?.let {
                        val res = FloatArray(1)
                        Location.distanceBetween(userLat, userLon, it.lat, it.lon, res)
                        result[type] = PoiEntity(
                            name = it.name,
                            address = it.address,
                            lat = it.lat,
                            lon = it.lon,
                            type = it.type,
                            sources = "osm_offline",
                            distance_m = res[0].toInt(),
                            cached_at = it.generatedAt,
                            phone = it.phone
                        )
                    }
                }
            }
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

            val arr = JSONArray(json)
            if (arr.length() == 0) return@withContext

            val now  = System.currentTimeMillis()
            val pois = mutableListOf<PoiEntity>()

            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val sources = obj.optJSONArray("sources")
                    ?.let { src -> (0 until src.length()).map { src.getString(it) }.joinToString(",") }
                    ?: "unknown"

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

        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e("RoadSOS", "Sync failed: ${e.message}")
        }
    }

    suspend fun syncCityOffline(city: String, lat: Double, lon: Double) = withContext(Dispatchers.IO) {
        try {
            if (BuildConfig.DEBUG) Log.d("RoadSOS", "Syncing city offline POIs for $city...")
            
            // 1. Export request
            val exportUrl = URL("$BASE_URL/city/osm-pois/export?city=$city&lat=$lat&lon=$lon")
            val conn = exportUrl.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 30_000
            
            if (conn.responseCode != 200) {
                if (BuildConfig.DEBUG) Log.e("RoadSOS", "Export failed: ${conn.responseCode}")
                return@withContext
            }
            
            val exportResponse = JSONObject(conn.inputStream.bufferedReader().readText())
            conn.disconnect()
            
            val downloadUrl = exportResponse.getString("download_url")
            val count = exportResponse.getInt("count")
            val generatedAt = exportResponse.getLong("created_at")
            
            // Skip if already current
            val localGeneratedAt = cityDao.getCityGeneratedAt(city)
            if (localGeneratedAt != null && localGeneratedAt >= generatedAt) {
                if (BuildConfig.DEBUG) Log.d("RoadSOS", "City POIs already up to date for $city")
                return@withContext
            }
            
            // 2. Download gzip
            val downloadConn = URL(downloadUrl).openConnection() as HttpURLConnection
            if (downloadConn.responseCode != 200) return@withContext
            
            val gzipStream = GZIPInputStream(downloadConn.inputStream)
            val json = gzipStream.bufferedReader().readText()
            downloadConn.disconnect()
            
            // 3. Parse and import
            val root = JSONObject(json)
            val poisArr = root.getJSONArray("pois")
            val cityPois = mutableListOf<CityPoiEntity>()
            
            for (i in 0 until poisArr.length()) {
                val obj = poisArr.getJSONObject(i)
                cityPois.add(CityPoiEntity(
                    sourceId = obj.getString("source_id"),
                    osmId = obj.getLong("osm_id"),
                    osmType = obj.getString("osm_type"),
                    city = city,
                    name = obj.getString("name"),
                    lat = obj.getDouble("lat"),
                    lon = obj.getDouble("lon"),
                    type = normalizeType(obj.getString("type")),
                    rawType = obj.getString("raw_type"),
                    address = obj.getString("address"),
                    phone = obj.optString("phone", "").ifEmpty { null },
                    openingHours = obj.optString("opening_hours", "").ifEmpty { null },
                    website = obj.optString("website", "").ifEmpty { null },
                    distanceM = obj.getInt("distance_m"),
                    generatedAt = generatedAt
                ))
            }
            
            cityDao.refreshCityPois(city, cityPois)
            if (BuildConfig.DEBUG) Log.d("RoadSOS", "Imported ${cityPois.size} city POIs for $city")
            
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e("RoadSOS", "City sync failed: ${e.message}")
        }
    }
}
