package com.lanceky.smartsenses.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Reading::class, ListeningSession::class, Event::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun readings(): ReadingDao
    abstract fun sessions(): SessionDao
    abstract fun events(): EventDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "smart-senses.db").addMigrations(MIGRATION_1_2).build()

        /** Eyes: events gain a signal plus brightness and room light. Existing rows stay and become EARS. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE events ADD COLUMN signal TEXT NOT NULL DEFAULT 'EARS'")
                db.execSQL("ALTER TABLE events ADD COLUMN brightnessPercent INTEGER")
                db.execSQL("ALTER TABLE events ADD COLUMN lux INTEGER")
            }
        }
    }
}
