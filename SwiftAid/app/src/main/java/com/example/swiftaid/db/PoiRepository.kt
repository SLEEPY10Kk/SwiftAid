package com.example.swiftaid.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.example.swiftaid.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

class PoiRepository(context: Context) {
    private val dbHelper = PoiDbHelper(context.applicationContext)
    private val cacheTtlMs = 30 * 60 * 1000L

    suspend fun isCacheValid(): Boolean = withContext(Dispatchers.IO) {
        val lastCached = dbHelper.readableDatabase.rawQuery(
            "SELECT cached_at FROM pois ORDER BY cached_at DESC LIMIT 1",
            emptyArray()
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else null
        } ?: return@withContext false

        System.currentTimeMillis() - lastCached < cacheTtlMs
    }

    suspend fun getNearestPois(limit: Int = 5): List<PoiEntity> = withContext(Dispatchers.IO) {
        dbHelper.queryPois("SELECT * FROM pois ORDER BY COALESCE(eta_seconds, 2147483647), distance_m ASC LIMIT ?", arrayOf(limit.toString()))
    }

    suspend fun getAllPois(limit: Int = 80): List<PoiEntity> = withContext(Dispatchers.IO) {
        dbHelper.queryPois("SELECT * FROM pois ORDER BY COALESCE(eta_seconds, 2147483647), distance_m ASC LIMIT ?", arrayOf(limit.toString()))
    }

    suspend fun getNearestByEmergencyType(): Map<String, PoiEntity> = withContext(Dispatchers.IO) {
        EMERGENCY_TYPES.mapNotNull { type ->
            dbHelper.queryPois(
                "SELECT * FROM pois WHERE type = ? ORDER BY COALESCE(eta_seconds, 2147483647), distance_m ASC LIMIT 1",
                arrayOf(type)
            ).firstOrNull()?.let { type to it }
        }.toMap()
    }

    suspend fun syncFromServer(lat: Double, lon: Double) = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/nearby/merged?lat=$lat&lon=$lon")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 45_000

            val code = conn.responseCode
            if (code != 200) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Server returned HTTP $code")
                return@withContext
            }

            val json = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val pois = parsePoiArray(json)
            if (pois.isEmpty()) {
                if (BuildConfig.DEBUG) Log.w(TAG, "Server returned empty POI array")
                return@withContext
            }

            dbHelper.replaceAll(pois)
            if (BuildConfig.DEBUG) Log.d(TAG, "Saved ${pois.size} POIs to SQLite")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Sync failed: ${e.javaClass.simpleName} - ${e.message}")
            }
        }
    }

    suspend fun fetchCategoryPois(lat: Double, lon: Double, category: String, limit: Int = 80): List<PoiEntity> =
        withContext(Dispatchers.IO) {
            try {
                val type = URLEncoder.encode(normalizeType(category), "UTF-8")
                val url = URL("$BASE_URL/nearby/category?lat=$lat&lon=$lon&type=$type&limit=$limit")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 45_000

                val code = conn.responseCode
                if (code != 200) {
                    if (BuildConfig.DEBUG) Log.e(TAG, "Category server returned HTTP $code")
                    return@withContext emptyList()
                }

                val json = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                parsePoiArray(json)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "Category fetch failed: ${e.javaClass.simpleName} - ${e.message}")
                }
                emptyList()
            }
        }

    companion object {
        private const val TAG = "SwiftAidPoiRepository"
        private val BASE_URL = BuildConfig.API_BASE_URL.trimEnd('/')

        val TYPE_ALIASES = mapOf(
            "hospital" to "hospital",
            "clinic" to "hospital",
            "doctors" to "hospital",
            "healthcare" to "hospital",
            "HLTHSP" to "hospital",
            "health" to "hospital",
            "nursing_home" to "hospital",
            "medical_center" to "hospital",
            "police" to "police",
            "PLCSTN" to "police",
            "fire_station" to "fire_station",
            "FIRSTN" to "fire_station",
            "pharmacy" to "pharmacy",
            "chemist" to "pharmacy",
            "MEDST" to "pharmacy",
            "drugstore" to "pharmacy",
            "hospitals" to "hospital",
            "fuel" to "fuel",
            "gas_station" to "fuel",
            "PETROL" to "fuel",
            "car_repair" to "mechanic",
            "vehicle_repair" to "mechanic",
            "auto_repair" to "mechanic",
            "garage" to "mechanic",
            "mechanic" to "mechanic",
            "tyres" to "mechanic",
            "ambulance_station" to "ambulance",
            "fire" to "fire_station",
            "parking" to "parking",
            "local_government_office" to "government",
            "government" to "government",
            "townhall" to "government",
            "GOVOFF" to "government",
            "post_office" to "post_office",
            "POSOFF" to "post_office",
            "atm" to "atm",
            "ATM" to "atm",
            "bank" to "bank",
            "banks" to "bank",
            "BANK" to "bank",
            "post office" to "post_office",
        )

        val EMERGENCY_TYPES = setOf("hospital", "police", "fire_station", "pharmacy", "fuel", "mechanic", "ambulance", "parking", "atm", "bank", "post_office")

        fun normalizeType(raw: String): String =
            TYPE_ALIASES[raw.trim()] ?: raw.lowercase().trim()
    }

    private fun parsePoiArray(json: String): List<PoiEntity> {
        val arr = JSONArray(json)
        val now = System.currentTimeMillis()
        return buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val sources = obj.optJSONArray("sources")
                    ?.let { src -> (0 until src.length()).map { src.getString(it) }.joinToString(",") }
                    ?: "unknown"

                add(
                    PoiEntity(
                        name = obj.optString("name", "Unknown"),
                        address = obj.optString("address", ""),
                        lat = obj.optDouble("lat", 0.0),
                        lon = obj.optDouble("lon", 0.0),
                        type = normalizeType(
                            obj.optString(
                                "type",
                                obj.optString("amenity", obj.optString("category", "unknown"))
                            )
                        ),
                        distance_m = obj.optInt("distance_m", 0),
                        route_distance_m = obj.optIntOrNull("route_distance_m"),
                        eta_seconds = obj.optIntOrNull("eta_seconds"),
                        sources = sources,
                        cached_at = now,
                        phone = obj.optString("phone", "").ifEmpty { null }
                    )
                )
            }
        }
    }
}

