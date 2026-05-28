package com.example.swiftaidmobile.db

import androidx.room.*

@Dao
interface CityPoiDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(pois: List<CityPoiEntity>)

    @Query("DELETE FROM city_pois WHERE city = :city")
    suspend fun deleteByCity(city: String)

    @Query("SELECT * FROM city_pois WHERE type = :type")
    suspend fun getByType(type: String): List<CityPoiEntity>

    @Query("SELECT * FROM city_pois")
    suspend fun getAll(): List<CityPoiEntity>

    @Transaction
    suspend fun refreshCityPois(city: String, pois: List<CityPoiEntity>) {
        deleteByCity(city)
        insertAll(pois)
    }

    @Query("SELECT generatedAt FROM city_pois WHERE city = :city LIMIT 1")
    suspend fun getCityGeneratedAt(city: String): Long?
}
