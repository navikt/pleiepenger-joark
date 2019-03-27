package no.nav.helse.journalforing.gateway

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.logging.Logging
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.*
import no.nav.helse.dusseldorf.ktor.client.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.IllegalStateException
import java.net.URL

private val logger: Logger = LoggerFactory.getLogger("nav.JournalforingGateway")

/*
    https://dokmotinngaaende-q1.nais.preprod.local/rest/mottaInngaaendeForsendelse
 */

class JournalforingGateway(
    baseUrl: URL,
    private val systemCredentialsProvider: SystemCredentialsProvider
) {

    private val monitoredHttpClient = MonitoredHttpClient(
        source = "pleiepenger-joark",
        destination = "dokmotinngaaende",
        httpClient = HttpClient(Apache) {
            install(JsonFeature) {
                serializer = JacksonSerializer { configureObjectMapper(this) }
            }
            engine {
                customizeClient { setProxyRoutePlanner() }
            }
            install (Logging) {
                sl4jLogger("dokmotinngaaende")
            }
        }
    )

    private val mottaInngaaendeForsendelseUrl = Url.buildURL(
        baseUrl = baseUrl,
        pathParts = listOf("rest", "mottaInngaaendeForsendelse")
    )


    internal suspend fun jorunalfor(request: JournalPostRequest) : JournalPostResponse {
        val httpRequest = HttpRequestBuilder()
        httpRequest.header(HttpHeaders.Authorization, systemCredentialsProvider.getAuthorizationHeader())
        httpRequest.method = HttpMethod.Post
        httpRequest.contentType(ContentType.Application.Json)
        httpRequest.body = request
        httpRequest.url(mottaInngaaendeForsendelseUrl)

        val response = monitoredHttpClient.requestAndReceive<JournalPostResponse>(httpRequest)

        if (request.forsokEndeligJF && JournalTilstand.ENDELIG_JOURNALFOERT != journalTilstandFraString(response.journalTilstand)) {
            throw IllegalStateException("Journalføring '$response' var forventet å bli endelig journalført, men ble det ikke..")
        } else {
            return response
        }
    }

    private fun configureObjectMapper(objectMapper: ObjectMapper) : ObjectMapper {
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        return objectMapper
    }
}