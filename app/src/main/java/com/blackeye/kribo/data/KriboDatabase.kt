package com.blackeye.kribo.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Note::class],
    version = 1,
    exportSchema = true,
)
abstract class KriboDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    companion object {
        private const val DATABASE_NAME = "kribo.db"

        fun create(context: Context): KriboDatabase =
            Room.databaseBuilder(context.applicationContext, KriboDatabase::class.java, DATABASE_NAME)
                .build()
    }
}
