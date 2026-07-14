package com.urlxl.mail.pgp

import kotlinx.serialization.Serializable

/** Response body of `GET /api/pgp/qr/token` (pairing-authenticated). */
@Serializable
data class PgpQrTokenDto(
    val token: String = "",
    val expiresAt: String = "",
    val url: String = "",
)

/** Response body of `GET /api/pgp/qr/key` (unauthenticated, token-gated). */
@Serializable
data class PgpQrKeyDto(
    val name: String = "",
    val fingerprint: String = "",
    val publicKey: String = "",
)
