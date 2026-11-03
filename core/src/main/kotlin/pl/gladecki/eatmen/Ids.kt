package pl.gladecki.eatmen.core

import java.util.UUID

/** Where ids come from. Tests pass a counter so a log is byte-for-byte repeatable. */
fun interface IdSource {
    fun next(): String
}

val RandomIds = IdSource { UUID.randomUUID().toString() }

fun sequentialIds(prefix: String = "id"): IdSource {
    var n = 0
    return IdSource { "$prefix-${++n}" }
}
