package com.blackeye.kribo

import android.app.Application
import com.blackeye.kribo.security.AppSignatureVerifier

class KriboApplication : Application() {

    lateinit var container: AppContainer
        private set

    /** Release builds show a blocking screen instead of killing the process. */
    var isSignatureAuthorized: Boolean = true
        private set

    override fun onCreate() {
        super.onCreate()

        // Release-only pinning. Debug remains unrestricted for local development.
        isSignatureAuthorized = if (BuildConfig.DEBUG) {
            true
        } else {
            AppSignatureVerifier.enforce(this)
        }

        container = AppContainer(this)
    }
}
