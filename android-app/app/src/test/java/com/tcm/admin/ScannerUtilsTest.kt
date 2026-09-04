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
        val confused = "O1lS8B"
        val cleaned = ScannerActivity.cleanDigits(confused)
        assertEquals("011588", cleaned)
    }

    @Test
    fun testCleanDigits_replacesAWith4_RWith8_CWith0() {
        // 'A' -> '4', 'R' -> '8', 'C' -> '0'
        val confused = "30A82R5C3"
        val cleaned = ScannerActivity.cleanDigits(confused)
        assertEquals("304828503", cleaned)
    }

    @Test
    fun testCleanDigits_removesNonDigitSymbols() {
        val mixed = "123-456#789"
        val cleaned = ScannerActivity.cleanDigits(mixed)
        assertEquals("123456789", cleaned)
    }

    @Test
    fun testExtractSku_handlesShuAndSuPrefixes() {
        assertEquals("304828503", ScannerActivity.extractSku("SHU: 304828503"))
        assertEquals("304828503", ScannerActivity.extractSku("SU: 304828503"))
        assertEquals("304828503", ScannerActivity.extractSku("S H U: 304828503"))
        assertEquals("304828503", ScannerActivity.extractSku("S.K.U. 304828503"))
        assertEquals("304828503", ScannerActivity.extractSku("5HU: 304828503"))
        assertEquals("304828503", ScannerActivity.extractSku("5U: 304828503"))
        assertEquals("304828503", ScannerActivity.extractSku("货号: 304828503"))
    }

    @Test
    fun testExtractSku_handlesOcrSubstitutionsWithSpaces() {
        assertEquals("304828503", ScannerActivity.extractSku("SKU: 30A 82R 5C3"))
        assertEquals("304828503", ScannerActivity.extractSku("SU: 30A 82R 5C3"))
        assertEquals("304828503", ScannerActivity.extractSku("SHU: 30A 82R 5C3"))
    }

    @Test
    fun testExtractSku_handlesRepeatedZerosAndDigits() {
        assertEquals("100025678", ScannerActivity.extractSku("SKU: 100C25678"))
        assertEquals("303327503", ScannerActivity.extractSku("SKU: 303E27503"))
    }

    @Test
    fun testFormatLocationCode_omitsLeadingZeros() {
        assertEquals("D-1-2-3", com.tcm.admin.ui.screens.formatLocationCode("D-01-02-03"))
        assertEquals("D-1-2-3-1", com.tcm.admin.ui.screens.formatLocationCode("D-01-02-03-01"))
        assertEquals("G-1-2", com.tcm.admin.ui.screens.formatLocationCode("G-01-02"))
        assertEquals("D-10-5-12", com.tcm.admin.ui.screens.formatLocationCode("D-10-05-12"))
    }
}
