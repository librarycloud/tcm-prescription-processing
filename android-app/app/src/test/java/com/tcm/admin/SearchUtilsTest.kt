package com.tcm.admin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchUtilsTest {

    @Test
    fun testShouldAutoSearchQuery_emptyString() {
        assertFalse(shouldAutoSearchQuery(""))
        assertFalse(shouldAutoSearchQuery("   "))
    }

    @Test
    fun testShouldAutoSearchQuery_chineseCharacters() {
        // Single chinese char: do not auto-search to prevent heavy prefix search
        assertFalse(shouldAutoSearchQuery("黄"))
        // Two or more chinese chars: auto-search
        assertTrue(shouldAutoSearchQuery("黄芪"))
        assertTrue(shouldAutoSearchQuery("熟地黄"))
    }

    @Test
    fun testShouldAutoSearchQuery_numericDigits() {
        // Less than 4 digits: do not auto-search
        assertFalse(shouldAutoSearchQuery("1"))
        assertFalse(shouldAutoSearchQuery("12"))
        assertFalse(shouldAutoSearchQuery("123"))
        // 4 or more digits: auto-search
        assertTrue(shouldAutoSearchQuery("1234"))
        assertTrue(shouldAutoSearchQuery("123456789"))
    }

    @Test
    fun testShouldAutoSearchQuery_latinLetters() {
        // Latin letters (pinyin initial / barcode / code): auto-search
        assertTrue(shouldAutoSearchQuery("a"))
        assertTrue(shouldAutoSearchQuery("hq"))
    }
}
