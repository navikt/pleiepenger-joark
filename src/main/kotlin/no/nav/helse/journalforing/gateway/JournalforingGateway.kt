package no.nav.helse.journalforing.gateway

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.prometheus.client.Histogram
import no.nav.helse.Health
import no.nav.helse.HealthCheck
import no.nav.helse.HealthCheckObserver
import no.nav.helse.HttpRequest
import no.nav.helse.systembruker.SystembrukerService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.IllegalStateException
import java.net.URL

private val logger: Logger = LoggerFactory.getLogger("nav.JournalforingGateway")

private val nyJournalforing = Histogram.build(
    "histogram_ny_journalforing_joark",
    "Tidsbruk for ny journalføring mot Joark"
).register()


/*
    https://dokmotinngaaende-q1.nais.preprod.local/rest/mottaInngaaendeForsendelse

 */

class JournalforingGateway(
    private val httpClient: HttpClient,
    baseUrl: URL,
    private val systembrukerService: SystembrukerService
) : HealthCheck {

    private val mottaInngaaendeForsendelseUrl = HttpRequest.buildURL(
        baseUrl = baseUrl,
        pathParts = listOf("rest", "mottaInngaaendeForsendelse")
    )

    private val healthCheckObserver = HealthCheckObserver(
        name = "opprettelse_av_journalpost",
        help = "Opprettelse av journalpost mot dokmotinngaaende på $mottaInngaaendeForsendelseUrl"
    )

    override suspend fun check(): Health {
        return healthCheckObserver.health()
    }

    internal suspend fun jorunalfor(request: JournalPostRequest) : JournalPostResponse {
        return healthCheckObserver.observe { request(request) }
    }

    private suspend fun request(request: JournalPostRequest) : JournalPostResponse {
        val httpRequest = HttpRequestBuilder()
        httpRequest.header(HttpHeaders.Authorization, systembrukerService.getAuthorizationHeader())
        httpRequest.method = HttpMethod.Post
        httpRequest.contentType(ContentType.Application.Json)
        httpRequest.body = request
        httpRequest.url(mottaInngaaendeForsendelseUrl)

        val response = HttpRequest.monitored<JournalPostResponse>(
            httpClient = httpClient,
            httpRequest = httpRequest,
            histogram = nyJournalforing
        )

        if (request.forsokEndeligJF && JournalTilstand.ENDELIG_JOURNALFOERT != journalTilstandFraString(response.journalTilstand)) {
            throw IllegalStateException("Journalføring '$response' var forventet å bli endelig journalført, men ble det ikke..")
        } else {
            return response
        }
    }
}