package com.tcm.admin

import android.icu.text.Transliterator

private val hanToLatin by lazy {
    Transliterator.getInstance("Han-Latin; Latin-ASCII")
}

private fun isHanCharacter(value: Char): Boolean =
    value.code in 0x3400..0x4DBF || value.code in 0x4E00..0x9FFF

private fun pinyinInitial(value: Char): Char? {
    if (value.isLetterOrDigit() && !isHanCharacter(value)) return value.lowercaseChar()
    val latin = synchronized(hanToLatin) { hanToLatin.transliterate(value.toString()) }
    return latin.firstOrNull { it in 'A'..'Z' || it in 'a'..'z' }?.lowercaseChar()
}

internal fun pinyinInitials(value: String): String = buildString {
    value.forEach { character -> pinyinInitial(character)?.let(::append) }
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
