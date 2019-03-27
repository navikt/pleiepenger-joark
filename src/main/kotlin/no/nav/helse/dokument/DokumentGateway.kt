package no.nav.helse.dokument

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.logging.Logging
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import no.nav.helse.CorrelationId
import no.nav.helse.dusseldorf.ktor.client.*
import no.nav.helse.journalforing.AktoerId
import java.net.URL

class DokumentGateway(
    private val systemCredentialsProvider: SystemCredentialsProvider
) {

    private val monitoredHttpClient = MonitoredHttpClient(
        source = "pleiepenger-joark",
        destination = "pleiepenger-dokument",
        overridePaths = mapOf(
            Pair(Regex("/v1/dokument/.*"), "/dokument")
        ),
        httpClient = HttpClient(Apache) {
            install(JsonFeature) {
                serializer = JacksonSerializer { configureObjectMapper(this) }
            }
            engine {
                customizeClient { setProxyRoutePlanner() }
            }
            install (Logging) {
                sl4jLogger("pleiepenger-dokument")
            }
        }
    )

    suspend fun hentDokumenter(
        urls : List<URL>,
        aktoerId: AktoerId,
        correlationId: CorrelationId) : List<Dokument> {
        val authorizationHeader = systemCredentialsProvider.getAuthorizationHeader()

        return coroutineScope {
            val deferred = mutableListOf<Deferred<Dokument>>()
            urls.forEach {
                deferred.add(async {
                    request(
                        url = it,
                        correlationId = correlationId,
                        aktoerId = aktoerId,
                        authorizationHeader = authorizationHeader
                    )
                })
            }
            deferred.awaitAll()
        }
    }

    private suspend fun request(
        url : URL,
        aktoerId: AktoerId,
        correlationId: CorrelationId,
        authorizationHeader : String) : Dokument {

        val urlMedEier = Url.buildURL(baseUrl = url, queryParameters = mapOf(Pair("eier", listOf(aktoerId.value))))
        val httpRequest = HttpRequestBuilder()
        httpRequest.header(HttpHeaders.XCorrelationId, correlationId.id)
        httpRequest.header(HttpHeaders.Authorization, authorizationHeader)
        httpRequest.method = HttpMethod.Get
        httpRequest.url(urlMedEier)

        return monitoredHttpClient.requestAndReceive(
            httpRequestBuilder = httpRequest
        )
    }

    private fun configureObjectMapper(objectMapper: ObjectMapper) : ObjectMapper {
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        objectMapper.propertyNamingStrategy = PropertyNamingStrategy.SNAKE_CASE
        return objectMapper
    }
}