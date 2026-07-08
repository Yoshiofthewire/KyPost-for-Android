package com.urlxl.mail.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Mirrors the `Contact` JSON shape in Mobile_Contact_Sync.md. [emailsJson]/[phonesJson]/
 * [addressesJson] hold pre-encoded kotlinx.serialization JSON for the field-entry lists — plain
 * String columns rather than a TypeConverter, since callers already have a Json instance handy
 * from decoding the sync response.
 */
@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val uid: String,
    val rev: Long,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val fn: String,
    val givenName: String? = null,
    val familyName: String? = null,
    val middleName: String? = null,
    val prefix: String? = null,
    val suffix: String? = null,
    val nickname: String? = null,
    val org: String? = null,
    val title: String? = null,
    val notes: String? = null,
    val birthday: String? = null,
    val emailsJson: String = "[]",
    val phonesJson: String = "[]",
    val addressesJson: String = "[]",
)
