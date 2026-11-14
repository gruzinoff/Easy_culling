package app.easyculling.meta

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile

/**
 * Reads/writes the XMP packet embedded in a DNG (TIFF) file, IFD0 tag 700 —
 * the place Lightroom reads ratings from for DNG files.
 *
 *  - If the tag exists and the new packet fits, it is overwritten in place.
 *  - Otherwise a copy of the file is made with the new packet and a new copy of IFD0
 *    appended at the end (all existing data stays at the same offsets), the header
 *    is pointed at the new IFD0, and the copy atomically replaces the original.
 */
object DngXmp {
    private const val TAG_XMP = 700
    private const val TYPE_BYTE = 1

    private class Tiff(val le: Boolean, val ifd0: Long)
    private class Entry(val tag: Int, val type: Int, val count: Long, val raw: ByteArray /* 12 bytes */)

    private fun RandomAccessFile.u16(le: Boolean): Int {
        val a = read(); val b = read()
        if (a < 0 || b < 0) throw IOException("EOF")
        return if (le) a or (b shl 8) else (a shl 8) or b
    }

    private fun RandomAccessFile.u32(le: Boolean): Long {
        val b = ByteArray(4); readFully(b)
        return u32(b, 0, le)
    }

    private fun u32(b: ByteArray, o: Int, le: Boolean): Long {
        val x = b.sliceArray(o until o + 4).map { it.toLong() and 0xFF }
        return if (le) x[0] or (x[1] shl 8) or (x[2] shl 16) or (x[3] shl 24)
        else (x[0] shl 24) or (x[1] shl 16) or (x[2] shl 8) or x[3]
    }

    private fun u16(b: ByteArray, o: Int, le: Boolean): Int {
        val a = b[o].toInt() and 0xFF; val c = b[o + 1].toInt() and 0xFF
        return if (le) a or (c shl 8) else (a shl 8) or c
    }

    private fun p16(v: Int, le: Boolean) =
        if (le) byteArrayOf(v.toByte(), (v shr 8).toByte()) else byteArrayOf((v shr 8).toByte(), v.toByte())

    private fun p32(v: Long, le: Boolean): ByteArray {
        val b = byteArrayOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte())
        return if (le) b.reversedArray() else b
    }

    private fun header(raf: RandomAccessFile): Tiff {
        raf.seek(0)
        val bo = raf.readUnsignedShort()
        val le = when (bo) {
            0x4949 -> true
            0x4D4D -> false
            else -> throw IOException("Not a TIFF/DNG")
        }
        if (raf.u16(le) != 42) throw IOException("Not a TIFF/DNG")
        return Tiff(le, raf.u32(le))
    }

    private fun entries(raf: RandomAccessFile, t: Tiff): Pair<List<Entry>, Long> {
        raf.seek(t.ifd0)
        val n = raf.u16(t.le)
        if (n <= 0 || n > 4096) throw IOException("Bad IFD")
        val list = ArrayList<Entry>(n)
        repeat(n) {
            val raw = ByteArray(12); raf.readFully(raw)
            list += Entry(u16(raw, 0, t.le), u16(raw, 2, t.le), u32(raw, 4, t.le), raw)
        }
        val next = raf.u32(t.le)
        return list to next
    }

    private class Found(val offset: Long, val count: Int, val text: String)

    private fun find(raf: RandomAccessFile, t: Tiff, list: List<Entry>, entryPos: (Int) -> Long): Found? {
        val i = list.indexOfFirst { it.tag == TAG_XMP }
        if (i < 0) return null
        val e = list[i]
        if (e.count > 4 * 1024 * 1024) return null
        val count = e.count.toInt()
        val offset = if (count <= 4) entryPos(i) + 8 else u32(e.raw, 8, t.le)
        val buf = ByteArray(count)
        raf.seek(offset); raf.readFully(buf)
        return Found(offset, count, String(buf, Charsets.UTF_8).trimEnd('\u0000'))
    }

    fun read(file: File): String? = RandomAccessFile(file, "r").use { raf ->
        val t = header(raf)
        val (list, _) = entries(raf, t)
        find(raf, t, list) { t.ifd0 + 2 + it * 12L }?.text
    }

    fun write(file: File, rating: Int, label: Int) {
        val mtime = file.lastModified()
        val t: Tiff
        val list: List<Entry>
        val next: Long
        val newXml: String
        RandomAccessFile(file, "rw").use { raf ->
            t = header(raf)
            val (l, n) = entries(raf, t)
            list = l; next = n
            val found = find(raf, t, list) { t.ifd0 + 2 + it * 12L }
            newXml = Xmp.update(found?.text, rating, label)
            if (found != null && found.count > 4) {
                val bytes = Xmp.encodePadded(newXml, found.count)
                if (bytes != null) {
                    raf.seek(found.offset)
                    raf.write(bytes)
                    raf.fd.sync()
                    file.setLastModified(mtime)
                    return
                }
            }
        }

        val xmpBytes = Xmp.encodeWithSlack(newXml)
        val tmp = File(file.parentFile, ".${file.name}.ectmp")
        try {
            RandomAccessFile(file, "r").use { src ->
                FileOutputStream(tmp).use { out ->
                    JpegXmp.transfer(src.channel, 0, src.length(), out.channel)
                }
            }
            RandomAccessFile(tmp, "rw").use { raf ->
                var end = raf.length()
                if (end % 2 != 0L) { raf.seek(end); raf.write(0); end++ }
                val xmpOffset = end
                raf.seek(xmpOffset); raf.write(xmpBytes)
                end = raf.length()
                if (end % 2 != 0L) { raf.seek(end); raf.write(0); end++ }
                val ifdOffset = end
                if (ifdOffset > 0xFFFFFFFFL) throw IOException("File too large")

                val newEntry = ByteArray(12).also {
                    p16(TAG_XMP, t.le).copyInto(it, 0)
                    p16(TYPE_BYTE, t.le).copyInto(it, 2)
                    p32(xmpBytes.size.toLong(), t.le).copyInto(it, 4)
                    p32(xmpOffset, t.le).copyInto(it, 8)
                }
                val merged = (list.filter { it.tag != TAG_XMP }.map { it.tag to it.raw } + (TAG_XMP to newEntry))
                    .sortedBy { it.first }
                val ifd = java.io.ByteArrayOutputStream()
                ifd.write(p16(merged.size, t.le))
                merged.forEach { ifd.write(it.second) }
                ifd.write(p32(next, t.le))
                raf.seek(ifdOffset); raf.write(ifd.toByteArray())
                raf.seek(4); raf.write(p32(ifdOffset, t.le))
                raf.fd.sync()
            }
            // Verify the result parses and carries the new values before replacing the original.
            val check = read(tmp)
            val v = Xmp.read(check)
            if (v.rating != rating.coerceIn(0, 5) || v.label != label) throw IOException("Verification failed")
            if (!tmp.renameTo(file)) throw IOException("Could not replace ${file.name}")
            file.setLastModified(mtime)
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }
}
