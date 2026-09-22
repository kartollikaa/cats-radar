package dev.catsradar.presentation

import androidx.lifecycle.ViewModelStore
import app.cash.turbine.test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private data class FakeState(val count: Int = 0)

private sealed interface FakeIntent {
    data object Increment : FakeIntent
    data object EmitPing : FakeIntent
    data object BlockUntilCancelled : FakeIntent
}

private data object FakeEffect

private class FakeStore : Store<FakeState, FakeIntent, FakeEffect>(FakeState()) {
    val cancelled = CompletableDeferred<Unit>()
    var pingHandled = false
        private set

    override suspend fun handle(intent: FakeIntent) {
        when (intent) {
            FakeIntent.Increment -> setState { copy(count = count + 1) }
            FakeIntent.EmitPing -> {
                emit(FakeEffect)
                pingHandled = true
            }
            FakeIntent.BlockUntilCancelled -> {
                try {
                    awaitCancellation()
                } finally {
                    cancelled.complete(Unit)
                }
            }
        }
    }

    // Calls the protected setState directly from real threads, bypassing dispatch()'s
    // single-threaded viewModelScope, so setState's own atomicity is what's under test.
    suspend fun incrementConcurrently(workers: Int, incrementsPerWorker: Int) = coroutineScope {
        repeat(workers) {
            launch(Dispatchers.Default) {
                repeat(incrementsPerWorker) {
                    setState { copy(count = count + 1) }
                }
            }
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class StoreTest {

    // A JVM test has no real Main dispatcher; Unconfined also runs dispatch()'s launch
    // synchronously, so assertions right after dispatch() see the result immediately.
    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `dispatch reaches handle and setState is observable on state`() = runTest {
        val store = FakeStore()

        store.dispatch(FakeIntent.Increment)

        assertEquals(FakeState(count = 1), store.state.value)
    }

    @Test
    fun `an effect is delivered exactly once and is not replayed to a later collector`() = runTest {
        val store = FakeStore()

        store.dispatch(FakeIntent.EmitPing)
        store.effects.test {
            assertEquals(FakeEffect, awaitItem())
            expectNoEvents()
        }

        // A collector attaching after the effect was already consumed must see nothing.
        store.effects.test {
            expectNoEvents()
        }
    }

    @Test
    fun `a second collector of state sees the current value immediately`() = runTest {
        val store = FakeStore()
        store.dispatch(FakeIntent.Increment)

        store.state.test { assertEquals(FakeState(count = 1), awaitItem()) }
        store.state.test { assertEquals(FakeState(count = 1), awaitItem()) }
    }

    @Test
    fun `work started in handle is cancelled when the scope closes`() = runTest {
        val store = FakeStore()
        val viewModelStore = ViewModelStore()
        viewModelStore.put("fake", store)

        store.dispatch(FakeIntent.BlockUntilCancelled)
        viewModelStore.clear()

        assertTrue(store.cancelled.isCompleted)
    }

    @Test
    fun `concurrent setState calls do not lose updates`() = runTest {
        val store = FakeStore()
        val workers = 8
        val incrementsPerWorker = 2_000

        store.incrementConcurrently(workers, incrementsPerWorker)

        assertEquals(workers * incrementsPerWorker, store.state.value.count)
    }

    @Test
    fun `emitting without a collector attached does not suspend`() = runTest {
        val store = FakeStore()

        // No collector on store.effects; Unconfined runs dispatch() to completion unless it suspends.
        store.dispatch(FakeIntent.EmitPing)
        val emitReturnedWithoutSuspending = store.pingHandled

        assertTrue(emitReturnedWithoutSuspending)
    }
}
