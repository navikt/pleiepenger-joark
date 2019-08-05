package no.nav.helse.dokument

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.core.Headers
import com.github.kittinunf.fuel.coroutines.awaitStringResponseResult
import com.github.kittinunf.fuel.httpGet
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import no.nav.helse.CorrelationId
import no.nav.helse.dusseldorf.ktor.client.*
import no.nav.helse.dusseldorf.ktor.core.Retry
import no.nav.helse.dusseldorf.ktor.health.HealthCheck
import no.nav.helse.dusseldorf.ktor.health.Healthy
import no.nav.helse.dusseldorf.ktor.health.Result
import no.nav.helse.dusseldorf.ktor.health.UnHealthy
import no.nav.helse.dusseldorf.ktor.metrics.Operation
import no.nav.helse.dusseldorf.oauth2.client.AccessTokenClient
import no.nav.helse.dusseldorf.oauth2.client.CachedAccessTokenClient
import no.nav.helse.journalforing.AktoerId
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI
import java.time.Duration

class DokumentGateway(
    private val accessTokenClient: AccessTokenClient,
    private val henteDokumentScopes: Set<String>
) : HealthCheck {
    private val objectMapper = configuredObjectMapper()

    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(DokumentGateway::class.java)
        private const val HENTE_DOKUMENT_OPERATION = "hente-dokument"
        private const val HENTE_ALLE_DOKUMENTER_OPERATION = "hente-alle-dokumenter"
    }

    private val cachedAccessTokenClient = CachedAccessTokenClient(accessTokenClient)

    override suspend fun check(): Result {
        return try {
            accessTokenClient.getAccessToken(henteDokumentScopes)
            Healthy("DokumentGateway", "Henting av access token for henting av dokument OK.")
        } catch (cause: Throwable) {
            logger.error("Feil ved henting av access token for henting av dokument", cause)
            UnHealthy("DokumentGateway", "Henting av access token for henting av dokument Feilet.")
        }
    }

    suspend fun hentDokumenter(
        urls : List<URI>,
        aktoerId: AktoerId,
        correlationId: CorrelationId) : List<Dokument> {
        val authorizationHeader = cachedAccessTokenClient.getAccessToken(henteDokumentScopes).asAuthoriationHeader()

        return Operation.monitored(
            app = "pleiepenger-joark",
            operation = HENTE_ALLE_DOKUMENTER_OPERATION
        ) {
            coroutineScope {
                val futures = mutableListOf<Deferred<Dokument>>()
                urls.forEach { url ->
                    futures.add(async {
                        hentDokument(url, aktoerId, authorizationHeader, correlationId)
                    })
                }
                futures.awaitAll()
            }
        }
    }

    private suspend fun hentDokument(
        url: URI,
        aktoerId: AktoerId,
        authorizationHeader: String,
        correlationId: CorrelationId
    ) : Dokument {
        val urlMedEier = Url.buildURL(baseUrl = url, queryParameters = mapOf(Pair("eier", listOf(aktoerId.value))))
        val httpRequst = urlMedEier.toString()
            .httpGet()
            .header(
                Headers.ACCEPT to "application/json",
                Headers.AUTHORIZATION to authorizationHeader,
                HttpHeaders.XCorrelationId to correlationId.id
            )

        return Retry.retry(
            operation = HENTE_DOKUMENT_OPERATION,
            initialDelay = Duration.ofMillis(200),
            factor = 2.0
        ) {
            val (request, _, result ) = Operation.monitored(
                app = "pleiepenger-joark",
                operation = HENTE_DOKUMENT_OPERATION,
                resultResolver = { 200 == it.second.statusCode}
            ) { httpRequst.awaitStringResponseResult() }

            result.fold(
                { success -> objectMapper.readValue<Dokument>(success)},
                { error ->
                    logger.error("Error response = '${error.response.body().asString("text/plain")}' fra '${request.url}'")
                    logger.error(error.toString())
                    throw IllegalStateException("Feil ved henting av dokument.")
                }
            )
        }
    }

    private fun configuredObjectMapper() : ObjectMapper {
        val objectMapper = jacksonObjectMapper()
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        objectMapper.propertyNamingStrategy = PropertyNamingStrategy.SNAKE_CASE
        return objectMapper
    }
}