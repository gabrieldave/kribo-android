package com.blackeye.kribo.ocr

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Quality gate with a real dense page: small body text, multiple paragraphs and > 300 expected
 * characters. The old 4-word synthetic test could pass while omitting entire paragraphs.
 */
@RunWith(AndroidJUnit4::class)
class DensePageOcrDeviceTest {

    @Test
    fun mlKitFallbackAlsoReadsDensePage(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val work = File(context.cacheDir, "dense-mlkit-${System.currentTimeMillis()}").apply { mkdirs() }
        val image = File(work, "dense-page.jpg")
        DensePageFactory.singlePage(image)

        val engine = MlKitBundledOcrEngine()
        val started = System.currentTimeMillis()
        val text = engine.recognize(image)
        val elapsed = System.currentTimeMillis() - started
        engine.close()

        println("MlKitDensePage: chars=${text?.length ?: 0} elapsed=${elapsed}ms")
        assertTrue("ML Kit fallback returned too little text: $text", (text?.length ?: 0) > 300)
        val normalized = text!!.lowercase()
        assertTrue("ML Kit missed body text", normalized.contains("documento") && normalized.contains("lectura"))
        work.deleteRecursively()
        Unit
    }

    @Test
    fun extractsCompleteParagraphsFromDensePage(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val work = File(context.cacheDir, "dense-ocr-${System.currentTimeMillis()}").apply { mkdirs() }
        val image = File(work, "dense-page.jpg")
        val expected = DensePageFactory.singlePage(image)

        val subsystem = OcrSubsystem(context)
        assertFalse("OCR must stay lazy before the first page", subsystem.isLoaded)

        val started = System.currentTimeMillis()
        val text = subsystem.recognize(image)
        val elapsed = System.currentTimeMillis() - started
        subsystem.close()

        println("DensePageOcr: chars=${text?.length ?: 0} elapsed=${elapsed}ms")
        assertTrue("OCR returned null or too little text: $text", (text?.length ?: 0) > 300)
        val normalized = text!!.lowercase()
        listOf("kribo", "documento", "lectura", "reconocimiento", "párrafo", "escaneo").forEach { word ->
            assertTrue("Missing body word '$word' in: $text", normalized.contains(word))
        }
        assertTrue("Expected at least 6 lines, got: $text", text.lines().size >= 6)
        assertTrue(
            "OCR kept only page numbers: $text",
            text.count { it.isLetter() } > expected.length / 8,
        )
        work.deleteRecursively()
        Unit
    }
}
