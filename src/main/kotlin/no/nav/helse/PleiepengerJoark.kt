package no.nav.helse

import io.ktor.application.*
import io.ktor.auth.Authentication
import io.ktor.auth.authenticate
import io.ktor.features.*
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.jackson.jackson
import io.ktor.metrics.micrometer.MicrometerMetrics
import io.ktor.routing.Routing
import io.ktor.util.KtorExperimentalAPI
import io.prometheus.client.hotspot.DefaultExports
import no.nav.helse.dokument.DokumentGateway
import no.nav.helse.dokument.DokumentService
import no.nav.helse.dokument.ContentTypeService
import no.nav.helse.dusseldorf.ktor.auth.*
import no.nav.helse.dusseldorf.ktor.client.*
import no.nav.helse.dusseldorf.ktor.core.*
import no.nav.helse.dusseldorf.ktor.health.HealthRoute
import no.nav.helse.dusseldorf.ktor.health.HealthService
import no.nav.helse.dusseldorf.ktor.health.TryCatchHealthCheck
import no.nav.helse.dusseldorf.ktor.jackson.JacksonStatusPages
import no.nav.helse.dusseldorf.ktor.jackson.dusseldorfConfigured
import no.nav.helse.dusseldorf.ktor.metrics.MetricsRoute
import no.nav.helse.dusseldorf.ktor.metrics.init
import no.nav.helse.dusseldorf.oauth2.client.CachedAccessTokenClient
import no.nav.helse.journalforing.api.journalforingApis
import no.nav.helse.journalforing.converter.Image2PDFConverter
import no.nav.helse.journalforing.gateway.JournalforingGateway
import no.nav.helse.journalforing.v1.JournalforingV1Service
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI

private val logger: Logger = LoggerFactory.getLogger("nav.PleiepengerJoark")

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

@KtorExperimentalAPI
fun Application.pleiepengerJoark() {
    val appId = environment.config.id()
    logProxyProperties()
    DefaultExports.initialize()

    val configuration = Configuration(environment.config)
    val issuers = configuration.issuers()

    install(Authentication) {
        multipleJwtIssuers(issuers)
    }

    install(ContentNegotiation) {
        jackson {
            dusseldorfConfigured()
        }
    }

    install(StatusPages) {
        DefaultStatusPages()
        JacksonStatusPages()
        AuthStatusPages()
    }

    val naisStsClient = configuration.naisStsClient()
    val naisStsAccessTokenClient = NaisStsAccessTokenClient(
        clientId = naisStsClient.clientId(),
        clientSecret = naisStsClient.clientSecret,
        tokenEndpoint = naisStsClient.tokenEndpoint()
    )
    val cachedNaisStsAccessTokenClient = CachedAccessTokenClient(naisStsAccessTokenClient)

    val journalforingGateway = JournalforingGateway(
        baseUrl = configuration.getDokmotinngaaendeBaseUrl(),
        accessTokenClient = cachedNaisStsAccessTokenClient
    )

    val dokumentGateway = DokumentGateway(
        accessTokenClient = cachedNaisStsAccessTokenClient
    )

    val healthService = HealthService(setOf(
        TryCatchHealthCheck(
            name = "NaisStsAccessTokenHealthCheck"
        ) {
            naisStsAccessTokenClient.getAccessToken(setOf("openid"))
        },
        HttpRequestHealthCheck(
            urlConfigMap = issuers.healthCheckMap(mutableMapOf(
                Url.buildURL(baseUrl = configuration.getDokmotinngaaendeBaseUrl(), pathParts = listOf("isReady")) to HttpRequestHealthConfig(expectedStatus = HttpStatusCode.OK)
            ))
        ))
    )

    install(CallIdRequired)

    install(Routing) {
        authenticate(*issuers.allIssuers()) {
            requiresCallId {
                journalforingApis(
                    journalforingV1Service = JournalforingV1Service(
                        journalforingGateway = journalforingGateway,
                        dokumentService = DokumentService(
                            dokumentGateway = dokumentGateway,
                            image2PDFConverter = Image2PDFConverter(),
                            contentTypeService = ContentTypeService()
                        )
                    )
                )
            }
        }
        MetricsRoute()
        DefaultProbeRoutes()
        HealthRoute(
            healthService = healthService
        )
    }

    install(MicrometerMetrics) {
        init(appId)
    }


    install(CallId) {
        fromXCorrelationIdHeader()
    }

    intercept(ApplicationCallPipeline.Monitoring) {
        call.request.log()
    }

    install(CallLogging) {
        correlationIdAndRequestIdInMdc()
        logRequests()
    }
}

private fun Map<Issuer, Set<ClaimRule>>.healthCheckMap(
    initial : MutableMap<URI, HttpRequestHealthConfig>
) : Map<URI, HttpRequestHealthConfig> {
    forEach { issuer, _ ->
        initial[issuer.jwksUri()] = HttpRequestHealthConfig(expectedStatus = HttpStatusCode.OK, includeExpectedStatusEntity = false)
    }
    return initial.toMap()
}
