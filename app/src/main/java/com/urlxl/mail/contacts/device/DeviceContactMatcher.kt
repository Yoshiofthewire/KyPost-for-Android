package com.urlxl.mail.contacts.device

import com.urlxl.mail.contacts.ContactDto

object DeviceContactMatcher {
    fun normalizeEmail(email: String): String {
        return email.trim().lowercase()
    }

    /** Strips non-digits, then drops a leading NANP "1" country code (11 digits, e.g.
     *  "+1 555 123 4567") so it normalizes the same as its 10-digit form ("555-123-4567") —
     *  device contacts and server-synced contacts don't consistently store one or the other. */
    fun normalizePhone(phone: String): String {
        val digits = phone.replace(Regex("[^0-9]"), "")
        return if (digits.length == 11 && digits.startsWith("1")) digits.substring(1) else digits
    }

    fun findMatch(
        candidateEmails: List<String>,
        candidatePhones: List<String>,
        existing: List<ContactDto>,
    ): String? {
        val normalizedCandidateEmails = candidateEmails.map { normalizeEmail(it) }.toSet()
        val normalizedCandidatePhones = candidatePhones.map { normalizePhone(it) }.toSet()

        for (contact in existing) {
            val contactEmails = contact.emails.map { normalizeEmail(it.value) }.toSet()
            val contactPhones = contact.phones.map { normalizePhone(it.value) }.toSet()

            if ((normalizedCandidateEmails intersect contactEmails).isNotEmpty()) {
                return contact.uid
            }
            if ((normalizedCandidatePhones intersect contactPhones).isNotEmpty()) {
                return contact.uid
            }
        }

        return null
    }
}