private fun org.json.JSONObject.optIntOrNull(name: String): Int? =
    if (has(name) && !isNull(name)) optInt(name) else null

private class PoiDbHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS pois (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                address TEXT NOT NULL,
                lat REAL NOT NULL,
                lon REAL NOT NULL,
                type TEXT NOT NULL,
                sources TEXT NOT NULL,
                distance_m INTEGER NOT NULL,
                route_distance_m INTEGER,
                eta_seconds INTEGER,
                cached_at INTEGER NOT NULL,
                phone TEXT
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            runCatching { db.execSQL("ALTER TABLE pois ADD COLUMN phone TEXT") }
        }
        if (oldVersion < 3) {
            runCatching { db.execSQL("ALTER TABLE pois ADD COLUMN route_distance_m INTEGER") }
            runCatching { db.execSQL("ALTER TABLE pois ADD COLUMN eta_seconds INTEGER") }
        }
    }

    fun replaceAll(pois: List<PoiEntity>) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete("pois", null, null)
            pois.forEach { poi ->
                writableDatabase.insert(
                    "pois",
                    null,
                    ContentValues().apply {
                        put("name", poi.name)
                        put("address", poi.address)
                        put("lat", poi.lat)
                        put("lon", poi.lon)
                        put("type", poi.type)
                        put("sources", poi.sources)
                        put("distance_m", poi.distance_m)
                        put("route_distance_m", poi.route_distance_m)
                        put("eta_seconds", poi.eta_seconds)
                        put("cached_at", poi.cached_at)
                        put("phone", poi.phone)
                    }
                )
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun queryPois(sql: String, args: Array<String>): List<PoiEntity> {
        return readableDatabase.rawQuery(sql, args).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        PoiEntity(
                            id = cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                            name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                            address = cursor.getString(cursor.getColumnIndexOrThrow("address")),
                            lat = cursor.getDouble(cursor.getColumnIndexOrThrow("lat")),
                            lon = cursor.getDouble(cursor.getColumnIndexOrThrow("lon")),
                            type = cursor.getString(cursor.getColumnIndexOrThrow("type")),
                            sources = cursor.getString(cursor.getColumnIndexOrThrow("sources")),
                            distance_m = cursor.getInt(cursor.getColumnIndexOrThrow("distance_m")),
                            route_distance_m = cursor.getNullableInt("route_distance_m"),
                            eta_seconds = cursor.getNullableInt("eta_seconds"),
                            cached_at = cursor.getLong(cursor.getColumnIndexOrThrow("cached_at")),
                            phone = cursor.getString(cursor.getColumnIndexOrThrow("phone"))
                        )
                    )
                }
            }
        }
    }

    companion object {
        private const val DB_NAME = "roadsos_db"
        private const val DB_VERSION = 3
    }
}

private fun android.database.Cursor.getNullableInt(columnName: String): Int? {
    val index = getColumnIndex(columnName)
    return if (index >= 0 && !isNull(index)) getInt(index) else null
}
