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
    fun testCleanDigits_doesNotReplaceA_filtersOutLetterA() {
        // 'A' should NOT be replaced by '4'; 'R' -> '8', 'C' -> '0'
        val confused = "30A82R5C3"
        val cleaned = ScannerActivity.cleanDigits(confused)
        assertEquals("30828503", cleaned)
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
    fun testExtractSku_rejectsNon9DigitTokens() {
        // "30A 82R 5C3" has only 8 digits since 'A' is not converted to 4
        assertEquals(null, ScannerActivity.extractSku("SKU: 30A 82R 5C3"))
        assertEquals(null, ScannerActivity.extractSku("12345678"))
    }

    @Test
    fun testExtractSku_handles9DigitSkuAndTrailingLetterA() {
        // 9 digits followed by trailing letter A (which is filtered out)
        assertEquals("300001700", ScannerActivity.extractSku("SKU: 300001700 A"))
        assertEquals("300001700", ScannerActivity.extractSku("SKU: 300001700A"))
        assertEquals("300001700", ScannerActivity.extractSku("300001700"))
        assertEquals("300001700", ScannerActivity.extractSku("货号: 300001700"))
        assertEquals("123456789", ScannerActivity.extractSku("123456789"))
    }

    @Test
    fun testExtractSku_handlesRepeatedZerosAndDigits() {
        assertEquals("100025678", ScannerActivity.extractSku("SKU: 100C25678"))
        assertEquals("303327503", ScannerActivity.extractSku("SKU: 303E27503"))
    }

    @Test
    fun testExtractSku_handlesTripleZeros() {
        assertEquals("300028503", ScannerActivity.extractSku("SKU: 300028503"))
        assertEquals("300028503", ScannerActivity.extractSku("300028503"))
    }

    @Test
    fun testFormatLocationCode_omitsLeadingZeros() {
        assertEquals("D-1-2-3", com.tcm.admin.ui.screens.formatLocationCode("D-01-02-03"))
        assertEquals("D-1-2-3-1", com.tcm.admin.ui.screens.formatLocationCode("D-01-02-03-01"))
        assertEquals("G-1-2", com.tcm.admin.ui.screens.formatLocationCode("G-01-02"))
        assertEquals("D-10-5-12", com.tcm.admin.ui.screens.formatLocationCode("D-10-05-12"))
    }
}
