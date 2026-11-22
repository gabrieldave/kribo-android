package com.blackeye.kribo.res

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Hard gate: English and Spanish resources must stay 100% in parity.
 * Adding a key to one file without the other fails the build.
 */
class StringParityTest {

    private val resDir: File = listOf(
        File("src/main/res"),
        File("app/src/main/res"),
    ).firstOrNull { it.isDirectory }
        ?: error("Could not locate main resources from ${File(".").absolutePath}")

    private fun readStrings(localeDir: String): Map<String, String> {
        val file = File(resDir, "$localeDir/strings.xml")
        assertTrue("Missing resource file: ${file.absolutePath}", file.isFile)
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = document.getElementsByTagName("string")
        val values = LinkedHashMap<String, String>()
        for (index in 0 until nodes.length) {
            val element = nodes.item(index) as Element
            values[element.getAttribute("name")] = element.textContent
        }
        return values
    }

    @Test
    fun englishAndSpanishHaveIdenticalKeys() {
        val english = readStrings("values")
        val spanish = readStrings("values-es")

        assertTrue("Default strings.xml is empty", english.isNotEmpty())
        assertEquals("Keys only in values/strings.xml", emptySet<String>(), english.keys - spanish.keys)
        assertEquals("Keys only in values-es/strings.xml", emptySet<String>(), spanish.keys - english.keys)
        assertEquals("Key count mismatch", english.size, spanish.size)
    }

    @Test
    fun noTranslationIsEmpty() {
        val english = readStrings("values")
        val spanish = readStrings("values-es")

        english.forEach { (key, value) ->
            assertTrue("Empty English value for $key", value.isNotBlank())
        }
        spanish.forEach { (key, value) ->
            assertTrue("Empty Spanish value for $key", value.isNotBlank())
        }
    }

    @Test
    fun formatPlaceholdersMatch() {
        val placeholder = Regex("%(\\d+\\$)?[sd]")
        val english = readStrings("values")
        val spanish = readStrings("values-es")

        english.forEach { (key, value) ->
            val englishPlaceholders = placeholder.findAll(value).map { it.value }.sorted().toList()
            val spanishValue = spanish.getValue(key)
            val spanishPlaceholders = placeholder.findAll(spanishValue).map { it.value }.sorted().toList()
            assertEquals("Placeholder mismatch for $key", englishPlaceholders, spanishPlaceholders)
        }
    }
}
