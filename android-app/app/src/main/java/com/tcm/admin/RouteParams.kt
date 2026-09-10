package com.tcm.admin

import java.util.UUID

internal object RouteParams {
    private val params = mutableMapOf<String, Any>()

    fun put(value: Any?): String {
        val key = UUID.randomUUID().toString()
        if (value != null) params[key] = value
        return key
    }

    fun pop(key: String): Any? = params.remove(key)
    fun peek(key: String): Any? = params[key]
}
