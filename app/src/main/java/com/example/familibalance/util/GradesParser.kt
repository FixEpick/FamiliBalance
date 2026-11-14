package com.example.familibalance.util

fun parseGrades(recognizedText: String): Map<Int, Int> {
    val counts = mutableMapOf(5 to 0, 4 to 0, 3 to 0, 2 to 0)
    val lines = recognizedText.split("\n")
    for (line in lines) {
        if (line.contains(".") || line.contains("/") || line.contains("-")) continue
        val trimmed = line.trim()
        val words = trimmed.split("\\s+".toRegex())
        for (word in words) {
            val clean = word.filter { it.isDigit() }
            if (clean.length == 1) {
                val digit = clean.toIntOrNull()
                if (digit != null && digit in 2..5) {
                    counts[digit] = (counts[digit] ?: 0) + 1
                }
            }
        }
    }
    return counts
}
