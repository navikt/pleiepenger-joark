package no.nav.helse

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.application.*
import io.ktor.auth.Authentication
import io.ktor.auth.authenticate
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.jwt.jwt
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.features.*
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.request.header
import io.ktor.response.header
import io.ktor.routing.Routing
import io.ktor.util.KtorExperimentalAPI
import io.prometheus.client.CollectorRegistry
import io.prometheus.client.hotspot.DefaultExports
import no.nav.helse.dokument.DokumentGateway
import no.nav.helse.dokument.DokumentService
import no.nav.helse.dokument.ContentTypeService
import no.nav.helse.journalforing.api.journalforingApis
import no.nav.helse.journalforing.api.metadataStatusPages
import no.nav.helse.journalforing.converter.Image2PDFConverter
import no.nav.helse.journalforing.gateway.JournalforingGateway
import no.nav.helse.journalforing.v1.JournalforingV1Service
import no.nav.helse.systembruker.SystembrukerGateway
import no.nav.helse.systembruker.SystembrukerService
import no.nav.helse.validering.valideringStatusPages
import org.apache.http.impl.conn.SystemDefaultRoutePlanner
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.ProxySelector
import java.util.*
import java.util.concurrent.TimeUnit

private val logger: Logger = LoggerFactory.getLogger("nav.PleiepengerJoark")
private const val GENERATED_REQUEST_ID_PREFIX = "generated-"

fun main(args: Array<String>): Unit  = io.ktor.server.netty.EngineMain.main(args)

@KtorExperimentalAPI
fun Application.pleiepengerJoark() {
    val collectorRegistry = CollectorRegistry.defaultRegistry
    DefaultExports.initialize()

    val joarkHttpClient = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = JacksonSerializer{
                ObjectMapper.joark(this)
            }
        }
        engine {
            customizeClient { setProxyRoutePlanner() }
        }
    }
    val systembrukerOgDokumentHttpClient = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = JacksonSerializer{
                ObjectMapper.server(this)
            }
        }
        engine {
            customizeClient { setProxyRoutePlanner() }
        }
    }

    val configuration = Configuration(environment.config)
    configuration.logIndirectlyUsedConfiguration()

    val authorizedSystems = configuration.getAuthorizedSystemsForRestApi()

    val jwkProvider = JwkProviderBuilder(configuration.getJwksUrl())
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()


    install(Authentication) {
        jwt {
            verifier(jwkProvider, configuration.getIssuer())
            realm = "pleiepenger-joark"
            validate { credentials ->
                logger.info("authorization attempt for ${credentials.payload.subject}")
                if (credentials.payload.subject in authorizedSystems) {
                    logger.info("authorization ok")
                    return@validate JWTPrincipal(credentials.payload)
                }
                logger.warn("authorization failed")
                return@validate null
            }
        }
    }

    install(ContentNegotiation) {
        jackson {
            ObjectMapper.server(this)
        }
    }

    install(StatusPages) {
        defaultStatusPages()
        valideringStatusPages()
        metadataStatusPages()
    }

    val systembrukerService = SystembrukerService(
        systembrukerGateway = SystembrukerGateway(
            httpClient = systembrukerOgDokumentHttpClient,
            clientId = configuration.getServiceAccountClientId(),
            clientSecret = configuration.getServiceAccountClientSecret(),
            scopes = configuration.getServiceAccountScopes(),
            tokenUrl = configuration.getTokenUrl()
        )
    )

    val journalforingGateway = JournalforingGateway(
        httpClient = joarkHttpClient,
        baseUrl = configuration.getDokmotinngaaendeBaseUrl(),
        systembrukerService = systembrukerService
    )

    val dokumentGateway = DokumentGateway(
        httpClient = systembrukerOgDokumentHttpClient,
        systembrukerService = systembrukerService
    )

    install(Routing) {
        authenticate {
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
        monitoring(
            collectorRegistry = collectorRegistry,
            healthChecks = listOf(
                systembrukerService,
                dokumentGateway,
                journalforingGateway
            ),
            healthCheckUrls = mapOf(
                Pair(configuration.getJwksUrl(), HttpStatusCode.OK)
            )
        )
    }

    install(CallId) {
        header(HttpHeaders.XCorrelationId)
    }

    install(CallLogging) {
        callIdMdc("correlation_id")
        mdc("request_id") { call ->
            val requestId = call.request.header(HttpHeaders.XRequestId)?.removePrefix(GENERATED_REQUEST_ID_PREFIX) ?: "$GENERATED_REQUEST_ID_PREFIX${UUID.randomUUID()}"
            call.response.header(HttpHeaders.XRequestId, requestId)
            requestId
        }
    }
}

fun HttpAsyncClientBuilder.setProxyRoutePlanner() {
    setRoutePlanner(SystemDefaultRoutePlanner(ProxySelector.getDefault()))
}