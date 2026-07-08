package com.urlxl.mail.contacts

import kotlinx.serialization.Serializable

/** Matches Mobile_Contact_Sync.md's Contact JSON shape exactly. */
@Serializable
data class ContactDto(
    val uid: String = "",
    val rev: Long = 0,
    val deleted: Boolean = false,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val fn: String = "",
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
    val emails: List<ContactFieldDto> = emptyList(),
    val phones: List<ContactFieldDto> = emptyList(),
    val addresses: List<ContactAddressDto> = emptyList(),
)

@Serializable
data class ContactFieldDto(
    val label: String? = null,
    val value: String = "",
)

@Serializable
data class ContactAddressDto(
    val label: String? = null,
    val street: String? = null,
    val city: String? = null,
    val region: String? = null,
    val postalCode: String? = null,
    val country: String? = null,
)

@Serializable
data class ContactSyncPullResponseDto(
    val cursor: Long = 0,
    val tooOld: Boolean = false,
    val changed: List<ContactDto> = emptyList(),
    val deleted: List<ContactDto> = emptyList(),
)

@Serializable
data class ContactSyncPushRequestDto(
    val baseCursor: Long,
    val changes: List<ContactDto>,
)
