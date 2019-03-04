package no.nav.helse.dokument

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import no.nav.helse.CorrelationId
import no.nav.helse.HttpRequest
import no.nav.helse.systembruker.SystembrukerService
import java.net.URL

class DokumentGateway(
    private val httpClient: HttpClient,
    private val systembrukerService: SystembrukerService
) {
    suspend fun hentDokument(url : URL,
                             correlationId: CorrelationId) : Dokument {
        val httpRequest = HttpRequestBuilder()
        httpRequest.header(HttpHeaders.Authorization, systembrukerService.getAuthorizationHeader())
        httpRequest.header(HttpHeaders.XCorrelationId, correlationId.id)
        httpRequest.method = HttpMethod.Get
        httpRequest.url(url)

        return HttpRequest.monitored(
            httpClient = httpClient,
            httpRequest = httpRequest
        )
    }
}