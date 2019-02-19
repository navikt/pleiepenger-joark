package no.nav.helse.dokument

import io.ktor.http.ContentType

data class Dokument(
    val tittel : String,
    val content : ByteArray,
    val contentType : ContentType
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Dokument

        if (tittel != other.tittel) return false
        if (!content.contentEquals(other.content)) return false
        if (contentType != other.contentType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = tittel.hashCode()
        result = 31 * result + content.contentHashCode()
        result = 31 * result + contentType.hashCode()
        return result
    }
}