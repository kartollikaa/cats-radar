package dev.catsradar.domain.testing

import dev.catsradar.domain.repository.TransactionRunner

interface RollsBack {
    /** Captures the current contents; invoking the result restores them. */
    fun checkpoint(): () -> Unit
}

/** Restores every participant to where it stood when the block began, if the block does not finish. */
class FakeTransactionRunner(private vararg val participants: RollsBack) : TransactionRunner {
    var isOpen = false
        private set

    override suspend fun <R> inTransaction(block: suspend () -> R): R {
        val restores = participants.map { it.checkpoint() }
        var committed = false
        isOpen = true
        try {
            return block().also { committed = true }
        } finally {
            isOpen = false
            if (!committed) restores.forEach { restore -> restore() }
        }
    }
}
