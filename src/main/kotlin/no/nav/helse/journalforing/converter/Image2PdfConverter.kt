package no.nav.helse.journalforing.converter

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream


private const val PDF = "application/pdf"
private val logger: Logger = LoggerFactory.getLogger("nav.Image2PDFConverter")

class Image2PDFConverter {
    fun convert(bytes: ByteArray, contentType: String): ByteArray {
        logger.trace("Konverterer fra $contentType til PDF.")
        return try { embedImagesInPdf(
            imgType = contentType.substringAfterLast("/"),
            image = bytes
        )} catch (cause: Throwable) {
            throw IllegalStateException("Klarte ikke å gjøre om $contentType bilde til PDF", cause)
        }
    }

    companion object {

        private val A4 = PDRectangle.A4

        private fun embedImagesInPdf(image: ByteArray, imgType: String): ByteArray {
            PDDocument().use { doc ->
                ByteArrayOutputStream().use { outputStream ->
                    addPDFPageFromImage(doc, image, imgType)
                    doc.save(outputStream)
                    return outputStream.toByteArray()
                }
            }
        }


        private fun addPDFPageFromImage(doc: PDDocument, origImg: ByteArray, imgFormat: String) {
            val page = PDPage(A4)
            doc.addPage(page)
            val scaledImg = ImageScaler.downToA4(origImg, imgFormat)
            PDPageContentStream(doc, page).use { contentStream ->
                val ximage = PDImageXObject.createFromByteArray(doc, scaledImg, "img")
                contentStream.drawImage(ximage, A4.lowerLeftX, A4.lowerLeftY)
            }
        }
    }
}
