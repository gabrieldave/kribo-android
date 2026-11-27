package com.blackeye.kribo.drive

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class AutoSyncUiState {
    IDLE,
    SYNCING,
    SYNCED,
}

interface DriveNetworkMonitor {
    fun isOnline(): Boolean
    fun isUnmeteredWifi(): Boolean
    fun registerForReconnect(onAvailable: () -> Unit)
    fun unregister()
}

/**
 * Lazy ConnectivityManager wrapper: registers a callback only while a sync is queued waiting
 * for network/Wi-Fi, so cold start never touches ConnectivityManager.
 */
class AndroidDriveNetworkMonitor(context: Context) : DriveNetworkMonitor {
    private val appContext = context.applicationContext
    private val lock = Any()
    private var activeCallback: ConnectivityManager.NetworkCallback? = null

    private fun connectivityManager(): ConnectivityManager? =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    override fun isOnline(): Boolean {
        val cm = connectivityManager() ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun isUnmeteredWifi(): Boolean {
        val cm = connectivityManager() ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val notMetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        val notCellular = !caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        return hasInternet && notMetered && notCellular
    }

    override fun registerForReconnect(onAvailable: () -> Unit) {
        synchronized(lock) {
            if (activeCallback != null) return
            val cm = connectivityManager() ?: return
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    onAvailable()
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities,
                ) {
                    onAvailable()
                }
            }
            val registered = runCatching {
                cm.registerDefaultNetworkCallback(callback)
                true
            }.getOrDefault(false)
            if (registered) {
                activeCallback = callback
            }
        }
    }

    override fun unregister() {
        synchronized(lock) {
            val callback = activeCallback ?: return
            activeCallback = null
            runCatching {
                connectivityManager()?.unregisterNetworkCallback(callback)
            }
        }
    }
}

/**
 * Automatic Google Drive sync coordinator mirroring Kribo Web:
 *  - Debounces local repository edits by [debounceMs] (6 seconds) before syncing silently.
 *  - Syncs on returning to foreground with at least [foregroundCooldownMs] (45 seconds) cooldown.
 *  - Strictly requires active Kribo Pro, connected Drive session with valid token, and autoSync ON.
 *  - Respects Wi-Fi Only: queues and retries automatically when eligible connectivity returns.
 *  - Prevents overlapping sync runs via [Mutex] + [AtomicBoolean] with single follow-up coalescing.
 */
