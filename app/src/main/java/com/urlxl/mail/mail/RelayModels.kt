package com.urlxl.mail.mail

import kotlinx.serialization.Serializable

/** DTOs matching Mobile_Mail_Relay.md's JSON exactly. */
@Serializable
data class RelayEmailDto(
    val messageId: String = "",
    val sender: String = "",
    val sentTo: String = "",
    val cc: String = "",
    val bcc: String = "",
    val subject: String = "",
    val body: String = "",
    val label: String = "",
    val status: String = "unread",
    val atUtc: String? = null,
)

@Serializable
data class RelayInboxResponseDto(
    val tabs: List<String> = emptyList(),
    val byTab: Map<String, List<RelayEmailDto>> = emptyMap(),
)

@Serializable
data class RelayFolderDto(val path: String, val deletable: Boolean = true)

@Serializable
data class RelayFolderListResponseDto(val parent: String = "", val folders: List<RelayFolderDto> = emptyList())

@Serializable
data class RelayFolderCreateRequestDto(val parent: String, val name: String)

@Serializable
data class RelayFolderRenameRequestDto(val folder: String, val name: String)

@Serializable
data class RelayActionRequestDto(
    val action: String,
    val messageIds: List<String>,
    val mailbox: String,
    val targetMailbox: String? = null,
)

@Serializable
data class RelayActionFailureDto(val messageId: String = "", val error: String = "")

@Serializable
data class RelayActionResponseDto(
    val ok: Boolean = false,
    val action: String = "",
    val processed: Int = 0,
    val failed: List<RelayActionFailureDto> = emptyList(),
    val targetMailbox: String = "",
)

/** `to`/`cc`/`bcc` are comma-separated strings here, not arrays — differs from /api/inbox's
 *  response shape and from contact sync's array-of-objects shape (Mobile_Mail_Relay.md Part 6). */
@Serializable
data class RelayMailRequestDto(
    val to: String,
    val cc: String = "",
    val bcc: String = "",
    val subject: String,
    val body: String,
    val mode: String = "plain",
)

@Serializable
data class RelaySendResponseDto(val ok: Boolean = false, val sentSaved: Boolean = false, val warning: String = "")
