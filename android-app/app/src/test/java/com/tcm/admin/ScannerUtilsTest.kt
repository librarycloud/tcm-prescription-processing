package com.tcm.admin

import org.junit.Assert.assertEquals
import org.junit.Test

class ScannerUtilsTest {

    @Test
    fun testNormalizeOcrText_convertsFullWidthChars() {
        val fullWidth = "ＳＫＵ：１２３４５６７８９"
        val normalized = ScannerActivity.normalizeOcrText(fullWidth)
        assertEquals("SKU:123456789", normalized)
    }

    @Test
    fun testCleanDigits_replacesSimilarLetters() {
        // 'O'/'o' -> '0', 'I'/'l'/'|' -> '1', 'S'/'s' -> '5', 'B'/'b' -> '8'
        val confused = "SKU-O1lS8B"
        val cleaned = ScannerActivity.cleanDigits(confused)
        assertEquals("011588", cleaned)
    }

    @Test
    fun testCleanDigits_removesNonDigitSymbols() {
        val mixed = "123-456#789"
        val cleaned = ScannerActivity.cleanDigits(mixed)
        assertEquals("123456789", cleaned)
    }
}
