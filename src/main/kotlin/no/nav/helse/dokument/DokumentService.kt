package no.nav.helse.dokument

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import no.nav.helse.CorrelationId
import no.nav.helse.journalforing.AktoerId
import java.net.URL

class DokumentService(
    private val dokumentGateway: DokumentGateway
) {

    suspend fun hentDokumenter(urls: List<URL>,
                               aktoerId : AktoerId,
                               correlationId: CorrelationId): List<Dokument> {
        return coroutineScope {
            val dokumenter = mutableListOf<Deferred<Dokument>>()
            urls.forEach {
                dokumenter.add(async {
                    dokumentGateway.hentDokument(
                        url = it,
                        correlationId = correlationId,
                        aktoerId = aktoerId
                    )
                })

            }
            dokumenter.awaitAll()
        }
    }
}