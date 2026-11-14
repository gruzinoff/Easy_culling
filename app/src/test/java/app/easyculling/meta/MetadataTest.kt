package app.easyculling.meta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

class MetadataTest {

    private fun fakeJpeg(trailer: String = "TRAILER"): ByteArray {
        val o = ByteArrayOutputStream()
        o.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
        // APP0 JFIF
        val jfif = "JFIF\u0000\u0001\u0001\u0000\u0000\u0001\u0000\u0001\u0000\u0000".toByteArray(Charsets.ISO_8859_1)
        o.write(0xFF); o.write(0xE0); o.write(0); o.write(jfif.size + 2); o.write(jfif)
        // DQT-like dummy segment
        o.write(0xFF); o.write(0xDB); o.write(0); o.write(6); o.write(byteArrayOf(1, 2, 3, 4))
        // SOS + "entropy data" + EOI + trailer
        o.write(0xFF); o.write(0xDA); o.write(0); o.write(4); o.write(byteArrayOf(9, 9))
        o.write(ByteArray(1000) { (it % 200).toByte() })
        o.write(0xFF); o.write(0xD9)
        o.write(trailer.toByteArray())
        return o.toByteArray()
    }

    @Test
    fun jpegRoundTrip() {
        val f = File.createTempFile("ectest", ".jpg")
        f.writeBytes(fakeJpeg())
        JpegXmp.write(f, 4, 2)
        assertEquals(Xmp.Values(4, 2), Xmp.read(JpegXmp.read(f)))
        val size = f.length()
        repeat(20) { JpegXmp.write(f, it % 6, it % 6) }
        assertEquals(size, f.length())
        JpegXmp.write(f, 0, 0)
        assertEquals(Xmp.Values(0, 0), Xmp.read(JpegXmp.read(f)))
        assertTrue(String(f.readBytes(), Charsets.ISO_8859_1).endsWith("TRAILER"))
        f.delete()
    }

    @Test
    fun keepsForeignProperties() {
        val src = Xmp.FRESH_PACKET.replace(
            "</rdf:RDF>",
            "<rdf:Description rdf:about=\"\" xmlns:xmp=\"http://ns.adobe.com/xap/1.0/\" xmp:Rating=\"2\" xmp:CreatorTool=\"Cam\"/></rdf:RDF>",
        )
        assertEquals(2, Xmp.read(src).rating)
        val out = Xmp.update(src, 5, 1)
        assertEquals(Xmp.Values(5, 1), Xmp.read(out))
        assertTrue(out.contains("xmp:CreatorTool=\"Cam\""))
        assertEquals(1, Regex("xmp:Rating").findAll(out).count())
    }
}
