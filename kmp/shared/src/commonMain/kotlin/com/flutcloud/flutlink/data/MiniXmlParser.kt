package com.flutcloud.flutlink.data

/**
 * Minimal streaming XML pull parser covering the subset needed to read
 * WebDAV PROPFIND/SEARCH multistatus documents on every KMP target:
 * start/end tags, text, CDATA, comments, processing instructions, DOCTYPE
 * and entity decoding. Namespace prefixes are stripped so [name] always
 * yields the local name — the same view XmlPullParser gave with
 * FEATURE_PROCESS_NAMESPACES enabled.
 */
class MiniXmlParser(private val source: String) {

    /** Event constants mirror org.xmlpull.v1.XmlPullParser. */
    companion object {
        const val START_DOCUMENT = 0
        const val END_DOCUMENT = 1
        const val START_TAG = 2
        const val END_TAG = 3
        const val TEXT = 4
    }

    private var pos = 0
    private var pendingText: String? = null
    private var currentText: String = ""
    private var pendingSelfClose = false

    var eventType: Int = START_DOCUMENT
        private set

    /** Local name of the current tag event (empty for TEXT). */
    var name: String = ""
        private set

    /** Decoded text of the current TEXT event. */
    val text: String get() = currentText

    fun next(): Int {
        if (pos >= source.length && pendingText == null && !pendingSelfClose) {
            eventType = END_DOCUMENT
            return END_DOCUMENT
        }

        // Self-closing tags produce their END_TAG on the following call.
        if (pendingSelfClose) {
            pendingSelfClose = false
            eventType = END_TAG
            return END_TAG
        }

        // Emit buffered character data before touching the next markup.
        pendingText?.let { decoded ->
            pendingText = null
            currentText = decoded
            eventType = TEXT
            return TEXT
        }

        if (source[pos] != '<') {
            val decoded = decodeEntities(readUntil('<'))
            if (decoded.isNotEmpty()) {
                currentText = decoded
                eventType = TEXT
                return TEXT
            }
            return next()
        }

        when {
            source.startsWith("<?", pos) -> { skipPast("?>"); return next() }
            source.startsWith("<!--", pos) -> { skipPast("-->"); return next() }
            source.startsWith("<![CDATA[", pos) -> {
                val end = source.indexOf("]]>", pos + 9)
                if (end < 0) {
                    currentText = source.substring(pos + 9)
                    pos = source.length
                } else {
                    currentText = source.substring(pos + 9, end)
                    pos = end + 3
                }
                eventType = TEXT
                return TEXT
            }
            source.startsWith("<!", pos) -> { skipPast(">"); return next() }  // DOCTYPE etc.
            source.startsWith("</", pos) -> {
                val end = source.indexOf('>', pos)
                name = source.substring(pos + 2, if (end < 0) source.length else end)
                    .substringBeforeWhitespaceOrAttrs().trim().substringAfter(':')
                pos = if (end < 0) source.length else end + 1
                eventType = END_TAG
                return END_TAG
            }
            else -> {
                val end = findTagEnd(pos)
                val body = source.substring(pos + 1, if (end < 0) source.length else end)
                val selfClosing = body.trimEnd().endsWith("/")
                name = body.trimStart().substringBeforeWhitespace()
                    .trimEnd('/').substringAfter(':')
                pos = if (end < 0) source.length else end + 1
                eventType = START_TAG
                if (selfClosing) {
                    pendingSelfClose = true
                }
                return START_TAG
            }
        }
    }

    private fun String.substringBeforeWhitespace(): String =
        takeWhile { !it.isWhitespace() }

    /** Tag name without attributes (used on END_TAG where only the name follows). */
    private fun String.substringBeforeWhitespaceOrAttrs(): String =
        takeWhile { !it.isWhitespace() && it != '/' }

    private fun findTagEnd(start: Int): Int {
        var i = start + 1
        var inQuote: Char? = null
        while (i < source.length) {
            val c = source[i]
            when {
                inQuote != null -> if (c == inQuote) inQuote = null
                c == '"' || c == '\'' -> inQuote = c
                c == '>' -> return i
            }
            i++
        }
        return -1
    }

    private fun skipPast(marker: String) {
        val end = source.indexOf(marker, pos)
        pos = if (end < 0) source.length else end + marker.length
    }

    private fun readUntil(stop: Char): String {
        val end = source.indexOf(stop, pos)
        val chunk = source.substring(pos, if (end < 0) source.length else end)
        pos = if (end < 0) source.length else end
        return chunk
    }

    internal fun decodeEntities(value: String): String {
        if ('&' !in value) return value
        return value.replace(Regex("&(#x?[0-9a-fA-F]+|[a-zA-Z]+);")) { match ->
            val token = match.groupValues[1]
            when {
                token == "amp" -> "&"
                token == "lt" -> "<"
                token == "gt" -> ">"
                token == "quot" -> "\""
                token == "apos" -> "'"
                token.startsWith("#x") || token.startsWith("#X") ->
                    token.substring(2).toIntOrNull(16)?.toChar()?.toString() ?: match.value
                token.startsWith("#") ->
                    token.substring(1).toIntOrNull()?.toChar()?.toString() ?: match.value
                else -> match.value
            }
        }
    }
}
