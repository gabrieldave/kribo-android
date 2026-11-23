package com.blackeye.kribo.billing

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

/** Minimal seam so entitlement logic can be tested without Play services. */
interface BillingSource {
    val state: StateFlow<BillingUiState>
    fun refresh()
    fun launchPurchase(activity: Activity)
    fun restore(onResult: (Boolean) -> Unit)
}
