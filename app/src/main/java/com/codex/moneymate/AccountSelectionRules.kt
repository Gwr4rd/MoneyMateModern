package com.codex.moneymate

import kotlin.math.abs

data class AccountSelectionOption(
    @JvmField val name: String,
    @JvmField val type: String,
    @JvmField val active: Boolean,
) {
    override fun toString(): String = name
}

object AccountSelectionRules {
    @JvmStatic
    fun isActive(balance: Double, hidden: Boolean): Boolean = !hidden && abs(balance) >= 0.005
}
