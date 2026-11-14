package com.example.familibalance.data.update

/**
 * Семантическое сравнение версий вида "1.0.10" vs "1.0.9".
 * Лидирующая 'v'/'V' отбрасывается. Суффиксы (-alpha, +build) игнорируются.
 */
object VersionComparator {

    fun isNewer(current: String, latest: String): Boolean {
        val c = parse(current)
        val l = parse(latest)
        for (i in 0 until maxOf(c.size, l.size)) {
            val cv = c.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (lv > cv) return true
            if (lv < cv) return false
        }
        return false
    }

    private fun parse(version: String): List<Int> {
        val core = version.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('-')
            .substringBefore('+')
        return core.split('.')
            .map { it.trim().toIntOrNull() ?: 0 }
    }
}
