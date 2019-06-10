package no.nav.helse.journalforing.gateway

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.core.Headers
import com.github.kittinunf.fuel.coroutines.awaitStringResponseResult
import com.github.kittinunf.fuel.httpPost
import io.ktor.http.*
import no.nav.helse.dusseldorf.ktor.client.*
import no.nav.helse.dusseldorf.ktor.metrics.Operation
import no.nav.helse.dusseldorf.oauth2.client.CachedAccessTokenClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.net.URI
import kotlin.IllegalStateException

private val logger: Logger = LoggerFactory.getLogger(JournalforingGateway::class.java)

/*
    https://dokmotinngaaende-q1.nais.preprod.local/rest/mottaInngaaendeForsendelse
 */

class JournalforingGateway(
    baseUrl: URI,
    private val accessTokenClient: CachedAccessTokenClient
) {

    private val mottaInngaaendeForsendelseUrl = Url.buildURL(
        baseUrl = baseUrl,
        pathParts = listOf("rest", "mottaInngaaendeForsendelse")
    ).toString()

    private val objectMapper = configuredObjectMapper()

    internal suspend fun jorunalfor(request: JournalPostRequest) : JournalPostResponse {
        val authorizationHeader = accessTokenClient.getAccessToken(setOf("openid")).asAuthoriationHeader()
        logger.trace("Genererer body for request")
        val body = objectMapper.writeValueAsBytes(request)
        val contentStream = { ByteArrayInputStream(body) }
        logger.trace("Generer http request")
        val httpRequest = mottaInngaaendeForsendelseUrl
            .httpPost()
            .body(contentStream)
            .header(
                Headers.AUTHORIZATION to authorizationHeader,
                Headers.CONTENT_TYPE to "application/json",
                Headers.ACCEPT to "application/json"
            )

        logger.trace("Sender reqeust")
        val (_, _, result) = Operation.monitored(
            app = "pleiepenger-joark",
            operation = "opprette-journalpost",
            resultResolver = { 200 == it.second.statusCode}
        ) {
            httpRequest.awaitStringResponseResult()
        }
        logger.trace("Håndterer response")
        val journalPostResponse : JournalPostResponse = result.fold(
            { success -> objectMapper.readValue(success) },
            { error ->
                logger.error(error.toString())
                throw IllegalStateException("Feil ved opperttelse av jorunalpost.")
            }
        )

        if (request.forsokEndeligJF && JournalTilstand.ENDELIG_JOURNALFOERT != journalTilstandFraString(journalPostResponse.journalTilstand)) {
            throw IllegalStateException("Journalføring '$journalPostResponse' var forventet å bli endelig journalført, men ble det ikke..")
        } else {
            return journalPostResponse
        }
    }

    private fun configuredObjectMapper() : ObjectMapper {
        val objectMapper = jacksonObjectMapper()
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        return objectMapper
    }
}
