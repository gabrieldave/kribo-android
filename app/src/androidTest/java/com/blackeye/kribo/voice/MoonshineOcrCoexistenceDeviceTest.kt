package com.blackeye.kribo.voice

import ai.moonshine.voice.JNI
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.blackeye.kribo.ocr.OcrSubsystem
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device coexistence proof on Pixel: verifies that Moonshine Voice JNI (`libmoonshine-jni.so` +
 * `libmoonshine.so`) AND Kribo's PP-OCRv5 ONNX Runtime (`libonnxruntime4j_jni.so` + full
 * `libonnxruntime.so` 1.23.2) load and run together in the same process with zero
 * UnsatisfiedLinkError / OrtException.
 */
@RunWith(AndroidJUnit4::class)
class MoonshineOcrCoexistenceDeviceTest {

    @Test
    fun moonshineAndOcrShareOnnxRuntimeWithoutConflict() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // 1. Load Moonshine native libraries first and query C API version.
        JNI.ensureLibraryLoaded()
        val moonshineVersion = JNI.moonshineGetVersion()
        Log.i(TAG, "Moonshine JNI loaded cleanly, version=$moonshineVersion")
        assertTrue("Expected positive Moonshine version, got $moonshineVersion", moonshineVersion > 0)

        // 2. Run PP-OCRv5 ONNX inference in the exact same process.
        val dir = File(context.cacheDir, "coexistence-test").apply { mkdirs() }
        val image = File(dir, "ocr-coexist.jpg")
        val bitmap = Bitmap.createBitmap(1200, 420, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 88f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("KRIBO OFFLINE 2026", 60f, 220f, paint)
        image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        bitmap.recycle()

        val ocr = OcrSubsystem(context)
        val text = ocr.recognize(image)
        Log.i(TAG, "PP-OCRv5 after Moonshine load: text=$text")
        assertNotNull("PP-OCRv5 returned null", text)
        assertTrue(
            "Expected KRIBO or 2026 in OCR output: $text",
            text!!.contains("KRIBO", ignoreCase = true) || text.contains("2026"),
        )
        ocr.close()

        // 3. Re-query Moonshine JNI after OCR session close to verify native stability.
        assertTrue(JNI.moonshineGetVersion() == moonshineVersion)
    }

    private companion object {
        const val TAG = "KriboNativeCoexist"
    }
}
