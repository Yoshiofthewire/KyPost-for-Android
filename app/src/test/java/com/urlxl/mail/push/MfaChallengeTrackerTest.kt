package com.urlxl.mail.push

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MfaChallengeTrackerTest {

    @Test
    fun isPending_falseForUnknownChallenge() {
        assertFalse(MfaChallengeTracker.isPending("never-delivered-${System.nanoTime()}"))
    }

    @Test
    fun isPending_trueImmediatelyAfterDelivery() {
        val id = "challenge-${System.nanoTime()}"
        val now = 1_000_000L
        MfaChallengeTracker.markDelivered(id, now)

        assertTrue(MfaChallengeTracker.isPending(id, now))
        assertTrue(MfaChallengeTracker.isPending(id, now + 60_000L))
    }

    @Test
    fun isPending_falseAfterExpiryWindow() {
        val id = "challenge-${System.nanoTime()}"
        val now = 1_000_000L
        MfaChallengeTracker.markDelivered(id, now)

        assertFalse(MfaChallengeTracker.isPending(id, now + 5 * 60 * 1000L + 1))
    }
}
