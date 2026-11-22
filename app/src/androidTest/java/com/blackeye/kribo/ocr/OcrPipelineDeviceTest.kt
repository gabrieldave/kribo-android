package com.blackeye.kribo.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device proof that the PP-OCRv5 ONNX models load lazily and produce real text on the Pixel.
 * Runs on the debug variant; the release build uses the exact same [OcrSubsystem] code path.
 */
@RunWith(AndroidJUnit4::class)
class OcrPipelineDeviceTest {

    @Test
    fun recognizesSyntheticDocumentAndLoadsEngineOnDemand() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.cacheDir, "ocr-device-test").apply { mkdirs() }
        val image = File(dir, "synthetic-document.jpg")

        val bitmap = Bitmap.createBitmap(1400, 500, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 92f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("KRIBO 2026", 70f, 180f, paint)
        canvas.drawText("NOTA ESCANEADA", 70f, 340f, paint)
        image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 94, it) }
        bitmap.recycle()

        val subsystem = OcrSubsystem(context)
        assertFalse("Engine must not be created before the first scan", subsystem.isLoaded)

        val started = System.currentTimeMillis()
        val text = subsystem.recognize(image)
        val elapsed = System.currentTimeMillis() - started
        Log.i(TAG, "PP-OCRv5 latency=${elapsed}ms text=$text")

        assertTrue("OCR should load the engine on demand", subsystem.isLoaded)
        assertNotNull("OCR returned null", text)
        assertTrue("OCR text should not be blank", text!!.isNotBlank())
        assertTrue(
            "Expected KRIBO or 2026 in: $text",
            text.contains("KRIBO", ignoreCase = true) || text.contains("2026"),
        )
        subsystem.close()
    }

    private companion object {
        const val TAG = "KriboOcrDevice"
    }
}
