package com.blackeye.kribo.transfer

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
                val pin = headers["x-kribo-pin"]
                    ?: headers["cookie"]?.split(';')
                        ?.map { it.trim() }
                        ?.firstOrNull { it.startsWith("kribo_pin=") }
                        ?.substringAfter('=')
                    ?: query["pin"]
                val output = socket.getOutputStream()

                if (method == "OPTIONS") {
                    respond(output, "204 No Content", "text/plain", ByteArray(0))
                    return@runCatching
                }

                when {
                    method == "GET" && path == "/" -> {
                        if (pin == sessionPin) {
                            respond(
                                output,
                                "200 OK",
                                "text/html; charset=utf-8",
                                APP_HTML.toByteArray(Charsets.UTF_8),
                                mapOf("Set-Cookie" to "kribo_pin=$sessionPin; Path=/; HttpOnly; SameSite=Strict"),
                            )
                        } else {
                            respond(
                                output,
                                "200 OK",
                                "text/html; charset=utf-8",
                                LOGIN_HTML.toByteArray(Charsets.UTF_8),
                            )
                        }
                    }

                    method == "GET" && path == "/api/login" -> {
                        if (query["pin"] == sessionPin) {
                            respond(
                                output,
                                "204 No Content",
                                "text/plain",
                                ByteArray(0),
                                mapOf("Set-Cookie" to "kribo_pin=$sessionPin; Path=/; HttpOnly; SameSite=Strict"),
                            )
                        } else {
                            unauthorized(output)
                        }
                    }

                    !isAuthenticated(pin) -> unauthorized(output)

                    method == "GET" && path == "/api/notes" -> {
                        val queryText = query["q"].orEmpty().trim()
                        val notes = repository.getAllNotes()
                            .asSequence()
                            .filter { note -> queryText.isBlank() || note.matches(queryText) }
                            .map(::noteDto)
                            .toList()
                        respondJson(output, json.encodeToString(notes))
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

                    else -> respond(output, "404 Not Found", "text/plain", "Not found".toByteArray())
                }
            }.onFailure {
                // A broken client must never take the server down.
            }
        }
    }

    private fun isAuthenticated(pin: String?): Boolean =
        sessionPin.isNotBlank() && pin == sessionPin

    private fun noteDto(note: NoteEntity): NoteDto {
        val text = note.ocrTextIndex.ifBlank { note.plainTextIndex }
        return NoteDto(
            id = note.id,
            title = note.title.ifBlank { text.lineSequence().firstOrNull().orEmpty().take(80) },
            folderId = note.folderId,
            updatedAt = note.updatedAt,
            preview = text.replace('\n', ' ').take(240),
            hasOcr = note.ocrTextIndex.isNotBlank(),
        )
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

    companion object {
        const val DEFAULT_PORT = 8765
        private val NOTE_EXPORT = Regex("^/api/notes/(\\d+)/export$")
        private val ATTACHMENT = Regex("^/api/attachments/(\\d+)$")

        private val LOGIN_HTML = """
            <!doctype html><html lang="es"><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>Kribo · PIN</title>
            <style>
            body{font-family:system-ui,-apple-system,sans-serif;background:#0D1117;color:#fff;display:grid;place-items:center;min-height:100vh;margin:0}
            .card{background:#161B22;border:1px solid #30363D;border-radius:20px;padding:28px;max-width:340px;width:90%}
            h1{margin:0 0 8px;color:#00D8FF;font-size:24px}p{color:#9BA7B4;font-size:14px}
            input{width:100%;box-sizing:border-box;margin:16px 0;padding:14px;border-radius:12px;border:1px solid #30363D;background:#0D1117;color:#fff;font-size:24px;letter-spacing:8px;text-align:center}
            button{width:100%;padding:14px;border-radius:12px;border:0;background:#00D8FF;color:#00232B;font-weight:700;font-size:16px}
            </style>
            <div class="card"><h1>Kribo</h1><p>Introduce el PIN de 6 dígitos que aparece en el Pixel.</p>
            <input id="pin" inputmode="numeric" maxlength="6" autofocus>
            <button id="go">Entrar</button></div>
            <script>
            const p=new URLSearchParams(location.search).get('pin');
            if(p&&p.length===6){location.href='/?pin='+encodeURIComponent(p)}
            document.getElementById('go').onclick=()=>{const v=document.getElementById('pin').value.trim();if(v.length===6)location.href='/?pin='+encodeURIComponent(v)}
            </script></html>
        """.trimIndent()

        private val APP_HTML = """
            <!doctype html><html lang="es"><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>Kribo · Transferencia local</title>
            <style>
            :root{color-scheme:dark}*{box-sizing:border-box}
            body{margin:0;font-family:system-ui,-apple-system,sans-serif;background:#0D1117;color:#fff}
            header{padding:20px;border-bottom:1px solid #30363D;display:flex;gap:12px;align-items:center;justify-content:space-between;flex-wrap:wrap}
            h1{margin:0;color:#00D8FF;font-size:22px}.muted{color:#9BA7B4;font-size:13px}
            main{max-width:860px;margin:0 auto;padding:20px}
            input,textarea{width:100%;padding:12px;border-radius:12px;border:1px solid #30363D;background:#161B22;color:#fff;font:inherit;margin:6px 0}
            button{padding:12px 16px;border-radius:12px;border:0;background:#00D8FF;color:#00232B;font-weight:700;cursor:pointer}
            button.ghost{background:#161B22;color:#00D8FF;border:1px solid #30363D}
            .card{background:#161B22;border:1px solid #30363D;border-radius:16px;padding:14px;margin:10px 0}
            .row{display:flex;gap:10px;justify-content:space-between;align-items:center;flex-wrap:wrap}
            a{color:#00D8FF;text-decoration:none}.preview{color:#9BA7B4;font-size:13px;margin-top:6px}
            .grid{display:grid;grid-template-columns:1fr 1fr;gap:10px}@media(max-width:600px){.grid{grid-template-columns:1fr}}
            </style>
            <header><div><h1>Kribo</h1><div class="muted">Transferencia local · solo en tu Wi-Fi</div></div>
            <button class="ghost" onclick="backup()">Descargar backup .zip</button></header>
            <main>
            <input id="q" placeholder="Buscar notas y OCR…" oninput="load()">
            <div id="notes"></div>
            <div class="card"><h2 style="margin:0 0 8px">Nueva nota</h2>
            <input id="title" placeholder="Título"><textarea id="text" rows="4" placeholder="Texto"></textarea>
            <button onclick="createNote()">Guardar en Kribo</button><span id="msg" class="muted"></span></div>
            </main>
            <script>
            const pin=new URLSearchParams(location.search).get('pin')||(document.cookie.match(/kribo_pin=([^;]+)/)||[])[1]||'';
            const h=pin?{'X-Kribo-Pin':pin}:{};
            const esc=s=>String(s||'').replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
            async function load(){const q=document.getElementById('q').value;const r=await fetch('/api/notes?q='+encodeURIComponent(q),{headers:h});if(!r.ok){document.getElementById('notes').innerHTML='<p class="muted">PIN incorrecto o sesión cerrada.</p>';return}
            const list=await r.json();document.getElementById('notes').innerHTML=list.map(n=>`<div class="card"><div class="row"><strong>${'$'}{esc(n.title)}</strong><a href="/api/notes/${'$'}{n.id}/export" download>Descargar .md</a></div><div class="preview">${'$'}{esc(n.preview)}</div></div>`).join('')||'<p class="muted">Sin notas.</p>'}
            async function createNote(){const title=document.getElementById('title').value;const text=document.getElementById('text').value;const r=await fetch('/api/notes',{method:'POST',headers:{...h,'Content-Type':'application/json'},body:JSON.stringify({title,text})});const m=document.getElementById('msg');if(r.ok){m.textContent='Guardada';document.getElementById('title').value='';document.getElementById('text').value='';load()}else{m.textContent='No se pudo guardar'}}
            function backup(){location.href='/api/backup.zip'}load();
            </script></html>
        """.trimIndent()
    }
}
