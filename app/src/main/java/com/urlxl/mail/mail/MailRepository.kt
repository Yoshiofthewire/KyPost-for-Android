package com.urlxl.mail.mail

import com.urlxl.mail.Email
import com.urlxl.mail.MailConnectionMode
import com.urlxl.mail.MailSettings
import com.urlxl.mail.data.AppDatabase
import com.urlxl.mail.data.toEntity
import com.urlxl.mail.data.toUiEmail

/**
 * Picks the active [MailSource] based on [MailSettings]' connection mode, writes fetch results
 * into the Room cache (the UI's read model regardless of source — see data/EmailDao's
 * replaceFolderSnapshot), and exposes the actions InboxActivity/EmailDetailActivity/
 * ComposeActivity call instead of instantiating MailGateway directly.
 */
class MailRepository(
    private val db: AppDatabase,
    private val imapSource: MailSource,
    private val relaySource: MailSource,
    private val mailSettings: MailSettings,
) {
    private fun activeSource(): MailSource =
        if (mailSettings.getConnectionMode() == MailConnectionMode.RELAY) relaySource else imapSource

    private fun activeMode(): String =
        if (mailSettings.getConnectionMode() == MailConnectionMode.RELAY) "relay" else "imap"

    /** Cached rows for [folder], available immediately (e.g. a fast cold-start render). */
    fun cachedEmails(folder: String): List<Email> = db.emailDao().getByFolder(folder).map { it.toUiEmail() }

    /** Fetches from the active source, reconciles into the Room cache, and returns the outcome. */
    fun refreshFolder(folder: String, limit: Int = 50): MailOutcome<MailFetchResult> {
        val outcome = activeSource().fetchInbox(folder, limit)
        if (outcome is MailOutcome.Success) {
            val entities = outcome.value.messages.map { it.toEntity(folder, activeMode()) }
            db.emailDao().replaceFolderSnapshot(folder, entities)
        }
        return outcome
    }

    fun markRead(id: String, folder: String): MailOutcome<Unit> {
        val outcome = activeSource().performAction(MailAction.READ, listOf(id), folder)
        if (outcome is MailOutcome.Success) db.emailDao().updateStatus(id, "read")
        return outcome.toUnitOutcome()
    }

    fun archive(id: String, folder: String): MailOutcome<Unit> = mutate(MailAction.ARCHIVE, id, folder)

    fun spam(id: String, folder: String): MailOutcome<Unit> = mutate(MailAction.SPAM, id, folder)

    fun delete(id: String, folder: String): MailOutcome<Unit> = mutate(MailAction.DELETE, id, folder)

    fun move(id: String, folder: String, targetFolder: String): MailOutcome<Unit> {
        val outcome = activeSource().performAction(MailAction.MOVE, listOf(id), folder, targetFolder)
        if (outcome is MailOutcome.Success) db.emailDao().deleteById(id)
        return outcome.toUnitOutcome()
    }

    private fun mutate(action: MailAction, id: String, folder: String): MailOutcome<Unit> {
        val outcome = activeSource().performAction(action, listOf(id), folder)
        if (outcome is MailOutcome.Success) db.emailDao().deleteById(id)
        return outcome.toUnitOutcome()
    }

    fun send(draft: MailDraft): MailOutcome<MailSendOutcome> = activeSource().sendMail(draft)

    /** Cache-first: relay mode already returns a full body inline via fetchInbox; IMAP mode fetches fresh. */
    fun fetchBody(id: String, folder: String): MailOutcome<MailMessageBody> {
        if (mailSettings.getConnectionMode() == MailConnectionMode.RELAY) {
            val cached = db.emailDao().getBody(id)
            if (!cached.isNullOrBlank()) {
                return MailOutcome.Success(MailMessageBody(html = cached, toAddresses = emptyList(), ccAddresses = emptyList()))
            }
        }
        return activeSource().fetchMessageBody(id, folder)
    }
}

private fun <T> MailOutcome<T>.toUnitOutcome(): MailOutcome<Unit> = when (this) {
    is MailOutcome.Success -> MailOutcome.Success(Unit)
    is MailOutcome.NotConfigured -> this
    is MailOutcome.Unauthorized -> this
    is MailOutcome.ServiceUnavailable -> this
    is MailOutcome.UpstreamFailure -> this
    is MailOutcome.BadRequest -> this
}
