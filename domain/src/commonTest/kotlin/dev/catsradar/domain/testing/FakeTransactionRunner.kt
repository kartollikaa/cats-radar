package dev.catsradar.domain.testing

import dev.catsradar.domain.repository.TransactionRunner

fun interface RollsBack {
    /** Captures the current contents; invoking the result restores them. */
    fun checkpoint(): () -> Unit
}

/** Restores every participant to where it stood when the block began, if the block does not finish. */
class FakeTransactionRunner(private vararg val participants: RollsBack) : TransactionRunner {

    override suspend fun <R> inTransaction(block: suspend () -> R): R {
        val restores = participants.map { it.checkpoint() }
        var committed = false
        try {
            return block().also { committed = true }
        } finally {
            if (!committed) restores.forEach { restore -> restore() }
        }
    }
}
