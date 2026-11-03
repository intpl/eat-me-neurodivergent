package pl.gladecki.eatmen

import pl.gladecki.eatmen.core.EventStore
import java.io.File

/** One append-only file of newline-delimited JSON. Nothing is ever rewritten in place. */
class FileEventStore(private val file: File) : EventStore {

    override fun readLines(): List<String> {
        if (!file.exists()) return emptyList()
        return try {
            file.readLines(Charsets.UTF_8).filter { it.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun append(line: String) {
        file.parentFile?.mkdirs()
        file.appendText(line + "\n", Charsets.UTF_8)
    }

    override fun clear() {
        if (file.exists()) file.delete()
    }
}
