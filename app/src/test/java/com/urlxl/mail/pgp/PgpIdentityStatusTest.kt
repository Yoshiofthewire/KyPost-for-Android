package com.urlxl.mail.pgp

import org.junit.Assert.assertEquals
import org.junit.Test

/** Covers [pgpIdentityFromMintResult] — the pure mapping [hasPgpIdentity] uses to turn a
 *  [PgpQrClient.mintToken] outcome into "does this account have a PGP identity". */
class PgpIdentityStatusTest {

    @Test
    fun success_mapsToTrue() {
        val result = PgpQrTokenResult.Success(PgpQrTokenDto(token = "t", expiresAt = "e", url = "u"))
        assertEquals(true, pgpIdentityFromMintResult(result))
    }

    @Test
    fun noIdentity_mapsToFalse() {
        val result = PgpQrTokenResult.NoIdentity("no pgp identity configured yet")
        assertEquals(false, pgpIdentityFromMintResult(result))
    }

    @Test
    fun unauthorized_mapsToNull_notFalse() {
        val result = PgpQrTokenResult.Unauthorized("bad secret")
        assertEquals(null, pgpIdentityFromMintResult(result))
    }

    @Test
    fun serviceUnavailable_mapsToNull_notFalse() {
        val result = PgpQrTokenResult.ServiceUnavailable("pairing not configured")
        assertEquals(null, pgpIdentityFromMintResult(result))
    }

    @Test
    fun retryable_mapsToNull_notFalse() {
        val result = PgpQrTokenResult.Retryable("network error")
        assertEquals(null, pgpIdentityFromMintResult(result))
    }
}
