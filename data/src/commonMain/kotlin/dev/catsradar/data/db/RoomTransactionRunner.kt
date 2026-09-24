package dev.catsradar.data.db

import androidx.room3.RoomDatabase
import androidx.room3.withWriteTransaction
import dev.catsradar.domain.repository.TransactionRunner

class RoomTransactionRunner(private val database: RoomDatabase) : TransactionRunner {
    override suspend fun <R> inTransaction(block: suspend () -> R): R = database.withWriteTransaction { block() }
}
