package com.blackeye.kribo.scan

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.blackeye.kribo.ocr.DensePageFactory
import com.blackeye.kribo.ocr.OcrSubsystem
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves the ScanOffline `procesado/` stack works inside Kribo: an open-book spread is split
 * by the spine, each half is flattened with the UVDoc ONNX model, and both halves yield text.
 */
@RunWith(AndroidJUnit4::class)
class BookPipelineDeviceTest {

    @Test
    fun splitAndUvDocRecoverBothPagesOfASpread(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val work = File(context.cacheDir, "book-pipeline-${System.currentTimeMillis()}").apply { mkdirs() }
        val image = File(work, "spread.jpg")
        DensePageFactory.doublePage(image)

        val processor = DocumentPageProcessor(context)
        val candidates = processor.process(image, File(work, "out"))
        assertEquals("The spine must produce two pages", 2, candidates.size)
        candidates.forEach { candidate ->
            val flat = candidate.flat
            assertNotNull("UVDoc must produce a flattened candidate", flat)
            assertTrue(candidate.raw.isFile && candidate.raw.length() > 0L)
            assertTrue(flat!!.isFile && flat.length() > 0L)
        }

        val ocr = OcrSubsystem(context)
        val texts = candidates.map { candidate ->
            val flatText = candidate.flat?.let { ocr.recognize(it) }.orEmpty()
            val rawText = ocr.recognize(candidate.raw).orEmpty()
            if (flatText.length >= rawText.length) flatText else rawText
        }
        ocr.close()

        val combined = texts.joinToString("\n")
        println("BookPipeline: sizes=${texts.map { it.length }}")
        assertTrue("Combined text too short: $combined", combined.length > 300)
        val normalized = combined.lowercase()
        assertTrue("Missing left-page body: $combined", normalized.contains("izquierda"))
        assertTrue("Missing right-page body: $combined", normalized.contains("derecha"))
        assertTrue("Only page numbers survived: $combined", combined.count { it.isLetter() } > 200)
        work.deleteRecursively()
        Unit
    }
}
