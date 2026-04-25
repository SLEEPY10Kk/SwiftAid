package com.example.swiftaidmobile.db

import android.content.Context
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

class PoiRepository(context: Context) {

    private val dao = AppDatabase.getInstance(context).poiDao()
    private val CACHE_TTL_MS = 30 * 60 * 1000L  // 30 min

    suspend fun isCacheValid(): Boolean {
        val lastCached = dao.getLastCachedAt() ?: return false
        return (System.currentTimeMillis() - lastCached) < CACHE_TTL_MS
    }

    suspend fun getNearestPois(limit: Int = 5): List<PoiEntity> =
        dao.getNearest(limit)

    suspend fun syncFromServer(lat: Double, lon: Double) {
        try {
            val url = URL("http://192.168.5.108:8000/nearby/merged?lat=$lat&lon=$lon")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout    = 15_000

            if (conn.responseCode != 200) return

            val json     = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val arr      = JSONArray(json)
            val now      = System.currentTimeMillis()
            val pois     = mutableListOf<PoiEntity>()

            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val sources = obj.optJSONArray("sources")
                    ?.let { src -> (0 until src.length()).map { src.getString(it) }.joinToString(",") }
                    ?: "unknown"

                pois.add(PoiEntity(
                    name       = obj.optString("name", "Unknown"),
                    address    = obj.optString("address", ""),
                    lat        = obj.optDouble("lat", 0.0),
                    lon        = obj.optDouble("lon", 0.0),
                    type       = obj.optString("type",
                        obj.optString("amenity",
                            obj.optString("category", "unknown"))),
                    distance_m = obj.optInt("distance_m", 0),
                    sources    = sources,
                    cached_at  = now,
                ))
            }

            dao.clearAll()
            dao.insertAll(pois)

        } catch (e: Exception) {
            e.printStackTrace()  // silently fail — old cache stays
        }
    }
}