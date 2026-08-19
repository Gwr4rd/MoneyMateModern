package com.codex.moneymate

import java.util.Locale

data class ImportPreview(
    val fileName: String,
    val accounts: Int,
    val categories: Int,
    val movements: Int,
    val transfers: Int,
    val duplicatesRemoved: Int,
    val existingMatches: Int,
    val unpairedTransfers: Int,
    val firstDate: String,
    val lastDate: String,
    val currencyCode: String,
    val income: Double,
    val expense: Double,
    val warnings: List<String>,
)

data class IntegrityReport(
    val databaseOk: Boolean,
    val duplicateMovements: Int,
    val orphanMovements: Int,
    val invalidMovements: Int,
    val unpairedTransfers: Int,
) {
    val repairableIssues: Int
        get() = duplicateMovements + orphanMovements

    val healthy: Boolean
        get() = databaseOk && duplicateMovements == 0 && orphanMovements == 0 && invalidMovements == 0 && unpairedTransfers == 0
}

object ImportSafety {
    @JvmStatic
    fun audit(database: MoneyDb): IntegrityReport {
        val unpairedTransfers = database.transactionsForDisplay(null, null).count {
            it.isTransfer && (it.transferFrom.isBlank() || it.transferTo.isBlank())
        }
        return IntegrityReport(
            databaseOk = database.sqliteIntegrityCheck().equals("ok", ignoreCase = true),
            duplicateMovements = database.duplicateTransactionCount(),
            orphanMovements = database.orphanTransactionCount(),
            invalidMovements = database.invalidTransactionCount(),
            unpairedTransfers = unpairedTransfers,
        )
    }

    @JvmStatic
    fun analyze(fileName: String, current: MoneyDb, staged: MoneyDb, duplicatesRemoved: Int): ImportPreview {
        val stagedRows = staged.transactionsForDisplay(null, null)
        val currentFingerprints = current.transactionsForDisplay(null, null)
            .asSequence()
            .map(::fingerprint)
            .toHashSet()
        val existingMatches = stagedRows.count { fingerprint(it) in currentFingerprints }
        val transfers = stagedRows.count { it.isTransfer }
        val unpairedTransfers = stagedRows.count {
            it.isTransfer && (it.transferFrom.isBlank() || it.transferTo.isBlank())
        }
        val summary = staged.summaryBetween(null, null)
        val warnings = buildList {
            if (stagedRows.isEmpty()) add("El archivo no contiene movimientos válidos.")
            if (unpairedTransfers > 0) add("Hay $unpairedTransfers transferencias sin una cuenta de origen o destino completa.")
            if (existingMatches > 0) add("$existingMatches movimientos también existen en los datos actuales.")
            if (duplicatesRemoved > 0) add("Se omitieron $duplicatesRemoved filas duplicadas dentro del archivo.")
            if (staged.earliestDate().startsWith("1970-")) add("Algunas fechas no pudieron identificarse con precisión.")
        }
        return ImportPreview(
            fileName = fileName,
            accounts = staged.accountOptions().size,
            categories = staged.categoryOptions("income").size + staged.categoryOptions("expense").size,
            movements = stagedRows.size,
            transfers = transfers,
            duplicatesRemoved = duplicatesRemoved,
            existingMatches = existingMatches,
            unpairedTransfers = unpairedTransfers,
            firstDate = staged.earliestDate(),
            lastDate = if (stagedRows.isEmpty()) "" else staged.latestDate(),
            currencyCode = staged.primaryCurrency(),
            income = summary.income,
            expense = summary.expense,
            warnings = warnings,
        )
    }

    private fun fingerprint(row: MoneyDb.Row): String {
        val account = if (row.isTransfer) "${row.transferFrom}>${row.transferTo}" else row.account
        return listOf(
            row.date,
            row.time,
            row.kind,
            account,
            row.category,
            String.format(Locale.ROOT, "%.2f", row.amount),
            row.note.trim().lowercase(Locale.ROOT),
            row.description.trim().lowercase(Locale.ROOT),
        ).joinToString("|")
    }
}
