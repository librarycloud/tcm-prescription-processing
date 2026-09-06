package com.tcm.admin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApiClientTokenTest {

    @Test
    fun testSanitizeToken_validJwt() {
        val validJwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpZCI6MTMsInJvbGUiOjMsInN0b3JlSWQiOjUsImlhdCI6MTYwMDAwMDAwMH0.R0gsaG9sqWGRGtAHDHZn3xoGhJafl-dqBIktdK9eYR8"
        assertEquals(validJwt, ApiClient.sanitizeToken(validJwt))
        // Trims leading/trailing whitespace
        assertEquals(validJwt, ApiClient.sanitizeToken("  $validJwt\n"))
    }

    @Test
    fun testSanitizeToken_rejectsUnexpectedChar0x08() {
        // OkHttp throws "Unexpected char 0x08 at 166 in Authorization value"
        // 0x08 is ASCII Backspace
        val tokenWith0x08 = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9" + 0x08.toChar() + "extra"
        assertNull("Tokens containing 0x08 control char must be rejected", ApiClient.sanitizeToken(tokenWith0x08))
    }

    @Test
    fun testSanitizeToken_rejectsOtherControlAndNonAsciiChars() {
        assertNull(ApiClient.sanitizeToken(null))
        assertNull(ApiClient.sanitizeToken(""))
        assertNull(ApiClient.sanitizeToken("   "))
        assertNull(ApiClient.sanitizeToken("token\u0000withNull"))
        assertNull(ApiClient.sanitizeToken("token\u001FwithUnitSeparator"))
        assertNull(ApiClient.sanitizeToken("tokenWithChinese字符"))
        assertNull(ApiClient.sanitizeToken("token with space"))
    }
}
