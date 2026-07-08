package com.urlxl.mail.contacts

import com.urlxl.mail.data.ContactEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val mapperJson = Json { ignoreUnknownKeys = true }

fun ContactDto.toEntity(): ContactEntity = ContactEntity(
    uid = uid,
    rev = rev,
    createdAt = createdAt,
    updatedAt = updatedAt,
    fn = fn,
    givenName = givenName,
    familyName = familyName,
    middleName = middleName,
    prefix = prefix,
    suffix = suffix,
    nickname = nickname,
    org = org,
    title = title,
    notes = notes,
    birthday = birthday,
    emailsJson = mapperJson.encodeToString(emails),
    phonesJson = mapperJson.encodeToString(phones),
    addressesJson = mapperJson.encodeToString(addresses),
)

fun ContactEntity.toDto(): ContactDto = ContactDto(
    uid = uid,
    rev = rev,
    createdAt = createdAt,
    updatedAt = updatedAt,
    fn = fn,
    givenName = givenName,
    familyName = familyName,
    middleName = middleName,
    prefix = prefix,
    suffix = suffix,
    nickname = nickname,
    org = org,
    title = title,
    notes = notes,
    birthday = birthday,
    emails = runCatching { mapperJson.decodeFromString<List<ContactFieldDto>>(emailsJson) }.getOrDefault(emptyList()),
    phones = runCatching { mapperJson.decodeFromString<List<ContactFieldDto>>(phonesJson) }.getOrDefault(emptyList()),
    addresses = runCatching { mapperJson.decodeFromString<List<ContactAddressDto>>(addressesJson) }.getOrDefault(emptyList()),
)
