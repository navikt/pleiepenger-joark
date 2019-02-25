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
    private val image2PDFConverter: Image2PDFConverter
) {

    suspend fun hentDokumenter(urls: List<URL>,
                               aktoerId : AktoerId,
                               correlationId: CorrelationId): List<Dokument> {
        logger.trace("Henter ${urls.size} dokumenter.")
        val dokumenter = coroutineScope {
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


        logger.trace("Endrer fra bilde til PDF.")
        val pdfDokumenter = mutableListOf<Dokument>()

        dokumenter.forEach { it ->
            pdfDokumenter.add(
                Dokument(
                    title = it.title,
                    contentType = "application/pdf",
                    content = image2PDFConverter.convert(it.content)
                )
            )
        }

        logger.trace("Endring fra bilde til PDF gjennomført.")
        return pdfDokumenter
    }
}