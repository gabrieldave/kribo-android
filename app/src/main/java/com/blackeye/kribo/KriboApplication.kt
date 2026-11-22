package com.blackeye.kribo

import android.app.Application
import com.blackeye.kribo.security.AppSignatureVerifier

class KriboApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()

        // Signature pinning is enforced for release builds only.
        if (!BuildConfig.DEBUG) {
            AppSignatureVerifier.enforce(this)
        }

        container = AppContainer(this)
        container.billingManager.start()
    }
}
