package com.blackeye.kribo.data.blocks

/**
 * URL safety + detection (#40). Only `http://` and `https://` are ever opened externally:
 * `www.` is normalized up-front and every other scheme (`javascript:`, `file:`,
 * `content:`, `intent:`, `data:`, …) is rejected. Pure, no Android dependencies.
 */
object UrlSafety {

    private val SCHEME = Regex("^(https?|www\\.)", RegexOption.IGNORE_CASE)
    private val URL_IN_TEXT = Regex("https?://[^\\s<>\"']+")

    /** True when [text] (trimmed) looks like a single http(s) URL or `www.` address. */
    fun looksLikeUrl(text: String): Boolean {
        val value = text.trim()
        return value.isNotBlank() && !value.contains(' ') && SCHEME.containsMatchIn(value)
    }

    /**
     * Opens-safe normalization: `www.example.com` becomes `https://www.example.com`.
     * Everything that is not http(s) or www returns null (never opened, never stored as link).
     */
    fun normalizeUrl(raw: String): String? {
        val value = raw.trim()
        if (value.isEmpty() || value.contains(' ')) return null
        if (value.startsWith("www.", ignoreCase = true)) return "https://" + value
        if (value.startsWith("http://", ignoreCase = true) ||
            value.startsWith("https://", ignoreCase = true)
        ) {
            return value
        }
        return null
    }

    /**
     * Up to [maxChips] unique http(s) URLs found in [text], in first-seen order, with
     * trailing punctuation (.,;:!?) trimmed. Never returns non-http(s) schemes.
     */
    fun extractSafeUrls(text: String, maxChips: Int = 3): List<String> {
        if (text.isBlank()) return emptyList()
        val found = URL_IN_TEXT.findAll(text)
            .map { it.value.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}') }
            .filter { it.isNotBlank() }
        val unique = LinkedHashSet<String>()
        for (url in found) {
            unique += url
            if (unique.size >= maxChips) break
        }
        return unique.toList()
    }

    /** Short host for the quick chip, e.g. `https://www.example.com/x` → `example.com`. */
    fun hostOf(url: String): String {
        val host = url.trim()
            .substringAfter("://")
            .substringBefore('/', "")
            .substringAfter("www.")
            .substringBefore(':')
        return host.ifBlank { url }
    }
}
