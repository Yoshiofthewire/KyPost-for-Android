package com.urlxl.mail.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** suspend/Flow-based, matching the coroutine convention already used by push/PushRepository. */
@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY fn COLLATE NOCASE")
    fun observeAll(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE uid = :uid")
    suspend fun getByUid(uid: String): ContactEntity?

    @Upsert
    suspend fun upsertAll(contacts: List<ContactEntity>)

    @Query("DELETE FROM contacts WHERE uid IN (:uids)")
    suspend fun deleteByUids(uids: List<String>)

    @Query("DELETE FROM contacts")
    suspend fun clearAll()
}
