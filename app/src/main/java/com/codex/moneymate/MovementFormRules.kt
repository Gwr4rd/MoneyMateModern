package com.codex.moneymate

data class MovementFormRule(
    @JvmField val kind: String,
    @JvmField val transfer: Boolean,
    @JvmField val accountLabel: String,
    @JvmField val showCategory: Boolean,
    @JvmField val showDestination: Boolean,
)

object MovementFormRules {
    @JvmStatic
    fun forPosition(position: Int): MovementFormRule = when (position) {
        1 -> MovementFormRule("income", false, "Cuenta", true, false)
        2 -> MovementFormRule("transfer", true, "Cuenta origen", false, true)
        else -> MovementFormRule("expense", false, "Cuenta", true, false)
    }

    @JvmStatic
    fun positionFor(kind: String?, transfer: Boolean): Int = when {
        transfer -> 2
        kind == "income" -> 1
        else -> 0
    }
}
