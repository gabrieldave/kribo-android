package com.blackeye.kribo

import android.content.Context
import com.blackeye.kribo.billing.BillingManager
import com.blackeye.kribo.billing.EntitlementRepository
import com.blackeye.kribo.data.KriboDatabase
import com.blackeye.kribo.data.NoteRepository
import com.blackeye.kribo.importexport.ExportService
import com.blackeye.kribo.importexport.ImportService
import com.blackeye.kribo.ocr.AttachmentStore
import com.blackeye.kribo.ocr.OcrQuotaStore
import com.blackeye.kribo.ocr.OcrSubsystem
import com.blackeye.kribo.scan.DocumentPageProcessor
import com.blackeye.kribo.security.KeystoreSecureStore
import com.blackeye.kribo.transfer.WifiTransferServer
import com.blackeye.kribo.ui.preferences.UiPreferences
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

    val noteRepository: NoteRepository by lazy { NoteRepository(database, attachmentsRoot) }

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

    /** UI-only preferences (theme + home layout); never touches Room, OCR or secure storage. */
    val uiPreferences: UiPreferences by lazy { UiPreferences(appContext) }
}
