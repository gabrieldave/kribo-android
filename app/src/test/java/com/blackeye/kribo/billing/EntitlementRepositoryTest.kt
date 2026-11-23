package com.blackeye.kribo.billing

import android.app.Activity
import com.blackeye.kribo.security.InMemorySecureStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EntitlementRepositoryTest {

    private class FakeSource(
        initial: BillingUiState = BillingUiState(),
    ) : BillingSource {
        val mutable = MutableStateFlow(initial)
        override val state: StateFlow<BillingUiState> = mutable
        var refreshes = 0
        var restoreResult = false

        override fun refresh() {
            refreshes++
        }

        override fun launchPurchase(activity: Activity) = Unit

        override fun restore(onResult: (Boolean) -> Unit) {
            onResult(restoreResult)
        }
    }

    private fun TestScope.newRepository(
        source: BillingSource,
        store: com.blackeye.kribo.security.SecureKeyValueStore,
    ): EntitlementRepository =
        EntitlementRepository(source, store, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

    @Test
    fun startsFromEncryptedCacheWhenOffline() = runTest {
        val store = InMemorySecureStore().apply {
            put(EntitlementRepository.KEY_PRO_CACHE, "1")
        }
        val source = FakeSource(BillingUiState(connected = false, resolved = false))
        val repository = newRepository(source, store)
        advanceUntilIdle()

        assertTrue(repository.isPro.value)
        assertFalse(repository.resolved.value)
    }

    @Test
    fun playBecomesAuthoritativeAndRewritesCache() = runTest {
        val store = InMemorySecureStore().apply {
            put(EntitlementRepository.KEY_PRO_CACHE, "1")
        }
        val source = FakeSource()
        val repository = newRepository(source, store)
        advanceUntilIdle()

        source.mutable.value = BillingUiState(connected = true, owned = false, resolved = true)
        advanceUntilIdle()

        assertFalse(repository.isPro.value)
        assertTrue(repository.resolved.value)
        assertEquals("0", store.get(EntitlementRepository.KEY_PRO_CACHE))
    }

    @Test
    fun unresolvedBillingDoesNotOverrideOfflineCache() = runTest {
        val store = InMemorySecureStore().apply {
            put(EntitlementRepository.KEY_PRO_CACHE, "1")
        }
        val source = FakeSource(BillingUiState(connected = true, owned = false, resolved = false))
        val repository = newRepository(source, store)
        advanceUntilIdle()

        assertTrue(repository.isPro.value)
    }

    @Test
    fun purchaseRestoresProAndForwardsRestoreResult() = runTest {
        val store = InMemorySecureStore()
        val source = FakeSource()
        val repository = newRepository(source, store)
        advanceUntilIdle()

        source.mutable.value = BillingUiState(connected = true, owned = true, resolved = true)
        advanceUntilIdle()
        assertTrue(repository.isPro.value)
        assertEquals("1", store.get(EntitlementRepository.KEY_PRO_CACHE))

        source.restoreResult = true
        var restored: Boolean? = null
        repository.restore { restored = it }
        assertEquals(true, restored)
    }

    @Test
    fun refreshDelegatesToBillingSource() = runTest {
        val source = FakeSource()
        val repository = newRepository(source, InMemorySecureStore())
        repository.refresh()
        assertEquals(1, source.refreshes)
    }
}
