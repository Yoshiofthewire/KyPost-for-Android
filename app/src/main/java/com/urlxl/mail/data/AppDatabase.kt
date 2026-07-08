package com.urlxl.mail.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        EmailEntity::class,
        FolderEntity::class,
        ContactEntity::class,
        PendingContactChangeEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun emailDao(): EmailDao
    abstract fun folderDao(): FolderDao
    abstract fun contactDao(): ContactDao
    abstract fun pendingContactChangeDao(): PendingContactChangeDao
}
