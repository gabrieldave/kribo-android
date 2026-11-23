package com.blackeye.kribo.drive

class DriveApiException(val statusCode: Int, message: String) : Exception(message)
class DriveAuthRequiredException : Exception("Google Drive authorization required")

interface DriveApiClient {
    suspend fun ensureRootFolder(existingId: String?): DriveFile
    suspend fun listFiles(folderId: String): List<DriveFile>
    suspend fun downloadText(fileId: String): String
    suspend fun downloadBytes(fileId: String): ByteArray
    suspend fun createFile(
        folderId: String,
        name: String,
        mimeType: String,
        bytes: ByteArray,
        appProperties: Map<String, String> = emptyMap(),
    ): DriveFile
    suspend fun updateFile(
        fileId: String,
        name: String,
        mimeType: String,
        bytes: ByteArray,
        appProperties: Map<String, String> = emptyMap(),
    ): DriveFile
    suspend fun deleteFile(fileId: String)
}
