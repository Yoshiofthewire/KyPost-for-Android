package com.urlxl.mail.push

import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks challenge IDs that arrived via a real, decrypted push delivery (recorded by
 * [PushNotificationDispatcher.showMfaChallenge], called only from
 * [KyPostFirebaseMessagingService]'s message handler). [MainActivity][com.urlxl.mail.MainActivity]
 * is exported as the app's launcher, so any co-installed app can start it with arbitrary Intent
 * extras; without this check, extras alone (`type=mfa_challenge`, any `challengeId`) would be
 * enough to surface the trusted-looking approval screen for a challenge that was never actually
 * pushed. Entries expire after a short window, since a legitimate tap happens within moments of
 * the notification arriving, not one tracked indefinitely.
 */
object MfaChallengeTracker {
    private const val VALID_FOR_MS = 5 * 60 * 1000L
    private val deliveredAtEpochMs = ConcurrentHashMap<String, Long>()

    fun markDelivered(challengeId: String, nowEpochMs: Long = System.currentTimeMillis()) {
        deliveredAtEpochMs[challengeId] = nowEpochMs
        prune(nowEpochMs)
    }

    fun isPending(challengeId: String, nowEpochMs: Long = System.currentTimeMillis()): Boolean {
        val deliveredAt = deliveredAtEpochMs[challengeId] ?: return false
        return nowEpochMs - deliveredAt in 0..VALID_FOR_MS
    }

    private fun prune(nowEpochMs: Long) {
        val cutoff = nowEpochMs - VALID_FOR_MS
        deliveredAtEpochMs.entries.removeIf { it.value < cutoff }
    }
}
