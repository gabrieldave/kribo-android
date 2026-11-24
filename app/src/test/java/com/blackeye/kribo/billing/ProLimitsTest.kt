package com.blackeye.kribo.billing

import com.blackeye.kribo.ui.preferences.AppTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProLimitsTest {

    @Test
    fun ocrAllowsFiveFreeScans() {
        assertTrue(ProLimits.canScan(0, isPro = false))
        assertTrue(ProLimits.canScan(4, isPro = false))
        assertFalse(ProLimits.canScan(5, isPro = false))
        assertTrue(ProLimits.canScan(999, isPro = true))
    }

    @Test
    fun foldersAllowThreeCustomFolders() {
        assertTrue(ProLimits.canCreateFolder(0, isPro = false))
        assertTrue(ProLimits.canCreateFolder(2, isPro = false))
        assertFalse(ProLimits.canCreateFolder(3, isPro = false))
        assertTrue(ProLimits.canCreateFolder(20, isPro = true))
    }

    @Test
    fun oneManualPhotoPerNoteOnFree() {
        assertTrue(ProLimits.canAttachManualPhoto(0, isPro = false))
        assertFalse(ProLimits.canAttachManualPhoto(1, isPro = false))
        assertTrue(ProLimits.canAttachManualPhoto(10, isPro = true))
    }

    @Test
    fun proThemesRequirePurchaseButOtherThemesAreFree() {
        assertTrue(ProLimits.canUseTheme(AppTheme.OBSIDIAN, isPro = false))
        assertTrue(ProLimits.canUseTheme(AppTheme.PURE_LIGHT, isPro = false))
        assertFalse(ProLimits.canUseTheme(AppTheme.EMERALD, isPro = false))
        assertFalse(ProLimits.canUseTheme(AppTheme.WARM_PAPER, isPro = false))
        assertFalse(ProLimits.canUseTheme(AppTheme.AMOLED, isPro = false))
        assertTrue(ProLimits.canUseTheme(AppTheme.AMOLED, isPro = true))
    }

    @Test
    fun backupIsFreeWhileWifiAndDriveAreProOnly() {
        assertTrue("ZIP export is part of the free plan", ProLimits.canExportBackup(isPro = false))
        assertTrue(ProLimits.canExportBackup(isPro = true))
        assertFalse(ProLimits.canUseWifi(isPro = false))
        assertFalse(ProLimits.canUseDrive(isPro = false))
        assertTrue(ProLimits.canUseWifi(isPro = true))
        assertTrue(ProLimits.canUseDrive(isPro = true))
    }
}
