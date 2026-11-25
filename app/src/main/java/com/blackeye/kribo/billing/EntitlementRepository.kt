package com.blackeye.kribo.billing

import android.app.Activity
import com.blackeye.kribo.BuildConfig
import com.blackeye.kribo.security.SecureKeyValueStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Single source of truth for the PRO entitlement.
 *
 * - Starts from the encrypted cache so offline users keep their PRO state.
 * - When Billing resolves, Play is authoritative and the cache is rewritten.
 * - In Release there is no bypass: only a PURCHASED `kribo_pro_lifetime` unlocks PRO.
 */
class EntitlementRepository(
    private val source: BillingSource,
    private val store: SecureKeyValueStore,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    /** Fail-open PRO for local UI work; release (false) always asks Play Billing. */
    private val debugOverride: Boolean = BuildConfig.DEBUG,
) {

    private val _isPro = MutableStateFlow(false)
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    private val _resolved = MutableStateFlow(false)
    val resolved: StateFlow<Boolean> = _resolved.asStateFlow()

    val billingState: StateFlow<BillingUiState> = source.state

    init {
        // Debug builds are fully unlocked for UI work; release keeps the Play verdict authoritative.
        if (debugOverride) {
            _isPro.value = true
            _resolved.value = true
        }
        scope.launch {
            if (!debugOverride) {
                // KeyStore decryption stays off the first frame; offline PRO appears a moment later.
                if (runCatching { store.get(KEY_PRO_CACHE) }.getOrNull() == "1") {
                    _isPro.value = true
                }
            }
            source.state.collect { state ->
                if (state.resolved) {
                    if (debugOverride) {
                        _isPro.value = true
                        _resolved.value = true
                    } else {
                        val pro = state.owned
                        store.put(KEY_PRO_CACHE, if (pro) "1" else "0")
                        _isPro.value = pro
                        _resolved.value = true
                    }
                }
            }
        }
    }

    fun refresh() = source.refresh()

    fun launchPurchase(activity: Activity) = source.launchPurchase(activity)

    fun restore(onResult: (Boolean) -> Unit) = source.restore(onResult)

    /** Debug-only convenience for local UI work. Never compiled into release behavior. */
    fun setDebugPro(value: Boolean) {
        if (!BuildConfig.DEBUG) return
        store.put(KEY_PRO_CACHE, if (value) "1" else "0")
        _isPro.value = value
        _resolved.value = true
    }

    companion object {
        internal const val KEY_PRO_CACHE = "pro_lifetime_v1"
    }
}
