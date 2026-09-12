package com.tcm.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavBackStackEntry
import org.json.JSONObject
import java.util.UUID

internal object RouteParams {
    private const val MAX_ENTRIES = 50
    private val params = object : LinkedHashMap<String, Any>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Any>?): Boolean {
            return size > MAX_ENTRIES
        }
    }

    @Synchronized
    fun put(value: Any?): String {
        val key = UUID.randomUUID().toString()
        if (value != null) params[key] = value
        return key
    }

    @Synchronized
    fun pop(key: String): Any? = params.remove(key)

    @Synchronized
    fun peek(key: String): Any? = params[key]
}

@Composable
internal inline fun <reified T> rememberRouteParam(
    entry: NavBackStackEntry,
    argId: String,
    crossinline fallback: () -> T
): T {
    val stateHandle = entry.savedStateHandle
    val restored = remember(argId) {
        val inMemory = RouteParams.peek(argId)
        if (inMemory != null) {
            when (inMemory) {
                is JSONObject -> stateHandle["json_$argId"] = inMemory.toString()
                is PackageItem -> stateHandle["pkg_$argId"] = inMemory.toJson().toString()
            }
            @Suppress("UNCHECKED_CAST")
            (inMemory as? T) ?: fallback()
        } else {
            val jsonStr = stateHandle.get<String>("json_$argId")
            val pkgStr = stateHandle.get<String>("pkg_$argId")
            when {
                T::class == JSONObject::class && jsonStr != null -> {
                    @Suppress("UNCHECKED_CAST")
                    runCatching { JSONObject(jsonStr) as T }.getOrElse { fallback() }
                }
                T::class == PackageItem::class && pkgStr != null -> {
                    @Suppress("UNCHECKED_CAST")
                    runCatching { packageItemFromJson(JSONObject(pkgStr)) as T }.getOrElse { fallback() }
                }
                else -> fallback()
            }
        }
    }

    DisposableEffect(entry, argId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                RouteParams.pop(argId)
            }
        }
        entry.lifecycle.addObserver(observer)
        onDispose {
            entry.lifecycle.removeObserver(observer)
        }
    }

    return restored
}
