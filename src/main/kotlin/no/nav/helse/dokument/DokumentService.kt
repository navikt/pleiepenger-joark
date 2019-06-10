package no.nav.helse.dokument

import io.prometheus.client.Counter
import no.nav.helse.CorrelationId
import no.nav.helse.journalforing.AktoerId
import no.nav.helse.journalforing.converter.Image2PDFConverter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI

private val logger: Logger = LoggerFactory.getLogger(DokumentService::class.java)

private val dokumentContentTypeCounter = Counter.build()
    .name("dokument_content_type_counter")
    .labelNames("content_type")
    .help("Teller for dokumenttyper som journalføres.")
    .register()

class DokumentService(
    private val dokumentGateway: DokumentGateway,
    private val image2PDFConverter: Image2PDFConverter,
    private val contentTypeService: ContentTypeService
) {

    suspend fun hentDokumenter(urls: List<URI>,
                               aktoerId: AktoerId,
                               correlationId: CorrelationId): List<Dokument> {
        logger.trace("Henter ${urls.size} dokumenter.")
        val alleDokumenter = dokumentGateway.hentDokumenter(
            urls = urls,
            aktoerId = aktoerId,
            correlationId = correlationId
        )

        alleDokumenter.tellContentType()

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
        bildeDokumenter.forEach {
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

private fun List<Dokument>.tellContentType() {
    forEach {
        dokumentContentTypeCounter.labels(it.contentType).inc()
    }
}