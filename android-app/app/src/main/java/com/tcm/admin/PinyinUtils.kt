package com.tcm.admin

import android.icu.text.Transliterator

private val hanToLatin by lazy {
    Transliterator.getInstance("Han-Latin; Latin-ASCII")
}

private val charPinyinCache = java.util.concurrent.ConcurrentHashMap<Char, Char>()
private val stringPinyinCache = object : java.util.LinkedHashMap<String, String>(256, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > 256
}

private fun isHanCharacter(value: Char): Boolean =
    value.code in 0x3400..0x4DBF || value.code in 0x4E00..0x9FFF

private fun pinyinInitial(value: Char): Char? {
    if (value.isLetterOrDigit() && !isHanCharacter(value)) return value.lowercaseChar()
    charPinyinCache[value]?.let { return it }
    val latin = synchronized(hanToLatin) { hanToLatin.transliterate(value.toString()) }
    val initial = latin.firstOrNull { it in 'A'..'Z' || it in 'a'..'z' }?.lowercaseChar()
    if (initial != null) {
        charPinyinCache[value] = initial
    }
    return initial
}

internal fun pinyinInitials(value: String): String {
    synchronized(stringPinyinCache) { stringPinyinCache[value]?.let { return it } }
    val result = buildString {
        value.forEach { character -> pinyinInitial(character)?.let(::append) }
    }
    synchronized(stringPinyinCache) { stringPinyinCache[value] = result }
    return result
}

internal fun pinyinInitialMatchRange(value: String, keyword: String): IntRange? {
    val query = keyword.trim().lowercase()
    if (query.isBlank() || query.any { it !in 'a'..'z' } || value.none(::isHanCharacter)) return null

    val initials = StringBuilder()
    val sourceIndexes = mutableListOf<Int>()
    value.forEachIndexed { index, character ->
        pinyinInitial(character)?.let { initial ->
            initials.append(initial)
            sourceIndexes += index
        }
    }
    val start = initials.indexOf(query)
    if (start < 0) return null
    return sourceIndexes[start]..sourceIndexes[start + query.length - 1]
}
