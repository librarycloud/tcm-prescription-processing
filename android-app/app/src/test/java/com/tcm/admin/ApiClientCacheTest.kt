package com.tcm.admin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiClientCacheTest {

    @Test
    fun testSanitizePrefix() {
        assertEquals("admin_prescriptions", ApiClient.sanitizePrefix("/admin/prescriptions"))
        assertEquals("admin_prescriptions", ApiClient.sanitizePrefix("/admin/prescriptions/"))
        assertEquals("admin_processing-plans", ApiClient.sanitizePrefix("/admin/processing-plans"))
        assertEquals("stores", ApiClient.sanitizePrefix("stores"))
        assertEquals("", ApiClient.sanitizePrefix("/"))
    }

    @Test
    fun testCacheKeyFormat() {
        val key = ApiClient.cacheKey("/admin/prescriptions?page=1&pageSize=20")
        assertTrue("Cache key must start with sanitized route prefix", key.startsWith("admin_prescriptions__"))
        val parts = key.split("__")
        assertEquals(2, parts.size)
        assertEquals("admin_prescriptions", parts[0])
        assertEquals(64, parts[1].length) // 64 hex characters for SHA-256
    }

    @Test
    fun testCacheKeyConsistency() {
        val key1 = ApiClient.cacheKey("/admin/packages?status=1")
        val key2 = ApiClient.cacheKey("/admin/packages?status=1")
        val key3 = ApiClient.cacheKey("/admin/packages?status=2")

        assertEquals(key1, key2)
        assertTrue(key1.startsWith("admin_packages__"))
        assertTrue(key3.startsWith("admin_packages__"))
        assertTrue(key1 != key3)
    }
}
