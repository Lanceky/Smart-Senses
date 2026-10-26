package com.lanceky.smartsenses.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Reading::class, ListeningSession::class, Event::class, BrightnessReading::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun readings(): ReadingDao
    abstract fun sessions(): SessionDao
    abstract fun events(): EventDao
    abstract fun brightness(): BrightnessDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "smart-senses.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()

        /** Eyes: events gain a signal plus brightness and room light. Existing rows stay and become EARS. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE events ADD COLUMN signal TEXT NOT NULL DEFAULT 'EARS'")
                db.execSQL("ALTER TABLE events ADD COLUMN brightnessPercent INTEGER")
                db.execSQL("ALTER TABLE events ADD COLUMN lux INTEGER")
            }
        }

        /** Trends: brightness history. A new empty table; nothing existing changes. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `brightness_readings` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`time` INTEGER NOT NULL, `epochDay` INTEGER NOT NULL, `brightnessPercent` INTEGER NOT NULL, " +
                        "`lux` INTEGER, `screenOnMs` INTEGER NOT NULL, `brightInDimMs` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_brightness_readings_epochDay` ON `brightness_readings` (`epochDay`)")
            }
        }
    }
}
