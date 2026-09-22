package dev.catsradar.presentation

import androidx.lifecycle.ViewModelStore
import app.cash.turbine.test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
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

    override suspend fun handle(intent: FakeIntent) {
        when (intent) {
            FakeIntent.Increment -> setState { copy(count = count + 1) }
            FakeIntent.EmitPing -> emit(FakeEffect)
            FakeIntent.BlockUntilCancelled -> {
                try {
                    awaitCancellation()
                } finally {
                    cancelled.complete(Unit)
                }
            }
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class StoreTest {

    // viewModelScope resolves to Dispatchers.Main.immediate; a JVM test has no real Main
    // dispatcher unless one is installed, and Unconfined runs dispatch()'s launch eagerly so
    // assertions right after dispatch() see its effect without pumping a scheduler.
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
}
