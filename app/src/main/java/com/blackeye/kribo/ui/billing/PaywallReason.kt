package com.blackeye.kribo.ui.billing

/** What the user tried to do when the PRO paywall appeared. */
enum class PaywallReason {
    OCR_LIMIT,
    FOLDERS_LIMIT,
    PHOTO_LIMIT,
    THEME_LOCKED,
    BACKUP_LOCKED,
    WIFI_LOCKED,
    DRIVE_LOCKED,
    GENERIC,
}
