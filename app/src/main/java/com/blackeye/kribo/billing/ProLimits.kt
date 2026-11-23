package com.blackeye.kribo.billing

import com.blackeye.kribo.ui.preferences.AppTheme

/** The exact Free vs PRO matrix. Pure functions so every gate is unit-tested. */
object ProLimits {
    const val FREE_OCR_LIMIT = 5
    const val MAX_FREE_CUSTOM_FOLDERS = 3
    const val MAX_FREE_MANUAL_PHOTOS = 1

    val PRO_THEMES: Set<AppTheme> = AppTheme.entries
        .filter { it != AppTheme.OBSIDIAN && it != AppTheme.PURE_LIGHT }
        .toSet()

    fun canScan(usedScans: Int, isPro: Boolean): Boolean =
        isPro || usedScans < FREE_OCR_LIMIT

    fun canCreateFolder(customFolderCount: Int, isPro: Boolean): Boolean =
        isPro || customFolderCount < MAX_FREE_CUSTOM_FOLDERS

    fun canAttachManualPhoto(manualPhotoCount: Int, isPro: Boolean): Boolean =
        isPro || manualPhotoCount < MAX_FREE_MANUAL_PHOTOS

    fun canUseTheme(theme: AppTheme, isPro: Boolean): Boolean =
        isPro || theme !in PRO_THEMES

    fun canExportBackup(isPro: Boolean): Boolean = isPro

    fun canUseWifi(isPro: Boolean): Boolean = isPro

    fun canUseDrive(isPro: Boolean): Boolean = isPro
}
