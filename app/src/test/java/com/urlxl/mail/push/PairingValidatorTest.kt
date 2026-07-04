package com.urlxl.mail.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingValidatorTest {

    @Test
    fun validate_requiredFields() {
        assertFalse(PairingValidator.validate("", "hash").isValid)
        assertFalse(PairingValidator.validate("sub", "").isValid)
        assertTrue(PairingValidator.validate("sub", "hash").isValid)
    }

    @Test
    fun validate_missingHash_returnsSpecificMessage() {
        val result = PairingValidator.validate("sub", "")

        assertFalse(result.isValid)
        assertEquals("Missing hash parameter", result.message)
    }
}
