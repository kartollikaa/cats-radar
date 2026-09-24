package dev.catsradar.domain.repository

/** Runs repository work as one unit: when [inTransaction]'s block throws, none of its writes remain. */
interface TransactionRunner {
    suspend fun <R> inTransaction(block: suspend () -> R): R
}
