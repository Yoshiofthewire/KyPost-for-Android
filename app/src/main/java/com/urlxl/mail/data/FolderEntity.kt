package com.urlxl.mail.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val path: String,
    val parent: String = "",
    val deletable: Boolean = true,
    val sourceMode: String,
)
