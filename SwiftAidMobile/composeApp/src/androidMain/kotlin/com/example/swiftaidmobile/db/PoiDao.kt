package com.example.swiftaidmobile.db

import androidx.room.*

@Dao
interface PoiDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(pois: List<PoiEntity>)

    @Query("SELECT * FROM pois ORDER BY distance_m ASC")
    suspend fun getAll(): List<PoiEntity>

    @Query("SELECT * FROM pois ORDER BY distance_m ASC LIMIT :limit")
    suspend fun getNearest(limit: Int): List<PoiEntity>

    @Query("SELECT * FROM pois WHERE type = :type ORDER BY distance_m ASC")
    suspend fun getByType(type: String): List<PoiEntity>

    @Query("DELETE FROM pois")
    suspend fun clearAll()

    @Query("SELECT cached_at FROM pois ORDER BY cached_at DESC LIMIT 1")
    suspend fun getLastCachedAt(): Long?
}