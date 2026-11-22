package com.blackeye.kribo.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [FolderEntity::class, NoteEntity::class, AttachmentEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class KriboDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun folderDao(): FolderDao
    abstract fun attachmentDao(): AttachmentDao

    companion object {
        private const val DATABASE_NAME = "kribo.db"

        fun create(context: Context): KriboDatabase =
            Room.databaseBuilder(context.applicationContext, KriboDatabase::class.java, DATABASE_NAME)
                .addMigrations(MIGRATION_1_2)
                .addCallback(SeedFoldersCallback)
                .build()

        private object SeedFoldersCallback : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                seedDefaultFolders(db)
            }
        }
    }
}
