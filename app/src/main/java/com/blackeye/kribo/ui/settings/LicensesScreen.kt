package com.blackeye.kribo.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.blackeye.kribo.R
import com.blackeye.kribo.ui.components.AppScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val licenses by produceState(initialValue = "") {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open("licenses/THIRD_PARTY_NOTICES.txt")
                    .bufferedReader()
                    .use { it.readText() }
            }.getOrDefault("")
        }
    }

    AppScreen(
        title = stringResource(R.string.licenses_title),
        onBack = onBack,
    ) {
        Text(
            text = licenses,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
