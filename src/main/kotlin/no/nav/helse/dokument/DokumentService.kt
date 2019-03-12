package no.nav.helse.dokument

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import no.nav.helse.CorrelationId
import no.nav.helse.journalforing.AktoerId
import no.nav.helse.journalforing.converter.Image2PDFConverter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL

private val logger: Logger = LoggerFactory.getLogger("nav.DokumentService")

class DokumentService(
    private val dokumentGateway: DokumentGateway,
    private val image2PDFConverter: Image2PDFConverter,
    private val contentTypeService: ContentTypeService
) {

    suspend fun hentDokumenter(urls: List<URL>,
                               aktoerId: AktoerId,
                               correlationId: CorrelationId): List<Dokument> {
        logger.trace("Henter ${urls.size} dokumenter.")
        val alleDokumenter = coroutineScope {
            val futures = mutableListOf<Deferred<Dokument>>()
            urls.forEach {
                futures.add(async {
                    dokumentGateway.hentDokument(
                        url = it,
                        correlationId = correlationId,
                        aktoerId = aktoerId
                    )
                })

            }
            futures.awaitAll()
        }

        logger.trace("Alle dokumenter hentet.")
        val bildeDokumenter = alleDokumenter.filter { contentTypeService.isSupportedImage(it.contentType) }
        logger.trace("${bildeDokumenter.size} bilder.")
        val applicationDokumenter = alleDokumenter.filter { contentTypeService.isSupportedApplication(it.contentType) }
        logger.trace("${applicationDokumenter.size} andre støttede dokumenter.")
        val ikkeSupporterteDokumenter = alleDokumenter.filter { !contentTypeService.isSupported(it.contentType) }
        if (ikkeSupporterteDokumenter.isNotEmpty()) {
            logger.warn("${ikkeSupporterteDokumenter.size} dokumenter som ikke støttes. Disse vil utelates fra journalføring.")
        }

        val supporterteDokumenter = applicationDokumenter.toMutableList()

        logger.trace("Gjør om de ${bildeDokumenter.size} bildene til PDF.")
        bildeDokumenter.forEach { it ->
            supporterteDokumenter.add(
                Dokument(
                    title = it.title,
                    contentType = "application/pdf",
                    content = image2PDFConverter.convert(bytes = it.content, contentType = it.contentType)
                )
            )
        }

        logger.trace("Endringer fra bilde til PDF gjennomført.")
        return supporterteDokumenter
    }
}