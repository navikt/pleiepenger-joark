package no.nav.helse.dokument

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.core.Headers
import com.github.kittinunf.fuel.core.ResponseResultOf
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
import no.nav.helse.dusseldorf.ktor.metrics.Operation
import no.nav.helse.dusseldorf.oauth2.client.CachedAccessTokenClient
import no.nav.helse.journalforing.AktoerId
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI

class DokumentGateway(
    private val accessTokenClient: CachedAccessTokenClient
) {
    private val objectMapper = configuredObjectMapper()

    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(DokumentGateway::class.java)

    }
    suspend fun hentDokumenter(
        urls : List<URI>,
        aktoerId: AktoerId,
        correlationId: CorrelationId) : List<Dokument> {
        val authorizationHeader = accessTokenClient.getAccessToken(setOf("openid")).asAuthoriationHeader()

        logger.trace("Henter dokumenter")
        val triplets = coroutineScope {
            val futures = mutableListOf<Deferred<ResponseResultOf<String>>>()
            urls.forEach { url ->
                futures.add(async {
                    hentDokument(url, aktoerId, authorizationHeader, correlationId)
                })
            }
            futures.awaitAll()
        }
        logger.trace("Håndterer response")

        return triplets.map { (request, _, result) ->
            result.fold(
                { success -> objectMapper.readValue<Dokument>(success)},
                { error ->
                    logger.error(error.toString())
                    throw IllegalStateException("Feil ved henting av dokument '${request.url}'")
                }
            )
        }

    }

    private suspend fun hentDokument(
        url: URI,
        aktoerId: AktoerId,
        authorizationHeader: String,
        correlationId: CorrelationId
    ) : ResponseResultOf<String> {
        val urlMedEier = Url.buildURL(baseUrl = url, queryParameters = mapOf(Pair("eier", listOf(aktoerId.value))))
        val httpRequst = urlMedEier.toString()
            .httpGet()
            .header(
                Headers.ACCEPT to "application/json",
                Headers.AUTHORIZATION to authorizationHeader,
                HttpHeaders.XCorrelationId to correlationId.id
            )

        return Operation.monitored(
            app = "pleiepenger-joark",
            operation = "hente-dokument",
            resultResolver = { 200 == it.second.statusCode}
        ) {
            httpRequst.awaitStringResponseResult()
        }
    }

    private fun configuredObjectMapper() : ObjectMapper {
        val objectMapper = jacksonObjectMapper()
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        objectMapper.propertyNamingStrategy = PropertyNamingStrategy.SNAKE_CASE
        return objectMapper
    }
}