class DriveAutoSyncScheduler(
    private val syncAction: suspend () -> DriveSyncResult,
    private val isPro: () -> Boolean,
    private val isDriveConnected: () -> Boolean,
    private val isAutoSyncEnabled: () -> Boolean,
    private val isWifiOnly: () -> Boolean,
    private val network: DriveNetworkMonitor,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val debounceMs: Long = DEFAULT_DEBOUNCE_MS,
    private val foregroundCooldownMs: Long = DEFAULT_FOREGROUND_COOLDOWN_MS,
    private val syncedBadgeMs: Long = DEFAULT_SYNCED_BADGE_MS,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow(AutoSyncUiState.IDLE)
    val state: StateFlow<AutoSyncUiState> = _state.asStateFlow()

    private val syncMutex = Mutex()
    private val inFlight = AtomicBoolean(false)
    private val rerunRequested = AtomicBoolean(false)
    private val pendingRetry = AtomicBoolean(false)
    @Volatile
    private var lastSyncAttemptAt = 0L

    private val jobLock = Any()
    private var debounceJob: Job? = null
    private var badgeJob: Job? = null

    val hasPendingRetry: Boolean
        get() = pendingRetry.get()

    /** Called by NoteRepository after any user-initiated note, folder or attachment write. */
    fun onLocalDataChanged() {
        if (!canAutoSync()) return
        synchronized(jobLock) {
            debounceJob?.cancel()
            debounceJob = scope.launch {
                delay(debounceMs)
                // Launch independently so future edits only reset the debounce timer and
                // never cancel an in-flight Drive upload/download.
                scope.launch { runSync(force = true) }
            }
        }
    }

    /** Called after the first frame on resume; throttled by [foregroundCooldownMs]. */
    fun onAppForeground() {
        if (!canAutoSync()) return
        val dueToRetry = pendingRetry.get() && hasEligibleNetwork()
        val cooledDown = nowMs() - lastSyncAttemptAt >= foregroundCooldownMs
        if (!dueToRetry && !cooledDown) return
        scope.launch {
            runSync(force = dueToRetry)
        }
    }

    /** Called when auto-sync / Wi-Fi-only switches change or after re-authorizing Drive. */
    fun onSettingsChanged() {
        if (!canAutoSync()) {
            cancelScheduledJobs()
            pendingRetry.set(false)
            rerunRequested.set(false)
            network.unregister()
            _state.value = AutoSyncUiState.IDLE
            return
        }
        if (pendingRetry.get() && hasEligibleNetwork()) {
            scope.launch { runSync(force = true) }
        }
    }

    /** Called when the user disconnects Google Drive. */
    fun onDisconnected() {
        cancelScheduledJobs()
        pendingRetry.set(false)
        rerunRequested.set(false)
        lastSyncAttemptAt = 0L
        network.unregister()
        _state.value = AutoSyncUiState.IDLE
    }

    private fun canAutoSync(): Boolean =
        isPro() && isAutoSyncEnabled() && isDriveConnected()

    private fun hasEligibleNetwork(): Boolean =
        if (isWifiOnly()) network.isUnmeteredWifi() else network.isOnline()

    private fun onNetworkAvailable() {
        if (!pendingRetry.get()) {
            network.unregister()
            return
        }
        if (!canAutoSync()) {
            pendingRetry.set(false)
            network.unregister()
            return
        }
        if (!hasEligibleNetwork()) return
        scope.launch { runSync(force = true) }
    }

    private suspend fun runSync(force: Boolean) {
        if (!canAutoSync()) {
            pendingRetry.set(false)
            network.unregister()
            return
        }
        if (!hasEligibleNetwork()) {
            pendingRetry.set(true)
            network.registerForReconnect(::onNetworkAvailable)
            return
        }
        if (!force && nowMs() - lastSyncAttemptAt < foregroundCooldownMs) return

        if (!inFlight.compareAndSet(false, true)) {
            rerunRequested.set(true)
            return
        }

        syncMutex.withLock {
            try {
                do {
                    rerunRequested.set(false)
                    if (!canAutoSync()) {
                        pendingRetry.set(false)
                        network.unregister()
                        _state.value = AutoSyncUiState.IDLE
                        return
                    }
                    if (!hasEligibleNetwork()) {
                        pendingRetry.set(true)
                        network.registerForReconnect(::onNetworkAvailable)
                        _state.value = AutoSyncUiState.IDLE
                        return
                    }
                    pendingRetry.set(false)
                    network.unregister()
                    lastSyncAttemptAt = nowMs()
                    cancelBadgeJob()
                    _state.value = AutoSyncUiState.SYNCING

                    val result = runCatching { syncAction() }.getOrElse {
                        DriveSyncResult.Error(DriveSyncError.UNKNOWN)
                    }
                    when (result) {
                        is DriveSyncResult.Success -> {
                            if (!rerunRequested.get()) {
                                _state.value = AutoSyncUiState.SYNCED
                                scheduleBadgeReset()
                            }
                        }

                        is DriveSyncResult.Error -> {
                            if (result.kind == DriveSyncError.NETWORK) {
                                pendingRetry.set(true)
                                network.registerForReconnect(::onNetworkAvailable)
                            }
                            _state.value = AutoSyncUiState.IDLE
                        }

                        DriveSyncResult.AuthRequired -> {
                            _state.value = AutoSyncUiState.IDLE
                        }
                    }
                } while (rerunRequested.get())
            } finally {
                inFlight.set(false)
            }
        }
    }

    private fun scheduleBadgeReset() {
        synchronized(jobLock) {
            badgeJob?.cancel()
            badgeJob = scope.launch {
                delay(syncedBadgeMs)
                if (_state.value == AutoSyncUiState.SYNCED) {
                    _state.value = AutoSyncUiState.IDLE
                }
            }
        }
    }

    private fun cancelBadgeJob() {
        synchronized(jobLock) {
            badgeJob?.cancel()
            badgeJob = null
        }
    }

    private fun cancelScheduledJobs() {
        synchronized(jobLock) {
            debounceJob?.cancel()
            debounceJob = null
            badgeJob?.cancel()
            badgeJob = null
        }
    }

    companion object {
        const val DEFAULT_DEBOUNCE_MS = 6_000L
        const val DEFAULT_FOREGROUND_COOLDOWN_MS = 45_000L
        const val DEFAULT_SYNCED_BADGE_MS = 3_500L
    }
}
