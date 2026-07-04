package com.urlxl.mail.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingValidatorTest {

    @Test
    fun validate_requiredFields() {
        assertFalse(PairingValidator.validate("", "sub", "hash").isValid)
        assertFalse(PairingValidator.validate("app", "", "hash").isValid)
        assertFalse(PairingValidator.validate("app", "sub", "").isValid)
        assertTrue(PairingValidator.validate("app", "sub", "hash").isValid)
    }

    @Test
    fun validate_missingHash_returnsSpecificMessage() {
        val result = PairingValidator.validate("app", "sub", "")

        assertFalse(result.isValid)
        assertEquals("Missing hash parameter", result.message)
    }
}

