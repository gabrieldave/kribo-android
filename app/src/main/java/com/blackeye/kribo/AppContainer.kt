package com.blackeye.kribo

import android.content.Context
import com.blackeye.kribo.billing.BillingManager
import com.blackeye.kribo.billing.EntitlementRepository
import com.blackeye.kribo.data.KriboDatabase
import com.blackeye.kribo.data.NoteRepository
import com.blackeye.kribo.drive.AndroidDriveNetworkMonitor
import com.blackeye.kribo.drive.DriveApiClient
import com.blackeye.kribo.drive.DriveAuthManager
import com.blackeye.kribo.drive.DriveAutoSyncScheduler
import com.blackeye.kribo.drive.DrivePrefs
import com.blackeye.kribo.drive.DriveSessionStore
import com.blackeye.kribo.drive.DriveSyncManager
import com.blackeye.kribo.drive.DriveSyncScheduler
import com.blackeye.kribo.drive.DriveTombstoneStore
import com.blackeye.kribo.drive.HttpDriveApiClient
import com.blackeye.kribo.importexport.ExportService
import com.blackeye.kribo.importexport.ImportService
import com.blackeye.kribo.ocr.AttachmentStore
import com.blackeye.kribo.ocr.OcrQuotaStore
import com.blackeye.kribo.ocr.OcrSubsystem
import com.blackeye.kribo.scan.DocumentPageProcessor
import com.blackeye.kribo.security.KeystoreSecureStore
import com.blackeye.kribo.transfer.WifiTransferServer
import com.blackeye.kribo.ui.preferences.UiPreferences
import com.blackeye.kribo.voice.VoiceModelManager
import java.io.File

/**
 * Manual dependency container with lazy properties.
 *
 * Application.onCreate() only instantiates this object: Room is not opened, the keystore is not
 * touched, Billing is not connected and neither ONNX Runtime nor the OCR models are loaded.
 * Everything materializes on first real use, keeping cold start inside the 250 ms budget.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val attachmentsRoot = File(appContext.filesDir, "attachments")

    private val database: KriboDatabase by lazy { KriboDatabase.create(appContext) }

    val attachmentStore: AttachmentStore by lazy { AttachmentStore(attachmentsRoot) }

    val driveTombstones: DriveTombstoneStore by lazy { DriveTombstoneStore(secureStore) }

    val noteRepository: NoteRepository by lazy {
        NoteRepository(
            database = database,
            attachmentsRoot = attachmentsRoot,
            onNoteDeleted = { noteId, deletedAt ->
                driveTombstones.mark(noteId, deletedAt)
            },
            onFolderDeleted = { folderId ->
                driveTombstones.markFolderDeleted(folderId)
            },
            onDataChanged = {
                driveAutoSyncScheduler.onLocalDataChanged()
            },
        )
    }

    val importService: ImportService by lazy {
        ImportService(noteRepository, attachmentStore)
    }

    val exportService: ExportService by lazy { ExportService(noteRepository) }

    val ocrSubsystem: OcrSubsystem by lazy { OcrSubsystem(appContext) }

    /** UVDoc + spine splitter, created only when a scan starts. */
    val pageProcessor: DocumentPageProcessor by lazy { DocumentPageProcessor(appContext) }

    /** Scratch space for raw scanner pages and UVDoc output. Cleared per scan. */
    val scanWorkRoot: File
        get() = File(appContext.cacheDir, "scan-work")

    private val secureStore by lazy { KeystoreSecureStore(appContext) }

    val quotaStore: OcrQuotaStore by lazy { OcrQuotaStore(secureStore) }

    val billingManager: BillingManager by lazy { BillingManager(appContext) }

    val entitlementRepository: EntitlementRepository by lazy {
        EntitlementRepository(billingManager, secureStore)
    }

    val wifiTransferServer: WifiTransferServer by lazy {
        WifiTransferServer(appContext, noteRepository, exportService)
    }

    val drivePrefs by lazy { DrivePrefs(appContext) }

    val driveSessionStore by lazy { DriveSessionStore(secureStore) }

    val driveAuthManager by lazy { DriveAuthManager(appContext, driveSessionStore) }

    val driveApiClient: DriveApiClient by lazy {
        HttpDriveApiClient { driveAuthManager.validToken() }
    }

    val driveSyncManager: DriveSyncManager by lazy {
        DriveSyncManager(
            repository = noteRepository,
            attachmentStore = attachmentStore,
            api = driveApiClient,
            tombstones = driveTombstones,
            auth = driveAuthManager,
        )
    }

    val driveSyncScheduler by lazy { DriveSyncScheduler(appContext, drivePrefs) }

    val driveAutoSyncScheduler: DriveAutoSyncScheduler by lazy {
        DriveAutoSyncScheduler(
            syncAction = { driveSyncManager.sync() },
            isPro = { entitlementRepository.isPro.value },
            isDriveConnected = { driveAuthManager.validToken() != null },
            isAutoSyncEnabled = { drivePrefs.autoSync },
            isWifiOnly = { drivePrefs.wifiOnly },
            network = AndroidDriveNetworkMonitor(appContext),
        )
    }

    /** UI-only preferences (theme + home layout + dictation engine); never touches Room, OCR or secure storage. */
    val uiPreferences: UiPreferences by lazy { UiPreferences(appContext) }

    /** On-demand Moonshine MIT streaming voice pack manager (`filesDir/voice_models/<lang>/`). */
    val voiceModelManager: VoiceModelManager by lazy {
        VoiceModelManager(File(appContext.filesDir, VoiceModelManager.MODELS_DIR_NAME))
    }
}
