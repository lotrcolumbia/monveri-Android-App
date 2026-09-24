package co.monveri.register.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingPayloadTest {

    @Test
    fun `parses a full pairing QR`() {
        val raw = "monveri://pair?v=1" +
            "&url=https%3A%2F%2Fapp.monveri.co%2Fstores%2FAB12CD34" +
            "&key=a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6" +
            "&name=Carolina%20Thread%20Place" +
            "&reg=7" +
            "&label=Front%20register"

        val payload = PairingPayload.parse(raw)

        assertEquals("https://app.monveri.co/stores/AB12CD34", payload?.storeUrl)
        assertEquals("a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6", payload?.apiKey)
        assertEquals("Carolina Thread Place", payload?.storeName)
        assertEquals(7, payload?.registerNumber)
        assertEquals("REG07", payload?.registerLabel)
        assertEquals("Front register", payload?.keyLabel)
    }

    @Test
    fun `tolerates the opaque monveri pair form without double slash`() {
        val raw = "monveri:pair?v=1&url=https%3A%2F%2Fstore.example&key=abc123"

        val payload = PairingPayload.parse(raw)

        assertEquals("https://store.example", payload?.storeUrl)
        assertEquals("abc123", payload?.apiKey)
    }

    @Test
    fun `optional fields default to null and empty`() {
        val raw = "monveri://pair?v=1&url=https%3A%2F%2Fstore.example&key=abc123"

        val payload = PairingPayload.parse(raw)

        assertNull(payload?.registerNumber)
        assertNull(payload?.keyLabel)
        assertEquals("", payload?.registerLabel)
        assertEquals("store.example", payload?.displayName)
    }

    @Test
    fun `rejects a non-monveri scheme`() {
        assertNull(PairingPayload.parse("https://example.com/pair?v=1&url=https://store.example&key=abc"))
    }

    @Test
    fun `rejects an unsupported version`() {
        assertNull(PairingPayload.parse("monveri://pair?v=2&url=https%3A%2F%2Fstore.example&key=abc123"))
    }

    @Test
    fun `rejects a missing api key`() {
        assertNull(PairingPayload.parse("monveri://pair?v=1&url=https%3A%2F%2Fstore.example"))
    }

    @Test
    fun `rejects a store url without an http scheme`() {
        assertNull(PairingPayload.parse("monveri://pair?v=1&url=ftp%3A%2F%2Fstore.example&key=abc123"))
    }

    @Test
    fun `rejects blank and garbage input`() {
        assertNull(PairingPayload.parse(""))
        assertNull(PairingPayload.parse("   "))
        assertNull(PairingPayload.parse("not a qr code at all"))
    }

    @Test
    fun `register number outside 1 to 99 is dropped, not fatal`() {
        val raw = "monveri://pair?v=1&url=https%3A%2F%2Fstore.example&key=abc123&reg=150"

        val payload = PairingPayload.parse(raw)

        assertEquals("abc123", payload?.apiKey)
        assertNull(payload?.registerNumber)
    }
}
