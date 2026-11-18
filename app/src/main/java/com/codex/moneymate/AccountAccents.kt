package com.codex.moneymate

object AccountAccents {
    // Keep the palette and assignment in sync with web/src/lib/accountAccents.js.
    private val light = intArrayOf(
        0xFF087C73.toInt(), 0xFF205CB5.toInt(), 0xFF514DB4.toInt(),
        0xFF7848A6.toInt(), 0xFF597320.toInt(), 0xFF826500.toInt(),
        0xFF257849.toInt(), 0xFF54647B.toInt(), 0xFF126F8E.toInt(),
    )
    private val dark = intArrayOf(
        0xFF52D6C5.toInt(), 0xFF8CB8FF.toInt(), 0xFFAAA9FF.toInt(),
        0xFFC9A2F2.toInt(), 0xFFB7D97E.toInt(), 0xFFE5C465.toInt(),
        0xFF79D49B.toInt(), 0xFFAABBD1.toInt(), 0xFF6CCDEB.toInt(),
    )

    @JvmStatic
    fun assign(names: List<String>, darkMode: Boolean): Map<String, Int> {
        val palette = if (darkMode) dark else light
        val used = IntArray(palette.size)
        return names.distinct().sorted().associateWith { name ->
            val preferred = Math.floorMod(name.hashCode(), palette.size)
            var index = preferred
            for (offset in 1 until palette.size) {
                val candidate = (preferred + offset) % palette.size
                if (used[candidate] < used[index]) index = candidate
            }
            used[index]++
            palette[index]
        }
    }
}
