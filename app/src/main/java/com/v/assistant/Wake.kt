package com.v.assistant

import java.text.Normalizer

object Wake {
    fun norm(s: String): String {
        val n = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        return n.replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9 ]"), " ")
    }

    fun nameFromFile(file: String): String {
        val base = file.substringBeforeLast('.')
        val first = base.split('-', '_', ' ').firstOrNull() ?: ""
        return norm(first).filter { it in 'a'..'z' }
    }

    fun wakeName(): String {
        val o = Core.wakeOverride
        if (!o.isNullOrBlank()) {
            val w = norm(o).trim().split(" ").firstOrNull() ?: ""
            if (w.isNotEmpty()) return w
        }
        val f = Core.modelName
        val n = if (f != null) nameFromFile(f) else ""
        return if (n.isBlank()) "v" else n
    }

    private fun lev(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return dp[b.length]
    }

    private fun match(w: String, wake: String): Boolean {
        if (w.isEmpty()) return false
        if (w == wake) return true
        if (wake.length < 3) return false
        return lev(w, wake) <= maxOf(1, wake.length / 4)
    }

    /** Retourne le reste de la phrase après le nom d'appel, ou null si le nom n'est pas dit. */
    fun find(text: String): String? {
        val wake = wakeName()
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        for (i in words.indices) {
            if (match(norm(words[i]).trim(), wake)) {
                return words.drop(i + 1).joinToString(" ")
            }
        }
        return null
    }
}
