package pl.gladecki.eatmen.core

/**
 * A small JSON reader and writer. The app has no dependencies and no network,
 * so this exists instead of a library: it only has to be correct, not fast.
 */
sealed interface Json {
    data class Str(val value: String) : Json
    data class Num(val raw: String) : Json {
        fun asLong(): Long = raw.toDouble().toLong()
        fun asDouble(): Double = raw.toDouble()
    }
    data class Bool(val value: Boolean) : Json
    data class Arr(val items: List<Json>) : Json
    data class Obj(val fields: Map<String, Json>) : Json
    object Null : Json

    companion object {
        fun of(value: String?): Json = if (value == null) Null else Str(value)
        fun of(value: Long): Json = Num(value.toString())
        fun of(value: Int): Json = Num(value.toString())
        fun of(value: Boolean): Json = Bool(value)
    }
}

class JsonException(message: String) : Exception(message)

object JsonCodec {

    fun write(value: Json, pretty: Boolean = false): String {
        val out = StringBuilder()
        writeValue(value, out, pretty, 0)
        return out.toString()
    }

    /** Parses one value and ignores trailing whitespace. Throws [JsonException] on bad input. */
    fun parse(text: String): Json {
        val parser = Parser(text)
        val value = parser.value()
        parser.skipSpace()
        if (!parser.done()) throw JsonException("trailing content at ${parser.pos}")
        return value
    }

    fun parseObjectOrNull(line: String): Json.Obj? =
        try {
            parse(line) as? Json.Obj
        } catch (e: JsonException) {
            null
        }

    private fun writeValue(value: Json, out: StringBuilder, pretty: Boolean, depth: Int) {
        when (value) {
            is Json.Null -> out.append("null")
            is Json.Bool -> out.append(if (value.value) "true" else "false")
            is Json.Num -> out.append(value.raw)
            is Json.Str -> writeString(value.value, out)
            is Json.Arr -> {
                if (value.items.isEmpty()) {
                    out.append("[]")
                    return
                }
                out.append('[')
                value.items.forEachIndexed { index, item ->
                    if (index > 0) out.append(',')
                    newline(out, pretty, depth + 1)
                    writeValue(item, out, pretty, depth + 1)
                }
                newline(out, pretty, depth)
                out.append(']')
            }
            is Json.Obj -> {
                if (value.fields.isEmpty()) {
                    out.append("{}")
                    return
                }
                out.append('{')
                var first = true
                for ((key, item) in value.fields) {
                    if (!first) out.append(',')
                    first = false
                    newline(out, pretty, depth + 1)
                    writeString(key, out)
                    out.append(':')
                    if (pretty) out.append(' ')
                    writeValue(item, out, pretty, depth + 1)
                }
                newline(out, pretty, depth)
                out.append('}')
            }
        }
    }

    private fun newline(out: StringBuilder, pretty: Boolean, depth: Int) {
        if (!pretty) return
        out.append('\n')
        repeat(depth) { out.append("  ") }
    }

    private fun writeString(value: String, out: StringBuilder) {
        out.append('"')
        for (c in value) {
            when (c) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                else -> if (c < ' ') out.append("\\u%04x".format(c.code)) else out.append(c)
            }
        }
        out.append('"')
    }

    private class Parser(private val text: String) {
        var pos = 0

        fun done(): Boolean = pos >= text.length

        fun skipSpace() {
            while (pos < text.length && text[pos].isWhitespace()) pos++
        }

        fun value(): Json {
            skipSpace()
            if (done()) throw JsonException("unexpected end")
            return when (text[pos]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> Json.Str(string())
                't' -> literal("true", Json.Bool(true))
                'f' -> literal("false", Json.Bool(false))
                'n' -> literal("null", Json.Null)
                else -> number()
            }
        }

        private fun literal(word: String, result: Json): Json {
            if (!text.startsWith(word, pos)) throw JsonException("bad literal at $pos")
            pos += word.length
            return result
        }

        private fun obj(): Json.Obj {
            expect('{')
            val fields = LinkedHashMap<String, Json>()
            skipSpace()
            if (peek() == '}') {
                pos++
                return Json.Obj(fields)
            }
            while (true) {
                skipSpace()
                val key = string()
                skipSpace()
                expect(':')
                fields[key] = value()
                skipSpace()
                when (peek()) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return Json.Obj(fields)
                    }
                    else -> throw JsonException("expected , or } at $pos")
                }
            }
        }

        private fun arr(): Json.Arr {
            expect('[')
            val items = ArrayList<Json>()
            skipSpace()
            if (peek() == ']') {
                pos++
                return Json.Arr(items)
            }
            while (true) {
                items.add(value())
                skipSpace()
                when (peek()) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return Json.Arr(items)
                    }
                    else -> throw JsonException("expected , or ] at $pos")
                }
            }
        }

        private fun string(): String {
            expect('"')
            val out = StringBuilder()
            while (true) {
                if (done()) throw JsonException("unterminated string")
                val c = text[pos++]
                when {
                    c == '"' -> return out.toString()
                    c == '\\' -> {
                        if (done()) throw JsonException("unterminated escape")
                        when (val escape = text[pos++]) {
                            '"' -> out.append('"')
                            '\\' -> out.append('\\')
                            '/' -> out.append('/')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                if (pos + 4 > text.length) throw JsonException("bad unicode escape")
                                val hex = text.substring(pos, pos + 4)
                                pos += 4
                                out.append(hex.toInt(16).toChar())
                            }
                            else -> throw JsonException("bad escape \\$escape")
                        }
                    }
                    else -> out.append(c)
                }
            }
        }

        private fun number(): Json.Num {
            val start = pos
            if (peek() == '-') pos++
            while (!done() && (text[pos].isDigit() || text[pos] == '.' || text[pos] == 'e' || text[pos] == 'E' || text[pos] == '+' || text[pos] == '-')) pos++
            if (pos == start) throw JsonException("expected a value at $start")
            val raw = text.substring(start, pos)
            raw.toDoubleOrNull() ?: throw JsonException("bad number '$raw'")
            return Json.Num(raw)
        }

        private fun peek(): Char = if (done()) '\u0000' else text[pos]

        private fun expect(c: Char) {
            skipSpace()
            if (done() || text[pos] != c) throw JsonException("expected $c at $pos")
            pos++
        }
    }
}
