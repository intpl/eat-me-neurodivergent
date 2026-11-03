package pl.gladecki.eatmen.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonTest {

    @Test
    fun writesAndParsesAFlatObject() {
        val value = Json.Obj(linkedMapOf("a" to Json.of(1L), "b" to Json.of("two"), "c" to Json.of(true)))
        val text = JsonCodec.write(value)
        assertEquals("{\"a\":1,\"b\":\"two\",\"c\":true}", text)
        assertEquals(value, JsonCodec.parse(text))
    }

    @Test
    fun roundTripsAwkwardStrings() {
        val awkward = "quote\" backslash\\ newline\n tab\t unicode \u00e9 \u0001 ok"
        val text = JsonCodec.write(Json.of(awkward))
        val parsed = JsonCodec.parse(text) as Json.Str
        assertEquals(awkward, parsed.value)
    }

    @Test
    fun keepsEpochMillisExact() {
        val at = 1_793_700_000_000L
        val text = JsonCodec.write(Json.of(at))
        assertEquals(at, (JsonCodec.parse(text) as Json.Num).asLong())
    }

    @Test
    fun parsesNestedStructures() {
        val text = "{\"events\":[{\"at\":1},{\"at\":2}],\"empty\":[],\"obj\":{}}"
        val parsed = JsonCodec.parse(text) as Json.Obj
        assertEquals(2, (parsed.fields["events"] as Json.Arr).items.size)
        assertEquals(0, (parsed.fields["empty"] as Json.Arr).items.size)
        assertEquals(0, (parsed.fields["obj"] as Json.Obj).fields.size)
    }

    @Test
    fun prettyPrintIsStillParseable() {
        val value = Json.Obj(linkedMapOf("k" to Json.Arr(listOf(Json.of("v"), Json.Null))))
        val pretty = JsonCodec.write(value, pretty = true)
        assertTrue(pretty.contains("\n"))
        assertEquals(value, JsonCodec.parse(pretty))
    }

    @Test
    fun rejectsBrokenInput() {
        assertNull(JsonCodec.parseObjectOrNull("{\"a\": }"))
        assertNull(JsonCodec.parseObjectOrNull("not json at all"))
        assertNull(JsonCodec.parseObjectOrNull(""))
        assertNull(JsonCodec.parseObjectOrNull("[1,2,3]"))
    }

    @Test
    fun skipsWhitespaceAroundAValue() {
        assertEquals(Json.Bool(false), JsonCodec.parse("   false  "))
    }
}
