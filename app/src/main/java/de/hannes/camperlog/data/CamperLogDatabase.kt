package de.hannes.camperlog.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** Lokale Room-Datenbank der App. */
@Database(entities = [TourEntity::class], version = 1, exportSchema = true)
abstract class CamperLogDatabase : RoomDatabase() {

    abstract fun tourDao(): TourDao

    companion object {
        /** Öffnet die Datenbankdatei der App. Nur einmal pro Prozess aufrufen. */
        fun open(context: Context): CamperLogDatabase =
            Room.databaseBuilder(context.applicationContext, CamperLogDatabase::class.java, "camperlog.db").build()
    }
}
