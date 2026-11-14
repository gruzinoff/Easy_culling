package app.easyculling.meta

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile

/**
 * Reads/writes the standard XMP APP1 segment of a JPEG file.
 *
 * Writing strategy:
 *  - If an XMP segment exists and the new packet fits in it, it is overwritten in place
 *    (only a few KB of the header change, the rest of the file is untouched).
 *  - Otherwise the file is rewritten to a temp file with a new/enlarged segment (with 2 KB of
 *    padding for future in-place edits) and atomically renamed over the original.
 *    Everything after the header segments (image data, Samsung/motion-photo trailers) is copied verbatim.
 */
object JpegXmp {
    private val HEADER = (Xmp.NS_XMP + "\u0000").toByteArray(Charsets.ISO_8859_1)

    private class Segment(val marker: Int, val offset: Long, val length: Int) {
        /** Total size including the 0xFF marker bytes. */
        val end: Long get() = offset + 2 + length
    }

    private fun scan(raf: RandomAccessFile): List<Segment> {
        raf.seek(0)
        if (raf.readUnsignedShort() != 0xFFD8) throw IOException("Not a JPEG")
        val segs = ArrayList<Segment>()
        var pos = 2L
        val size = raf.length()
        while (pos + 4 <= size) {
            raf.seek(pos)
            if (raf.read() != 0xFF) break
            var marker = raf.read()
            while (marker == 0xFF) { pos++; marker = raf.read() }
            if (marker == 0xDA || marker == 0xD9 || marker < 0) break
            if (marker in 0xD0..0xD7 || marker == 0x01) { pos += 2; continue }
            val len = raf.readUnsignedShort()
            if (len < 2) throw IOException("Bad segment length")
            segs += Segment(marker, pos, len)
            pos += 2 + len
        }
        return segs
    }

    private fun isXmp(raf: RandomAccessFile, s: Segment): Boolean {
        if (s.marker != 0xE1 || s.length - 2 < HEADER.size) return false
        raf.seek(s.offset + 4)
        val buf = ByteArray(HEADER.size)
        raf.readFully(buf)
        return buf.contentEquals(HEADER)
    }

    private fun payload(raf: RandomAccessFile, s: Segment): String {
        val n = s.length - 2 - HEADER.size
        val buf = ByteArray(n)
        raf.seek(s.offset + 4 + HEADER.size)
        raf.readFully(buf)
        return String(buf, Charsets.UTF_8)
    }

    fun read(file: File): String? = RandomAccessFile(file, "r").use { raf ->
        scan(raf).firstOrNull { isXmp(raf, it) }?.let { payload(raf, it) }
    }

    fun write(file: File, rating: Int, label: Int) {
        val mtime = file.lastModified()
        var rewriteFrom: Long
        var rewriteTo: Long
        val newXml: String
        RandomAccessFile(file, "rw").use { raf ->
            val segs = scan(raf)
            val xmpSeg = segs.firstOrNull { isXmp(raf, it) }
            newXml = Xmp.update(xmpSeg?.let { payload(raf, it) }, rating, label)
            if (xmpSeg != null) {
                val capacity = xmpSeg.length - 2 - HEADER.size
                val bytes = Xmp.encodePadded(newXml, capacity)
                if (bytes != null) {
                    raf.seek(xmpSeg.offset + 4 + HEADER.size)
                    raf.write(bytes)
                    raf.fd.sync()
                    file.setLastModified(mtime)
                    return
                }
                rewriteFrom = xmpSeg.offset
                rewriteTo = xmpSeg.end
            } else {
                // Insert after the leading run of APP0/APP1 (JFIF/EXIF) segments.
                var insert = 2L
                for (s in segs) {
                    if (s.marker == 0xE0 || s.marker == 0xE1) insert = s.end else break
                }
                rewriteFrom = insert
                rewriteTo = insert
            }
        }
        val bytes = Xmp.encodeWithSlack(newXml)
        val segLen = 2 + HEADER.size + bytes.size
        if (segLen > 0xFFFF) throw IOException("XMP too large for a JPEG segment")

        val tmp = File(file.parentFile, ".${file.name}.ectmp")
        try {
            RandomAccessFile(file, "r").use { src ->
                FileOutputStream(tmp).use { out ->
                    val inCh = src.channel
                    val outCh = out.channel
                    transfer(inCh, 0, rewriteFrom, outCh)
                    val seg = java.io.ByteArrayOutputStream(segLen + 2)
                    seg.write(0xFF); seg.write(0xE1)
                    seg.write(segLen shr 8); seg.write(segLen and 0xFF)
                    seg.write(HEADER); seg.write(bytes)
                    out.write(seg.toByteArray())
                    transfer(inCh, rewriteTo, src.length() - rewriteTo, outCh)
                    out.fd.sync()
                }
            }
            if (tmp.length() != file.length() - (rewriteTo - rewriteFrom) + segLen + 2) {
                throw IOException("Size check failed while rewriting ${file.name}")
            }
            if (!tmp.renameTo(file)) throw IOException("Could not replace ${file.name}")
            file.setLastModified(mtime)
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    internal fun transfer(
        src: java.nio.channels.FileChannel, from: Long, count: Long, dst: java.nio.channels.WritableByteChannel,
    ) {
        var pos = from
        var left = count
        while (left > 0) {
            val n = src.transferTo(pos, left, dst)
            if (n <= 0) throw IOException("Short transfer")
            pos += n; left -= n
        }
    }
}
