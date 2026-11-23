package com.blackeye.kribo.drive

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

interface DriveAuthSource {
    fun validToken(): String?
    fun sessionSnapshot(): DriveSession
    fun saveRootFolder(id: String)
    fun saveLastSync(at: Long, notes: Int, attachments: Int)
}

sealed interface DriveAuthOutcome {
    data object Connected : DriveAuthOutcome
    data class Resolution(val pendingIntent: PendingIntent) : DriveAuthOutcome
    data object Cancelled : DriveAuthOutcome
    data object Unavailable : DriveAuthOutcome
    data object OAuthConfiguration : DriveAuthOutcome
    data object Error : DriveAuthOutcome
}

/**
 * Modern Google Identity authorization for the non-sensitive `drive.file` scope.
 * Android pairs the app with the OAuth client using applicationId + signing SHA-1;
 * there is no client secret anywhere in the APK.
 */
class DriveAuthManager(
    context: Context,
    private val store: DriveSessionStore,
) : DriveAuthSource {
    private val appContext = context.applicationContext
    private val _session = MutableStateFlow(store.load())
    val session: StateFlow<DriveSession> = _session.asStateFlow()

    suspend fun authorize(activity: Activity): DriveAuthOutcome = withContext(Dispatchers.IO) {
        runCatching {
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
                .build()
            val result = Tasks.await(
                Identity.getAuthorizationClient(activity).authorize(request),
                45,
                TimeUnit.SECONDS,
            )
            when {
                result.hasResolution() -> result.pendingIntent?.let {
                    DriveAuthOutcome.Resolution(it)
                } ?: DriveAuthOutcome.Error

                result.accessToken != null -> {
                    persist(result)
                    DriveAuthOutcome.Connected
                }

                else -> DriveAuthOutcome.Unavailable
            }
        }.getOrElse(::mapException)
    }

    fun completeAuthorization(activity: Activity, intent: Intent): DriveAuthOutcome =
        runCatching {
            val result = Identity.getAuthorizationClient(activity)
                .getAuthorizationResultFromIntent(intent)
            if (result.accessToken != null) {
                persist(result)
                DriveAuthOutcome.Connected
            } else {
                DriveAuthOutcome.Unavailable
            }
        }.getOrElse(::mapException)

    override fun validToken(): String? {
        val session = _session.value
        val token = session.accessToken
        if (token.isNullOrBlank()) return null
        if (session.tokenExpiry <= System.currentTimeMillis() + 60_000L) return null
        return token
    }

    fun disconnect() {
        store.clear()
        _session.value = DriveSession()
    }

    override fun sessionSnapshot(): DriveSession = _session.value

    override fun saveRootFolder(id: String) {
        store.saveRootFolder(id)
        _session.value = _session.value.copy(rootFolderId = id)
    }

    override fun saveLastSync(at: Long, notes: Int, attachments: Int) {
        store.saveLastSync(at, notes, attachments)
        _session.value = _session.value.copy(
            lastSyncAt = at,
            lastNotes = notes,
            lastAttachments = attachments,
        )
    }

    private fun persist(result: AuthorizationResult) {
        val token = result.accessToken ?: return
        val email = runCatching { result.toGoogleSignInAccount()?.email }.getOrNull()
        val expiresIn = runCatching {
            when (val raw = result.tokenResponseParams?.get("expires_in")) {
                is Long -> raw
                is Int -> raw.toLong()
                is String -> raw.toLongOrNull() ?: 0L
                else -> 0L
            }
        }.getOrDefault(0L).takeIf { it > 0 } ?: 3300L
        val expiry = System.currentTimeMillis() + expiresIn * 1000L
        store.saveAuth(email, token, expiry)
        _session.value = _session.value.copy(
            email = email ?: _session.value.email,
            accessToken = token,
            tokenExpiry = expiry,
        )
    }

    private fun mapException(throwable: Throwable): DriveAuthOutcome {
        val api = throwable as? ApiException
        return when (api?.statusCode) {
            CommonStatusCodes.CANCELED -> DriveAuthOutcome.Cancelled
            CommonStatusCodes.DEVELOPER_ERROR -> DriveAuthOutcome.OAuthConfiguration
            else -> DriveAuthOutcome.Error
        }
    }

    companion object {
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    }
}
