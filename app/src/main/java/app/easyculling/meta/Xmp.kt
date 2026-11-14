package app.easyculling.meta

/**
 * Minimal, text-level XMP editing for the two properties this app owns:
 * xmp:Rating (0..5, Lightroom/Bridge compatible) and xmp:Label (colour label).
 *
 * Everything else inside the packet is preserved byte for byte.
 */
object Xmp {
    const val NS_XMP = "http://ns.adobe.com/xap/1.0/"

    /** Lightroom's default colour label names, indexed by label id 1..5. */
    val LABEL_NAMES = listOf("Red", "Yellow", "Green", "Blue", "Purple")

    fun labelName(id: Int): String? = LABEL_NAMES.getOrNull(id - 1)
    fun labelId(name: String?): Int =
        if (name == null) 0 else LABEL_NAMES.indexOfFirst { it.equals(name.trim(), ignoreCase = true) } + 1

    private val ratingAttr = Regex("""\s(?:xmp|xap):Rating\s*=\s*(["'])(-?\d+(?:\.\d+)?)\1""")
    private val ratingElem = Regex("""<(xmp|xap):Rating>\s*(-?\d+(?:\.\d+)?)\s*</\1:Rating>""")
    private val labelAttr = Regex("""\s(?:xmp|xap):Label\s*=\s*(["'])([^"']*)\1""")
    private val labelElem = Regex("""<(xmp|xap):Label>([^<]*)</\1:Label>""")

    /** The description element this app writes; removed when it becomes empty. */
    private val emptyOwnDescription =
        Regex("""<rdf:Description rdf:about=""\s+xmlns:xmp="http://ns\.adobe\.com/xap/1\.0/"\s*/>\n?""")

    private val paddingBeforeEnd = Regex("""\s*(<\?xpacket\s+end=)""")

    const val FRESH_PACKET =
        "<?xpacket begin=\"﻿\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>\n" +
            "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\" x:xmptk=\"EasyCulling\">\n" +
            "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n" +
            "</rdf:RDF>\n" +
            "</x:xmpmeta>\n" +
            "<?xpacket end=\"w\"?>"

    data class Values(val rating: Int, val label: Int)

    fun read(xml: String?): Values {
        if (xml == null) return Values(0, 0)
        val r = (ratingAttr.find(xml)?.groupValues?.get(2) ?: ratingElem.find(xml)?.groupValues?.get(2))
            ?.toDoubleOrNull()?.toInt() ?: 0
        val l = labelAttr.find(xml)?.groupValues?.get(2) ?: labelElem.find(xml)?.groupValues?.get(2)
        return Values(r.coerceIn(0, 5), labelId(l))
    }

    /** Returns the packet with rating/label replaced and trailing padding stripped. */
    fun update(xml: String?, rating: Int, label: Int): String {
        var s = xml?.takeIf { it.contains("</rdf:RDF>") } ?: FRESH_PACKET
        s = ratingAttr.replace(s, "")
        s = ratingElem.replace(s, "")
        s = labelAttr.replace(s, "")
        s = labelElem.replace(s, "")
        s = emptyOwnDescription.replace(s, "")
        val attrs = buildString {
            if (rating in 1..5) append(" xmp:Rating=\"").append(rating).append('"')
            labelName(label)?.let { append(" xmp:Label=\"").append(it).append('"') }
        }
        if (attrs.isNotEmpty()) {
            val idx = s.lastIndexOf("</rdf:RDF>")
            s = s.substring(0, idx) +
                "<rdf:Description rdf:about=\"\" xmlns:xmp=\"$NS_XMP\"$attrs/>\n" +
                s.substring(idx)
        }
        return stripPadding(s)
    }

    fun stripPadding(s: String): String =
        if (s.contains("<?xpacket")) paddingBeforeEnd.replace(s) { "\n" + it.groupValues[1] } else s.trimEnd()

    /**
     * Encodes [xml] as UTF-8 padded with whitespace to exactly [size] bytes,
     * or returns null when it does not fit.
     */
    fun encodePadded(xml: String, size: Int): ByteArray? {
        val body = xml.toByteArray(Charsets.UTF_8)
        if (body.size > size) return null
        val padLen = size - body.size
        if (padLen == 0) return body
        val pad = ByteArray(padLen) { i -> if ((i + 1) % 100 == 0) '\n'.code.toByte() else ' '.code.toByte() }
        val endIdx = xml.lastIndexOf("<?xpacket end=")
        if (endIdx < 0) return body + pad
        val head = xml.substring(0, endIdx).toByteArray(Charsets.UTF_8)
        val tail = xml.substring(endIdx).toByteArray(Charsets.UTF_8)
        return head + pad + tail
    }

    /** Encodes with the standard ~2 KB of padding so later edits can be done in place. */
    fun encodeWithSlack(xml: String): ByteArray {
        val n = xml.toByteArray(Charsets.UTF_8).size
        return encodePadded(xml, n + 2048)!!
    }
}
