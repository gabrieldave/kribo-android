package com.blackeye.kribo.ocr

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Owns `filesDir/attachments/<noteId>/`. Scan pages are downscaled and recompressed before
 * being persisted, so OCR and exports work on predictable sizes.
 */
class AttachmentStore(private val attachmentsRoot: File) {

    fun noteDir(noteId: Long): File = File(attachmentsRoot, noteId.toString()).apply { mkdirs() }

    fun saveBytes(noteId: Long, fileName: String, bytes: ByteArray): File {
        val cleanName = File(fileName).name.ifBlank { "attachment-${System.currentTimeMillis()}" }
        val target = uniqueFile(noteDir(noteId), cleanName)
        target.writeBytes(bytes)
        return target
    }

    /**
     * Copies a gallery/camera picture into private storage, downscaled to a sane long side and
     * recompressed as clean JPEG. Nothing is written outside `filesDir/attachments/<noteId>/`.
     */
    suspend fun saveImage(
        contentResolver: ContentResolver,
        noteId: Long,
        uri: Uri,
    ): File? = withContext(Dispatchers.IO) {
        val bitmap = decodeScaled(contentResolver, uri, MAX_IMAGE_LONG_SIDE) ?: return@withContext null
        try {
            val target = uniqueFile(
                noteDir(noteId),
                "image-${System.currentTimeMillis()}.jpg",
            )
            val written = runCatching {
                target.outputStream().use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, IMAGE_QUALITY, stream)
                }
            }.getOrDefault(false)
            if (written && target.length() > 0L) target else {
                target.delete()
                null
            }
        } finally {
            bitmap.recycle()
        }
    }

    suspend fun saveScanPage(
        contentResolver: ContentResolver,
        noteId: Long,
        uri: Uri,
        pageIndex: Int,
    ): File? = withContext(Dispatchers.IO) {
        val bitmap = decodeScaled(contentResolver, uri, MAX_SCAN_LONG_SIDE) ?: return@withContext null
        try {
            val target = uniqueFile(
                noteDir(noteId),
                "scan-${System.currentTimeMillis()}-${pageIndex + 1}.jpg",
            )
            val written = runCatching {
                target.outputStream().use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, SCAN_QUALITY, stream)
                }
            }.getOrDefault(false)
            if (written && target.length() > 0L) target else {
                target.delete()
                null
            }
        } finally {
            bitmap.recycle()
        }
    }

    fun deleteNote(noteId: Long) {
        File(attachmentsRoot, noteId.toString()).deleteRecursively()
    }

    fun deleteAll() {
        attachmentsRoot.deleteRecursively()
    }

    /** Never overwrites an existing attachment: `foto.jpg` → `foto-1.jpg`, `foto-2.jpg`… */
    private fun uniqueFile(directory: File, fileName: String): File {
        val base = fileName.substringBeforeLast('.', fileName)
        val extension = fileName.substringAfterLast('.', "")
        val suffix = if (extension.isBlank()) "" else ".$extension"
        var candidate = File(directory, fileName)
        var index = 1
        while (candidate.exists()) {
            candidate = File(directory, "$base-$index$suffix")
            index++
        }
        return candidate
    }

    private fun decodeScaled(
        contentResolver: ContentResolver,
        uri: Uri,
        maxLongSide: Int,
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        val longestSource = max(bounds.outWidth, bounds.outHeight)
        while (longestSource / (sample * 2) >= maxLongSide) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
        }
        var bitmap = runCatching {
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }.getOrNull() ?: return null

        val longest = max(bitmap.width, bitmap.height)
        if (longest > maxLongSide) {
            val ratio = maxLongSide.toFloat() / longest
            val scaled = Bitmap.createScaledBitmap(
                bitmap,
                max(1, (bitmap.width * ratio).toInt()),
                max(1, (bitmap.height * ratio).toInt()),
                true,
            )
            if (scaled !== bitmap) bitmap.recycle()
            bitmap = scaled
        }
        return bitmap
    }

    companion object {
        private const val MAX_SCAN_LONG_SIDE = 2400
        private const val SCAN_QUALITY = 90
        private const val MAX_IMAGE_LONG_SIDE = 2048
        private const val IMAGE_QUALITY = 88
    }
}
