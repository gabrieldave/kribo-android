package com.blackeye.kribo.scan

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Locale

object BookOrt {
    const val DIGEST = "5f7c399b3ecd8c9ba965cfa0a8ec5f5dd6e3ca6deef7b61a47b1b10dea54f168"
    const val MODEL_BYTES = 31_587_333L
    private val bookMutex = Mutex()
    private val inputShape = longArrayOf(1, 3, 712, 488)
    private val outputShape = longArrayOf(1, 2, 45, 31)

    /** One job per process and one session per batch; process pages sequentially inside work. */
    suspend fun <T> withBook(context: Context, work: suspend Runner.() -> T): T =
        bookMutex.withLock {
            val path = withContext(Dispatchers.IO) { stageAsset(context.applicationContext) }
            withContext(Dispatchers.Default) {
                val env = OrtEnvironment.getEnvironment()
                OrtSession.SessionOptions().use { options ->
                    options.setIntraOpNumThreads(2)
                    options.setInterOpNumThreads(1)
                    env.createSession(path.absolutePath, options).use { session ->
                        checkContract(session)
                        Runner(env,session).work()
                    }
                }
            }
        }

    class Runner internal constructor(
        private val env: OrtEnvironment, private val session: OrtSession
    ) {
        private val input = BookMath.inputBuffer()
        private val inferMutex = Mutex()

        suspend fun infer(page: BookPixels): FloatArray = withContext(Dispatchers.Default) {
            inferMutex.withLock {
                val ctx = currentCoroutineContext()
                BookMath.packBgr(page, input) { ctx.ensureActive() }
                OnnxTensor.createTensor(env, input, inputShape).use { tensor ->
                    ctx.ensureActive()
                    session.run(mapOf("image" to tensor), setOf("grid")).use { result ->
                        ctx.ensureActive() // a synchronous native call may finish after cancellation
                        val t = result[0] as OnnxTensor
                        check(t.info.type == OnnxJavaType.FLOAT && t.info.shape.contentEquals(outputShape))
                        val grid = FloatArray(BookMath.GRID_FLOATS)
                        val buffer = t.floatBuffer
                        check(buffer.remaining() == grid.size)
                        buffer.get(grid)
                        grid
                    }
                }
            }
        }

        /** Applies the UVDoc grid and returns a flattened bitmap at the requested size. */
        suspend fun flatten(
            page: BookPixels,
            width: Int = page.width,
            height: Int = page.height,
        ): Bitmap? = withContext(Dispatchers.Default) {
            val ctx = currentCoroutineContext()
            val grid = infer(page)
            if (BookMath.gridIssue(grid) != null) return@withContext null
            require(width in 1..32768 && height in 1..32768 && width.toLong() * height <= 64_000_000L)
            BookMath.renderToBitmap(page, grid, width, height) { ctx.ensureActive() }
        }
    }

    private fun checkContract(session: OrtSession) {
        require(session.inputNames == setOf("image") && session.outputNames == setOf("grid"))
        val i = session.inputInfo.getValue("image").info as TensorInfo
        val o = session.outputInfo.getValue("grid").info as TensorInfo
        require(i.type == OnnxJavaType.FLOAT && i.shape.contentEquals(inputShape))
        require(o.type == OnnxJavaType.FLOAT && o.shape.contentEquals(outputShape))
    }

    private suspend fun stageAsset(context: Context): File {
        val ctx = currentCoroutineContext()
        val root = File(context.noBackupFilesDir, "book-models")
        check(root.isDirectory || root.mkdirs() || root.isDirectory)
        val dest = File(root, "uvdoc-" + DIGEST + ".onnx")
        RandomAccessFile(File(root,"copy.lock"), "rw").use { handle ->
            handle.channel.lock().use {
                val scratch = ByteArray(65536)
                fun digest(file: File): String {
                    val md = MessageDigest.getInstance("SHA-256")
                    file.inputStream().use { input ->
                        while (true) {
                            ctx.ensureActive()
                            val n = input.read(scratch)
                            if (n < 0) break
                            md.update(scratch,0,n)
                        }
                    }
                    return md.digest().joinToString("") { b -> "%02x".format(Locale.ROOT,b.toInt() and 255) }
                }
                if (dest.isFile && dest.length() == MODEL_BYTES && digest(dest) == DIGEST) return dest
                val tmp = File.createTempFile("uvdoc-", ".part", root)
                try {
                    context.assets.open("models/UVDoc_grid.onnx").use { src ->
                        FileOutputStream(tmp).use { out ->
                            var total = 0L
                            while (true) {
                                ctx.ensureActive()
                                val n = src.read(scratch)
                                if (n < 0) break
                                total += n
                                check(total <= MODEL_BYTES) { "unexpected model length" }
                                out.write(scratch,0,n)
                            }
                            out.fd.sync()
                        }
                    }
                    check(tmp.length() == MODEL_BYTES && digest(tmp) == DIGEST) { "model integrity" }
                    Files.move(tmp.toPath(), dest.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                    return dest
                } finally {
                    tmp.delete()
                }
            }
        }
    }
}
