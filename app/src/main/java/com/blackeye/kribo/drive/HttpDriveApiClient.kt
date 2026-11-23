package com.blackeye.kribo.drive

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Minimal Drive REST v3 client over HTTPS. No Drive SDK, no Google API client,
 * no extra startup weight. Every request is authorized with the short-lived
 * token obtained from DriveAuthManager.
 */
class HttpDriveApiClient(
    private val tokenProvider: suspend () -> String?,
) : DriveApiClient {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun ensureRootFolder(existingId: String?): DriveFile {
        if (!existingId.isNullOrBlank()) {
            runCatching { getFile(existingId) }.getOrNull()?.let { return it }
        }
        val found = searchFiles(
            "name='Kribo' and mimeType='$FOLDER_MIME' and trashed=false",
        ).firstOrNull { it.appProperties["kriboRoot"] == "true" }
        if (found != null) return found

        val metadata = buildJsonObject {
            put("name", "Kribo")
            put("mimeType", FOLDER_MIME)
            putJsonObject("appProperties") { put("kriboRoot", "true") }
        }
        val bytes = execute(
            url = "$BASE/files?fields=$FIELDS",
            method = "POST",
            contentType = "application/json; charset=UTF-8",
            body = metadata.toString().toByteArray(Charsets.UTF_8),
        )
        return parseFile(bytes.toString(Charsets.UTF_8))
    }

    override suspend fun listFiles(folderId: String): List<DriveFile> =
        searchFiles("'$folderId' in parents and trashed=false")

    override suspend fun downloadText(fileId: String): String =
        downloadBytes(fileId).toString(Charsets.UTF_8)

    override suspend fun downloadBytes(fileId: String): ByteArray =
        execute("$BASE/files/$fileId?alt=media", "GET", null, null)

    override suspend fun createFile(
        folderId: String,
        name: String,
        mimeType: String,
        bytes: ByteArray,
        appProperties: Map<String, String>,
    ): DriveFile {
        val metadata = buildJsonObject {
            put("name", name)
            put("mimeType", mimeType)
            putJsonArray("parents") { add(JsonPrimitive(folderId)) }
            if (appProperties.isNotEmpty()) {
                putJsonObject("appProperties") {
                    appProperties.forEach { (key, value) -> put(key, value) }
                }
            }
        }
        val body = multipartBody(metadata.toString(), mimeType, bytes)
        val result = execute(
            url = "$UPLOAD/files?uploadType=multipart&fields=$FIELDS",
            method = "POST",
            contentType = "multipart/related; boundary=$BOUNDARY",
            body = body,
        )
        return parseFile(result.toString(Charsets.UTF_8))
    }

    override suspend fun updateFile(
        fileId: String,
        name: String,
        mimeType: String,
        bytes: ByteArray,
        appProperties: Map<String, String>,
    ): DriveFile {
        val metadata = buildJsonObject {
            put("name", name)
            put("mimeType", mimeType)
            if (appProperties.isNotEmpty()) {
                putJsonObject("appProperties") {
                    appProperties.forEach { (key, value) -> put(key, value) }
                }
            }
        }
        val body = multipartBody(metadata.toString(), mimeType, bytes)
        val result = execute(
            url = "$UPLOAD/files/$fileId?uploadType=multipart&fields=$FIELDS",
            method = "PATCH",
            contentType = "multipart/related; boundary=$BOUNDARY",
            body = body,
        )
        return parseFile(result.toString(Charsets.UTF_8))
    }

    override suspend fun deleteFile(fileId: String) {
        execute("$BASE/files/$fileId", "DELETE", null, null)
    }

    private suspend fun searchFiles(query: String): List<DriveFile> {
        val encoded = URLEncoder.encode(query, "UTF-8").replace("+", "%20")
        val bytes = execute(
            "$BASE/files?q=$encoded&pageSize=1000&spaces=drive&fields=files($FIELDS)",
            "GET",
            null,
            null,
        )
        val root = json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
        return root["files"]?.jsonArray?.mapNotNull { element ->
            runCatching { parseFile(element.jsonObject) }.getOrNull()
        }.orEmpty()
    }

    private suspend fun getFile(fileId: String): DriveFile {
        val bytes = execute("$BASE/files/$fileId?fields=$FIELDS", "GET", null, null)
        return parseFile(bytes.toString(Charsets.UTF_8))
    }

    private fun parseFile(text: String): DriveFile {
        val obj = json.parseToJsonElement(text).jsonObject
        return parseFile(obj)
    }

    private fun parseFile(obj: kotlinx.serialization.json.JsonObject): DriveFile {
        val properties = obj["appProperties"]?.jsonObject?.mapValues { (_, value) ->
            runCatching { value.jsonPrimitive.content }.getOrDefault("")
        }.orEmpty()
        return DriveFile(
            id = obj["id"]?.jsonPrimitive?.content.orEmpty(),
            name = obj["name"]?.jsonPrimitive?.content.orEmpty(),
            mimeType = obj["mimeType"]?.jsonPrimitive?.content,
            modifiedTime = obj["modifiedTime"]?.jsonPrimitive?.content,
            appProperties = properties,
            md5Checksum = obj["md5Checksum"]?.jsonPrimitive?.content,
        )
    }

    private suspend fun execute(
        url: String,
        method: String,
        contentType: String?,
        body: ByteArray?,
    ): ByteArray {
        require(url.startsWith("https://")) { "Drive requests must use HTTPS" }
        val token = tokenProvider() ?: throw DriveAuthRequiredException()
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            if (contentType != null) connection.setRequestProperty("Content-Type", contentType)
            if (body != null) {
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.use { it.readBytes() } ?: ByteArray(0)
            if (code !in 200..299) {
                throw DriveApiException(
                    code,
                    response.toString(Charsets.UTF_8).take(600),
                )
            }
            return response
        } finally {
            connection.disconnect()
        }
    }

    private fun multipartBody(metadata: String, mimeType: String, bytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        val boundary = BOUNDARY
        output.write("--$boundary\r\n".toByteArray())
        output.write("Content-Type: application/json; charset=UTF-8\r\n\r\n".toByteArray())
        output.write(metadata.toByteArray(Charsets.UTF_8))
        output.write("\r\n--$boundary\r\n".toByteArray())
        output.write("Content-Type: $mimeType\r\n\r\n".toByteArray())
        output.write(bytes)
        output.write("\r\n--$boundary--\r\n".toByteArray())
        return output.toByteArray()
    }

    private companion object {
        const val BASE = "https://www.googleapis.com/drive/v3"
        const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        const val FOLDER_MIME = "application/vnd.google-apps.folder"
        const val FIELDS = "id,name,mimeType,modifiedTime,appProperties,md5Checksum"
        val BOUNDARY = "kribo-${UUID.randomUUID()}"
    }
}
