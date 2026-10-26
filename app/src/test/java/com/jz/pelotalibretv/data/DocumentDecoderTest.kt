package com.jz.pelotalibretv.data

import com.jz.pelotalibretv.domain.model.Source
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DocumentDecoderTest {
    // Fixture sintético cifrado independientemente con Node crypto, sin datos de transmisiones.
    private fun fixture() = JSONObject(javaClass.getResource("/encrypted-agenda.json")!!.readText())
    private fun source() = Source("test", "Prueba", listOf("https://agenda.example"), "/", "/", "test",
        0, -180, false, "", "", "", "", sourceTimeZone = "UTC")
    private fun recipe() = JSONObject("""{
      "format":"json","recognize":"$.matches","items":"$.matches[*]","allowEmpty":true,"timeFormat":"HH:mm:ss",
      "fields":{"title":{"select":"$.title"},"date":{"select":"$.starts_at","transforms":[{"op":"before","value":"T"}]},
        "time":{"select":"$.starts_at","transforms":[{"op":"after","value":"T"},{"op":"before","value":"Z"}]}},
      "servers":{"items":"$.streams[*]","fields":{"name":{"select":"$.name"},"url":{"select":"$.embed_url"},
        "referer":{"literal":"https://wrapper.example/"}}}
    }""").put("documentDecoder", fixture().getJSONObject("decoder"))

    @Test fun encryptedAgendaPreservesSignalsAndConvertsDateAcrossMidnight() {
        val recipe = recipe()
        RecipeEngine.validate(recipe)
        val events = RecipeEngine.parse(fixture().getString("payload"), "https://api.example/matches", source(), recipe)
        assertEquals(2, events.size)
        assertEquals("Prueba ágil", events[0].title)
        assertEquals("2026-10-26", events[0].date)
        assertEquals("22:30", events[0].time)
        assertEquals("https://wrapper.example/", events[0].servers.single().referer)
        assertEquals("https://player.example/live", events[0].servers.single().embedUrl)
        assertEquals("https://api.example/matches", events[0].originUrl)
        assertTrue(events[1].servers.isEmpty())
    }

    @Test fun authenticationRejectsTamperedPayloadAndWrongKey() {
        val fixture = fixture()
        val payload = fixture.getString("payload")
        val tampered = payload.replaceRange(2, 3, if (payload[2] == 'A') "B" else "A")
        assertThrows(RecipeMismatch::class.java) { DocumentDecoder.decode(tampered, fixture.getJSONObject("decoder")) }
        val wrongKey = fixture.getJSONObject("decoder").put("keyBase64", "AAAAAAAAAAAAAAAAAAAAAA==")
        assertThrows(RecipeMismatch::class.java) { DocumentDecoder.decode(payload, wrongKey) }
    }

    @Test fun malformedEnvelopeAndUnknownDecoderAreRejected() {
        val spec = fixture().getJSONObject("decoder")
        for (payload in listOf("{}", "k1!", "k1AAAA")) {
            assertThrows(IllegalArgumentException::class.java) { DocumentDecoder.decode(payload, spec) }
        }
        assertThrows(IllegalArgumentException::class.java) { DocumentDecoder.validate(JSONObject(spec.toString()).put("type", "eval")) }
        assertThrows(IllegalArgumentException::class.java) { DocumentDecoder.validate(JSONObject(spec.toString()).put("alphabet", "A".repeat(64))) }
        assertThrows(IllegalArgumentException::class.java) { DocumentDecoder.decode("x".repeat(4_000_001), spec) }
    }

    @Test fun plainJsonKeepsExistingRefererAndEmptyFormatSemantics() {
        val recipe = recipe().apply { remove("documentDecoder"); getJSONObject("servers").getJSONObject("fields").remove("referer") }
        val plain = DocumentDecoder.decode(fixture().getString("payload"), fixture().getJSONObject("decoder"))
        val events = RecipeEngine.parse(plain, "https://api.example/matches", source(), recipe)
        assertEquals("https://api.example/matches", events[0].servers.single().referer)
        assertTrue(RecipeEngine.parse("""{"matches":[]}""", "https://api.example", source(), recipe).isEmpty())
        assertThrows(RecipeMismatch::class.java) { RecipeEngine.parse("{}", "https://api.example", source(), recipe) }
    }
}
