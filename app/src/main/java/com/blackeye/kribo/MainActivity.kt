package com.blackeye.kribo

import android.os.Bundle
import android.view.Choreographer
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import com.blackeye.kribo.billing.ProLimits
import com.blackeye.kribo.ui.KriboApp
import com.blackeye.kribo.ui.preferences.AppTheme
import com.blackeye.kribo.ui.theme.KriboTheme

class MainActivity : AppCompatActivity() {

    private val container: AppContainer
        get() = (application as KriboApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        // No keep-on-screen condition and no artificial delay: the splash hands over immediately.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val theme by container.uiPreferences.theme.collectAsStateWithLifecycle()
            val isPro by container.entitlementRepository.isPro.collectAsStateWithLifecycle()
            val resolved by container.entitlementRepository.resolved.collectAsStateWithLifecycle()
            LaunchedEffect(theme, isPro, resolved) {
                if (resolved && !isPro && theme in ProLimits.PRO_THEMES) {
                    container.uiPreferences.setTheme(AppTheme.OBSIDIAN)
                }
            }
            KriboTheme(theme = theme) {
                val billingState by container.billingManager.state.collectAsStateWithLifecycle()
                KriboApp(
                    container = container,
                    billingState = billingState,
                    isPro = isPro,
                    onPurchase = { container.entitlementRepository.launchPurchase(this) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Defer Billing until after the first frame so cold start stays under 250 ms.
        Choreographer.getInstance().postFrameCallback {
            container.entitlementRepository.refresh()
        }
    }
}
