package org.futo.inputmethod.latin.uix.actions.media

/** File type is determined from bytes, never from untrusted URI MIME labels. */
internal enum class MediaKind(val mime: String, val extension: String) {
    PNG("image/png", "png"),
    JPEG("image/jpeg", "jpg"),
    WEBP("image/webp", "webp"),
    GIF("image/gif", "gif");

    val animatedCandidate: Boolean get() = this == GIF || this == WEBP
}

internal object MediaMagic {
    fun detect(header: ByteArray): MediaKind? {
        if (header.size < 12) return null
        fun has(offset: Int, magic: String): Boolean {
            val bytes = magic.toByteArray(Charsets.US_ASCII)
            return header.size >= offset + bytes.size &&
                bytes.indices.all { header[offset + it] == bytes[it] }
        }
        return when {
            header[0] == 0x89.toByte() && has(1, "PNG") &&
                header[4] == 0x0d.toByte() && header[5] == 0x0a.toByte() &&
                header[6] == 0x1a.toByte() && header[7] == 0x0a.toByte() -> MediaKind.PNG
            has(0, "GIF89a") || has(0, "GIF87a") -> MediaKind.GIF
            has(0, "RIFF") && has(8, "WEBP") -> MediaKind.WEBP
            header[0] == 0xff.toByte() && header[1] == 0xd8.toByte() &&
                header[2] == 0xff.toByte() -> MediaKind.JPEG
            else -> null
        }
    }

    fun isZip(header: ByteArray): Boolean = header.size >= 4 &&
        header[0] == 0x50.toByte() && header[1] == 0x4b.toByte() &&
        header[2] == 0x03.toByte() && header[3] == 0x04.toByte()
}
