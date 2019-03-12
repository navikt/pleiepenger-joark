package no.nav.helse.dokument

import io.ktor.http.ContentType
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val logger: Logger = LoggerFactory.getLogger("nav.ContentTypeService")

class ContentTypeService {
    companion object {
        val JSON = ContentType.Application.Json
        val PDF = ContentType.parse("application/pdf")
        val XML = ContentType.Application.Xml
        val PNG = ContentType.Image.PNG
        val JPEG = ContentType.Image.JPEG
    }

    private val supportedApplicationContentTypes = listOf(
        JSON,
        PDF,
        XML
    )
    private val supportedImageContentTypes = listOf(
        PNG,
        JPEG
    )

    fun isSupported(contentType: String): Boolean {
        val parsedContentType = ContentType.parseOrNull(contentType)
        return supportedImageContentTypes.contains(parsedContentType) || supportedApplicationContentTypes.contains(
            parsedContentType
        )
    }

    fun isSupportedImage(contentType: String): Boolean =
        supportedImageContentTypes.contains(ContentType.parseOrNull(contentType))

    fun isSupportedApplication(contentType: String): Boolean =
        supportedApplicationContentTypes.contains(ContentType.parseOrNull(contentType))
}


private fun ContentType.Companion.parseOrNull(contentType: String) : ContentType? {
    return try { parse(contentType) } catch (cause: Throwable) {
        logger.warn("Ugyldig content type $contentType")
        null
    }
}
