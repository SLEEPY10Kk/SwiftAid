package com.example.swiftaidmobile.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [PoiEntity::class, CityPoiEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun poiDao(): PoiDao
    abstract fun cityPoiDao(): CityPoiDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pois ADD COLUMN phone TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS city_pois (
                        sourceId TEXT NOT NULL, 
                        osmId INTEGER NOT NULL, 
                        osmType TEXT NOT NULL, 
                        city TEXT NOT NULL, 
                        name TEXT NOT NULL, 
                        lat REAL NOT NULL, 
                        lon REAL NOT NULL, 
                        type TEXT NOT NULL, 
                        rawType TEXT NOT NULL, 
                        address TEXT NOT NULL, 
                        phone TEXT, 
                        openingHours TEXT, 
                        website TEXT, 
                        distanceM INTEGER NOT NULL, 
                        generatedAt INTEGER NOT NULL, 
                        PRIMARY KEY(sourceId)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_city_pois_type ON city_pois (type)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_city_pois_lat_lon ON city_pois (lat, lon)")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "roadsos_db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build().also { INSTANCE = it }
            }
    }
}
