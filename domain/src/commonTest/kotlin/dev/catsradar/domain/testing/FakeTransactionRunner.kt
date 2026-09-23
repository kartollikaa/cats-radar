package dev.catsradar.domain.testing

import dev.catsradar.domain.repository.TransactionRunner

interface RollsBack {
    /** Captures the current contents; invoking the result restores them. */
    fun checkpoint(): () -> Unit
}

/** Restores every participant to where it stood when the block began, if the block does not finish. */
class FakeTransactionRunner(private vararg val participants: RollsBack) : TransactionRunner {
    private var depth = 0
    val isOpen: Boolean get() = depth > 0

    override suspend fun <R> inTransaction(block: suspend () -> R): R {
        val restores = participants.map { it.checkpoint() }
        var committed = false
        depth++
        try {
            return block().also { committed = true }
        } finally {
            depth--
            if (!committed) restores.forEach { restore -> restore() }
        }
    }
}
