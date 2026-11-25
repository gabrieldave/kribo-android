package com.blackeye.kribo.transfer

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.ui.graphics.toArgb
import com.blackeye.kribo.data.NoteEntity
import com.blackeye.kribo.data.NoteRepository
import com.blackeye.kribo.data.blocks.AttachmentBlock
import com.blackeye.kribo.data.blocks.BlockCodec
import com.blackeye.kribo.data.blocks.ParagraphBlock
import com.blackeye.kribo.data.blocks.TitleBlock
import com.blackeye.kribo.importexport.ExportService
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom
import java.util.Locale
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Tiny on-device HTTP server for local Wi-Fi transfer.
 *
 * Only runs while the user keeps the switch on. It binds to all local interfaces, requires a
 * 6-digit session PIN for every API call, and never talks to a cloud service.
 */
class WifiTransferServer(
    private val context: Context,
    private val repository: NoteRepository,
    private val exportService: ExportService,
) {

    @Serializable
    data class State(
        val running: Boolean = false,
        val port: Int = 0,
        val pin: String = "",
        val ip: String? = null,
        val wifi: Boolean = false,
        val startFailed: Boolean = false,
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private var sessionPin: String = ""
    private var sessionToken: String = ""

    private val _state = kotlinx.coroutines.flow.MutableStateFlow(State())
    val state: kotlinx.coroutines.flow.StateFlow<State> = _state

    @Synchronized
    fun start(port: Int = DEFAULT_PORT): Boolean {
        if (_state.value.running) return true
        return runCatching {
            val socket = ServerSocket()
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(port))
            serverSocket = socket
            sessionPin = "%06d".format(Locale.US, Random.nextInt(1_000_000))
            sessionToken = newSessionToken()
            val (ip, wifi) = networkInfo()
            _state.value = State(
                running = true,
                port = socket.localPort,
                pin = sessionPin,
                ip = ip,
                wifi = wifi,
            )
            acceptJob = scope.launch { acceptLoop(socket) }
            true
        }.getOrElse {
            runCatching { serverSocket?.close() }
            serverSocket = null
            _state.value = State(startFailed = true)
            false
        }
    }

    @Synchronized
    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        sessionPin = ""
        sessionToken = ""
        _state.value = State()
    }

    fun close() {
        stop()
        scope.cancel()
    }

    private suspend fun acceptLoop(socket: ServerSocket) {
        while (scope.isActive) {
            val client = runCatching { socket.accept() }.getOrNull() ?: break
            scope.launch { handle(client) }
        }
    }

    private suspend fun handle(client: Socket) {
        client.use { socket ->
            runCatching {
                socket.soTimeout = 15_000
                val input = BufferedInputStream(socket.getInputStream())
                val requestLine = readLine(input) ?: return@runCatching
                val parts = requestLine.split(' ')
                if (parts.size < 2) return@runCatching
                val method = parts[0].uppercase(Locale.ROOT)
                val rawPath = parts[1]
                val headers = mutableMapOf<String, String>()
                while (true) {
                    val line = readLine(input) ?: break
                    if (line.isEmpty()) break
                    val colon = line.indexOf(':')
                    if (colon > 0) {
                        headers[line.substring(0, colon).trim().lowercase(Locale.ROOT)] =
                            line.substring(colon + 1).trim()
                    }
                }
                val contentLength = headers["content-length"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                val body = if (contentLength > 0) {
                    val buffer = ByteArray(contentLength.coerceAtMost(2_000_000))
                    var read = 0
                    while (read < buffer.size) {
                        val n = input.read(buffer, read, buffer.size - read)
                        if (n < 0) break
                        read += n
                    }
                    buffer.copyOf(read)
                } else {
                    ByteArray(0)
                }

                val path = rawPath.substringBefore('?')
                val query = parseQuery(rawPath.substringAfter('?', ""))
                val cookieValues = headers["cookie"]?.split(';')?.map { it.trim() }.orEmpty()
                val cookieCredential = cookieValues
                    .firstOrNull { it.startsWith("kribo_session=") }
                    ?.substringAfter('=')
                    ?: cookieValues
                        .firstOrNull { it.startsWith("kribo_pin=") }
                        ?.substringAfter('=')
                val credential = headers["x-kribo-pin"] ?: cookieCredential ?: query["pin"]
                val output = socket.getOutputStream()

                if (method == "OPTIONS") {
                    respond(output, "204 No Content", "text/plain", ByteArray(0))
                    return@runCatching
                }

                when {
                    method == "GET" && path == "/" -> {
                        val queryPin = query["pin"]
                        when {
                            queryPin != null && queryPin == sessionPin ->
                                redirect(output, "/", sessionCookie())

                            queryPin != null && !isAuthenticated(credential) ->
                                redirect(output, "/?error=1")

                            isAuthenticated(credential) ->
                                respondHtml(output, APP_HTML)

                            else -> respondHtml(output, LOGIN_HTML)
                        }
                    }

                    method == "GET" && path == "/api/login" -> {
                        if (query["pin"] == sessionPin) {
                            respond(
                                output,
                                "204 No Content",
                                "text/plain",
                                ByteArray(0),
                                mapOf("Set-Cookie" to sessionCookie()),
                            )
                        } else {
                            unauthorized(output)
                        }
                    }

                    !isAuthenticated(credential) -> unauthorized(output)

                    method == "GET" && path == "/api/notes" -> {
                        val queryText = query["q"].orEmpty().trim()
                        val notes = repository.getAllNotes()
                            .asSequence()
                            .filter { note -> queryText.isBlank() || note.matches(queryText) }
                            .map(::noteDto)
                            .toList()
                        respondJson(output, json.encodeToString(notes))
                    }

                    method == "GET" && NOTE_DETAIL.matches(path) -> {
                        val id = NOTE_DETAIL.matchEntire(path)!!.groupValues[1].toLong()
                        val note = repository.getNote(id)
                        if (note == null) {
                            respond(output, "404 Not Found", "text/plain", "Not found".toByteArray())
                        } else {
                            val blocks = BlockCodec.decode(note.blocksJson)
                            val attachments = repository.getAttachments(id).map { attachment ->
                                AttachmentDto(
                                    id = attachment.id,
                                    fileName = File(attachment.filePath).name,
                                    mimeType = attachment.mimeType,
                                    url = "/api/attachments/${attachment.id}",
                                )
                            }
                            val detail = NoteDetailDto(
                                id = note.id,
                                title = note.displayTitle(),
                                body = note.bodyWithoutTitle(),
                                bodyHtml = note.bodyHtml(),
                                ocrText = note.ocrTextIndex,
                                updatedAt = note.updatedAt,
                                editable = blocks.all {
                                    it is TitleBlock || it is ParagraphBlock
                                },
                                attachments = attachments,
                            )
                            respondJson(output, json.encodeToString(detail))
                        }
                    }

                    method == "GET" && path == "/api/folders" -> {
                        val folders = repository.getAllFolders().map { FolderDto(it.id, it.name) }
                        respondJson(output, json.encodeToString(folders))
                    }

                    method == "GET" && NOTE_EXPORT.matches(path) -> {
                        val id = NOTE_EXPORT.matchEntire(path)!!.groupValues[1].toLong()
                        val note = repository.getNote(id)
                        if (note == null) {
                            respond(output, "404 Not Found", "text/plain", "Not found".toByteArray())
                        } else {
                            val attachments = repository.getAttachments(id)
                            var markdown = exportService.renderMarkdown(
                                note,
                                BlockCodec.decode(note.blocksJson),
                                attachments,
                            )
                            attachments.forEach { attachment ->
                                markdown = markdown.replace(
                                    "attachments/${note.id}/${attachment.filePath.substringAfterLast('/')}",
                                    "/api/attachments/${attachment.id}",
                                )
                            }
                            val safe = note.title.ifBlank { "kribo-$id" }
                                .replace(Regex("[^\\p{L}\\p{N}._-]+"), "-")
                                .trim('-')
                                .ifBlank { "kribo-$id" }
                            respond(
                                output,
                                "200 OK",
                                "text/markdown; charset=utf-8",
                                markdown.toByteArray(Charsets.UTF_8),
                                mapOf("Content-Disposition" to "attachment; filename=\"$safe.md\""),
                            )
                        }
                    }

                    method == "GET" && ATTACHMENT.matches(path) -> {
                        val id = ATTACHMENT.matchEntire(path)!!.groupValues[1].toLong()
                        val attachment = repository.getAttachment(id)
                        val file = attachment?.let { File(it.filePath) }
                        if (attachment == null || file == null || !file.isFile) {
                            respond(output, "404 Not Found", "text/plain", "Not found".toByteArray())
                        } else {
                            respond(
                                output,
                                "200 OK",
                                attachment.mimeType,
                                file.readBytes(),
                                mapOf(
                                    "Content-Disposition" to
                                        "inline; filename=\"${file.name.replace("\"", "")}\"",
                                ),
                            )
                        }
                    }

                    method == "GET" && path == "/api/backup.zip" -> {
                        val buffer = ByteArrayOutputStream()
                        exportService.writeBackup(buffer)
                        respond(
                            output,
                            "200 OK",
                            "application/zip",
                            buffer.toByteArray(),
                            mapOf("Content-Disposition" to "attachment; filename=\"kribo-backup.zip\""),
                        )
                    }

                    method == "POST" && path == "/api/notes" -> {
                        val request = runCatching {
                            json.decodeFromString<CreateNoteRequest>(body.toString(Charsets.UTF_8))
                        }.getOrNull()
                        if (request == null) {
                            respond(output, "400 Bad Request", "text/plain", "Invalid JSON".toByteArray())
                        } else {
                            val blocks = buildList {
                                if (request.title.isNotBlank()) add(TitleBlock(text = request.title.trim()))
                                if (request.text.isNotBlank()) add(ParagraphBlock(text = request.text.trim()))
                            }
                            if (blocks.isEmpty()) {
                                respond(output, "400 Bad Request", "text/plain", "Empty note".toByteArray())
                            } else {
                                val id = repository.createNote(
                                    folderId = request.folderId,
                                    blocks = blocks,
                                    title = request.title.ifBlank { "Nota web" },
                                )
                                respondJson(output, "{\"id\":$id}")
                            }
                        }
                    }

                    method == "PUT" && NOTE_DETAIL.matches(path) -> {
                        val id = NOTE_DETAIL.matchEntire(path)!!.groupValues[1].toLong()
                        val request = runCatching {
                            json.decodeFromString<UpdateNoteRequest>(body.toString(Charsets.UTF_8))
                        }.getOrNull()
                        val note = repository.getNote(id)
                        when {
                            request == null -> respond(
                                output,
                                "400 Bad Request",
                                "text/plain",
                                "Invalid JSON".toByteArray(),
                            )

                            note == null -> respond(
                                output,
                                "404 Not Found",
                                "text/plain",
                                "Not found".toByteArray(),
                            )

                            else -> {
                                val existing = repository.getBlocks(id)
                                if (!existing.all { it is TitleBlock || it is ParagraphBlock }) {
                                    respond(
                                        output,
                                        "409 Conflict",
                                        "application/json; charset=utf-8",
                                        "{\"error\":\"rich_note\"}".toByteArray(Charsets.UTF_8),
                                    )
                                } else {
                                    val title = request.title.trim().take(120)
                                    val text = request.text.trim()
                                    val blocks = buildList {
                                        if (title.isNotBlank()) add(TitleBlock(text = title))
                                        if (text.isNotBlank()) add(ParagraphBlock(text = text))
                                    }
                                    if (blocks.isEmpty()) {
                                        respond(
                                            output,
                                            "400 Bad Request",
                                            "text/plain",
                                            "Empty note".toByteArray(),
                                        )
                                    } else {
                                        repository.saveBlocks(id, blocks)
                                        respondJson(output, "{\"id\":$id}")
                                    }
                                }
                            }
                        }
                    }

                    else -> respond(output, "404 Not Found", "text/plain", "Not found".toByteArray())
                }
            }.onFailure {
                // A broken client must never take the server down.
            }
        }
    }

    private fun isAuthenticated(credential: String?): Boolean =
        sessionPin.isNotBlank() &&
            (credential == sessionPin ||
                (sessionToken.isNotBlank() && credential == sessionToken))

    private fun noteDto(note: NoteEntity): NoteDto = NoteDto(
        id = note.id,
        title = note.displayTitle(),
        folderId = note.folderId,
        updatedAt = note.updatedAt,
        preview = note.previewText(),
        hasOcr = note.ocrTextIndex.isNotBlank(),
    )

    private fun NoteEntity.displayTitle(): String =
        title.ifBlank {
            plainTextIndex.lineSequence()
                .firstOrNull { it.isNotBlank() }
                ?.trim()
                .orEmpty()
                .ifBlank {
                    ocrTextIndex.lineSequence()
                        .firstOrNull { it.isNotBlank() }
                        ?.trim()
                        .orEmpty()
                }
                .take(80)
        }

    /** Body without repeating the headline that the UI already shows on its own. */
    private fun NoteEntity.bodyWithoutTitle(): String {
        val cleanTitle = displayTitle().trim()
        val lines = plainTextIndex.lineSequence().toList()
        val start = if (cleanTitle.isNotBlank() && lines.firstOrNull()?.trim() == cleanTitle) 1 else 0
        return lines.drop(start).joinToString("\n").trim()
    }

    /**
     * Safe HTML body (#42): every chunk is escaped, then only whitelisted tags
     * (`strong/em/u/s` and inline `color` from the [AccentTone] palette) are emitted.
     * No raw user HTML or URLs ever pass through.
     */
    private fun NoteEntity.bodyHtml(): String {
        val blocks = BlockCodec.decode(blocksJson)
        val out = StringBuilder()
        blocks.forEach { block ->
            val (rawText, spans) = when (block) {
                is com.blackeye.kribo.data.blocks.TitleBlock -> block.text to block.spans
                is com.blackeye.kribo.data.blocks.ParagraphBlock -> block.text to block.spans
                is com.blackeye.kribo.data.blocks.TaskBlock -> block.text to block.spans
                is com.blackeye.kribo.data.blocks.QuoteBlock -> block.text to block.spans
                is com.blackeye.kribo.data.blocks.ToggleBlock -> block.content to block.contentSpans
                is com.blackeye.kribo.data.blocks.LinkBlock -> {
                    val url = com.blackeye.kribo.data.blocks.UrlSafety.normalizeUrl(block.url)
                    if (url == null) return@forEach
                    val label = block.label.ifBlank { url }
                    out.append("<p><a href=\"").append(escHtml(url))
                        .append("\" target=\"_blank\" rel=\"noopener noreferrer\">")
                        .append(escHtml(label))
                        .append("</a></p>")
                    return@forEach
                }
                else -> return@forEach
            }
            if (rawText.isBlank()) return@forEach
            out.append("<p>").append(renderSpansHtml(rawText, spans)).append("</p>")
        }
        return out.toString()
    }

    private fun escHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    private fun renderSpansHtml(text: String, spans: List<com.blackeye.kribo.data.blocks.InlineSpan>): String {
        if (spans.isEmpty()) return escHtml(text).replace("\n", "<br>")
        val ordered = spans.sortedWith(compareBy({ it.start }, { -(it.end - it.start) }))
        val out = StringBuilder()
        var cursor = 0
        ordered.forEach { span ->
            val start = span.start.coerceIn(0, text.length)
            val end = span.end.coerceIn(start, text.length)
            if (start >= end || start < cursor) return@forEach
            out.append(escHtml(text.substring(cursor, start)).replace("\n", "<br>"))
            val chunk = escHtml(text.substring(start, end)).replace("\n", "<br>")
            out.append(
                when (span.type) {
                    com.blackeye.kribo.data.blocks.SpanType.BOLD -> "<strong>$chunk</strong>"
                    com.blackeye.kribo.data.blocks.SpanType.ITALIC -> "<em>$chunk</em>"
                    com.blackeye.kribo.data.blocks.SpanType.UNDERLINE -> "<u>$chunk</u>"
                    com.blackeye.kribo.data.blocks.SpanType.STRIKETHROUGH -> "<s>$chunk</s>"
                    com.blackeye.kribo.data.blocks.SpanType.TEXT_COLOR -> {
                        val hex = span.tone?.let {
                            "#%06X".format(com.blackeye.kribo.ui.theme.InlineColors.pdfColor(it).toArgb() and 0xFFFFFF)
                        }
                        if (hex == null) chunk else "<span style=\"color:$hex\">$chunk</span>"
                    }
                    com.blackeye.kribo.data.blocks.SpanType.HIGHLIGHT -> {
                        val hex = span.tone?.let {
                            "#%06X".format(com.blackeye.kribo.ui.theme.InlineColors.pdfColor(it).toArgb() and 0xFFFFFF)
                        }
                        if (hex == null) chunk else {
                            "<mark style=\"background:$hex;opacity:.25\">$chunk</mark>"
                        }
                    }
                },
            )
            cursor = end
        }
        out.append(escHtml(text.substring(cursor)).replace("\n", "<br>"))
        return out.toString()
    }

    /** Card preview based on real body copy, never the title twice. */
    private fun NoteEntity.previewText(): String {
        val cleanTitle = displayTitle().trim()
        val candidates = listOf(bodyWithoutTitle(), ocrTextIndex)
        for (candidate in candidates) {
            var text = candidate.trim()
            if (text.isBlank()) continue
            if (cleanTitle.isNotBlank() && text.startsWith(cleanTitle, ignoreCase = true)) {
                text = text.substring(cleanTitle.length)
                    .trimStart(' ', '\n', ':', '-', '–', '—')
            }
            if (text.isNotBlank()) return text.replace('\n', ' ').take(240)
        }
        return ""
    }

    private fun NoteEntity.matches(query: String): Boolean {
        val needle = query.lowercase(Locale.ROOT)
        return title.lowercase(Locale.ROOT).contains(needle) ||
            plainTextIndex.lowercase(Locale.ROOT).contains(needle) ||
            ocrTextIndex.lowercase(Locale.ROOT).contains(needle)
    }

    private fun parseQuery(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        return raw.split('&').mapNotNull { part ->
            val key = part.substringBefore('=', "").ifBlank { return@mapNotNull null }
            val value = part.substringAfter('=', "")
            decode(key) to decode(value)
        }.toMap()
    }

    private fun decode(value: String): String =
        runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

    private fun readLine(input: InputStream): String? {
        val buffer = ByteArrayOutputStream()
        var previous = -1
        while (true) {
            val current = input.read()
            if (current < 0) return if (buffer.size() == 0) null else buffer.toString("UTF-8")
            if (current == '\n'.code) break
            if (current != '\r'.code) buffer.write(current)
            previous = current
        }
        if (buffer.size() == 0 && previous == -1) return null
        return buffer.toString("UTF-8")
    }

    private fun respondJson(output: OutputStream, body: String) {
        respond(output, "200 OK", "application/json; charset=utf-8", body.toByteArray(Charsets.UTF_8))
    }

    private fun respondHtml(output: OutputStream, html: String) {
        respond(
            output,
            "200 OK",
            "text/html; charset=utf-8",
            html.toByteArray(Charsets.UTF_8),
            HTML_HEADERS,
        )
    }

    private fun redirect(output: OutputStream, location: String, cookie: String? = null) {
        val headers = mutableMapOf("Location" to location)
        if (cookie != null) headers["Set-Cookie"] = cookie
        respond(output, "302 Found", "text/plain", ByteArray(0), headers)
    }

    private fun sessionCookie(): String =
        "kribo_session=$sessionToken; Path=/; HttpOnly; SameSite=Strict"

    private fun newSessionToken(): String {
        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") {
            "%02x".format(Locale.US, it.toInt() and 0xFF)
        }
    }

    private fun unauthorized(output: OutputStream) {
        respond(
            output,
            "401 Unauthorized",
            "application/json; charset=utf-8",
            "{\"error\":\"invalid_pin\"}".toByteArray(Charsets.UTF_8),
        )
    }

    private fun respond(
        output: OutputStream,
        status: String,
        contentType: String,
        body: ByteArray,
        extraHeaders: Map<String, String> = emptyMap(),
    ) {
        val headers = buildString {
            append("HTTP/1.1 ").append(status).append("\r\n")
            append("Content-Type: ").append(contentType).append("\r\n")
            append("Content-Length: ").append(body.size).append("\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n")
            extraHeaders.forEach { (key, value) ->
                append(key).append(": ").append(value).append("\r\n")
            }
            append("\r\n")
        }
        output.write(headers.toByteArray(Charsets.UTF_8))
        output.write(body)
        output.flush()
    }

    private fun networkInfo(): Pair<String?, Boolean> {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val network = manager?.activeNetwork
        val capabilities = manager?.getNetworkCapabilities(network)
        val wifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val ip = manager?.getLinkProperties(network)
            ?.linkAddresses
            ?.firstOrNull { it.address is Inet4Address && !it.address.isLoopbackAddress }
            ?.address
            ?.hostAddress
            ?: fallbackIp()
        return ip to wifi
    }

    private fun fallbackIp(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .sortedByDescending { it.name.startsWith("wlan") }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress && it.isSiteLocalAddress }
            ?.hostAddress
    }.getOrNull()

    @Serializable
    data class NoteDto(
        val id: Long,
        val title: String,
        val folderId: Long?,
        val updatedAt: Long,
        val preview: String,
        val hasOcr: Boolean,
    )

    @Serializable
    data class FolderDto(val id: Long, val name: String)

    @Serializable
    data class CreateNoteRequest(
        val title: String = "",
        val text: String = "",
        val folderId: Long? = null,
    )

    @Serializable
    data class UpdateNoteRequest(
        val title: String = "",
        val text: String = "",
    )

    @Serializable
    data class AttachmentDto(
        val id: Long,
        val fileName: String,
        val mimeType: String,
        val url: String,
    )

    @Serializable
    data class NoteDetailDto(
        val id: Long,
        val title: String,
        val body: String,
        val bodyHtml: String = "",
        val ocrText: String,
        val updatedAt: Long,
        val editable: Boolean,
        val attachments: List<AttachmentDto>,
    )

    companion object {
        const val DEFAULT_PORT = 8765
        private val NOTE_DETAIL = Regex("^/api/notes/(\\d+)$")
        private val NOTE_EXPORT = Regex("^/api/notes/(\\d+)/export$")
        private val ATTACHMENT = Regex("^/api/attachments/(\\d+)$")

        private val HTML_HEADERS = mapOf(
            "Content-Security-Policy" to
                "default-src 'self'; img-src 'self' data:; style-src 'unsafe-inline'; " +
                "script-src 'unsafe-inline'; connect-src 'self'; base-uri 'none'; " +
                "form-action 'self'",
            "X-Content-Type-Options" to "nosniff",
            "Referrer-Policy" to "no-referrer",
        )

        private val LOGIN_HTML = WifiTransferPages.LOGIN
        private val APP_HTML = WifiTransferPages.APP
    }
}
