package com.blackeye.kribo

import android.content.Context
import com.blackeye.kribo.billing.BillingManager
import com.blackeye.kribo.data.KriboDatabase
import com.blackeye.kribo.data.NoteRepository

/** Manual dependency container: no reflection, no service locator, no startup cost beyond Room. */
class AppContainer(context: Context) {
    private val database = KriboDatabase.create(context)
    val noteRepository = NoteRepository(database.noteDao())
    val billingManager = BillingManager(context)
}
