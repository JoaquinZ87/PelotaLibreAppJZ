package com.jz.pelotalibretv.data

import org.junit.Assert.*
import org.junit.Test

class SignedConfigChannelTest {
    @Test fun engineChannelTakesPriorityEvenWhenEnvelopeIsInvalid() {
        val paths = mutableListOf<String>()
        assertEquals("invalid", SignedConfig.fetchEnvelope { paths.add(it); "invalid" })
        assertEquals(listOf("manifest-v2.json"), paths)
        assertThrows(Exception::class.java) { SignedConfig.verify("invalid", "invalid") }
    }

    @Test fun onlyMissingChannelFallsBack() {
        val paths = mutableListOf<String>()
        assertEquals("legacy", SignedConfig.fetchEnvelope { paths.add(it); if (it == "manifest-v2.json") null else "legacy" })
        assertEquals(listOf("manifest-v2.json", "manifest.json"), paths)
        paths.clear()
        assertThrows(IllegalStateException::class.java) {
            SignedConfig.fetchEnvelope { paths.add(it); error("HTTP 503") }
        }
        assertEquals(listOf("manifest-v2.json"), paths)
    }
}
