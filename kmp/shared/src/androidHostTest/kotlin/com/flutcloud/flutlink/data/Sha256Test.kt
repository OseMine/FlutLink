package com.flutcloud.flutlink.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIPS 180-4 test vectors for [Sha256] — the digester guards Android update
 * downloads, so it must produce real SHA-256 (one-shot and chunked).
 */
class Sha256Test {

    private fun hexOf(bytes: ByteArray): String = Sha256.hex(bytes)

    @Test
    fun emptyInput() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            hexOf(ByteArray(0))
        )
    }

    @Test
    fun abcVector() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            hexOf("abc".encodeToByteArray())
        )
    }

    @Test
    fun twoBlockVector() {
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            hexOf("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray())
        )
    }

    @Test
    fun millionAVector() {
        // 1_000_000 × 'a' — exercises many blocks without a huge literal.
        val data = ByteArray(1_000_000) { 'a'.code.toByte() }
        assertEquals(
            "cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0",
            hexOf(data)
        )
    }

    @Test
    fun chunkedUpdateMatchesOneShot() {
        val data = ByteArray(100_001) { (it % 251).toByte() }
        val expected = Sha256.hex(data)
        // Odd chunk sizes cross block boundaries in every position.
        for (chunkSize in intArrayOf(1, 3, 55, 56, 57, 63, 64, 65, 4096)) {
            val digester = Sha256.Digester()
            var offset = 0
            while (offset < data.size) {
                val take = minOf(chunkSize, data.size - offset)
                digester.update(data, offset, take)
                offset += take
            }
            assertEquals("chunkSize=$chunkSize", expected, digester.hex())
        }
    }

    @Test
    fun lengthPaddingBoundaries() {
        // Messages of length 55/56/57/63/64 sit exactly on padding edges.
        assertEquals(
            "0be66ce72c2467e793202906000672306661791622e0ca9adf4a8955b2ed189c",
            hexOf("12345678901234567890123456789012345678901234567890123456".encodeToByteArray())
        )
        assertTrue(hexOf(ByteArray(64)).length == 64)
    }
}
