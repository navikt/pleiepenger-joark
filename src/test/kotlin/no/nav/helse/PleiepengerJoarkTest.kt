package no.nav.helse

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.typesafe.config.ConfigFactory
import io.ktor.config.ApplicationConfig
import io.ktor.config.HoconApplicationConfig
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.createTestEnvironment
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import io.ktor.util.KtorExperimentalAPI
import no.nav.helse.dusseldorf.ktor.jackson.dusseldorfConfigured
import no.nav.helse.dusseldorf.ktor.testsupport.jws.Azure
import no.nav.helse.dusseldorf.ktor.testsupport.jws.NaisSts
import no.nav.helse.dusseldorf.ktor.testsupport.wiremock.WireMockBuilder
import no.nav.helse.journalforing.v1.MeldingV1
import org.junit.AfterClass
import org.junit.BeforeClass
import org.skyscreamer.jsonassert.JSONAssert
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI
import java.time.ZonedDateTime
import kotlin.test.*

@KtorExperimentalAPI
class PleiepengerJoarkTest {

    @KtorExperimentalAPI
    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(PleiepengerJoarkTest::class.java)

        private val wireMockServer: WireMockServer = WireMockBuilder()
            .withNaisStsSupport()
            .withAzureSupport()
            .build()
            .stubGetDokument()
            .stubDomotInngaaendeIsReady()

        private val objectMapper = jacksonObjectMapper().dusseldorfConfigured()
        private val authorizedAccessToken = Azure.V1_0.generateJwt(clientId = "pleiepengesoknad-prosessering", audience = "pleiepenger-joark")

        fun getConfig() : ApplicationConfig {
            val fileConfig = ConfigFactory.load()
            val testConfig = ConfigFactory.parseMap(TestConfiguration.asMap(
                wireMockServer = wireMockServer,
                azureAuthorizedClients = setOf("pleiepengesoknad-prosessering"),
                pleiepengerJoarkAzureClientId = "pleiepenger-joark"
            ))
            val mergedConfig = testConfig.withFallback(fileConfig)

            return HoconApplicationConfig(mergedConfig)
        }

        val engine = TestApplicationEngine(createTestEnvironment {
            config = getConfig()
        })

        @BeforeClass
        @JvmStatic
        fun buildUp() {
            engine.start(wait = true)
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            logger.info("Tearing down")
            wireMockServer.stop()
            logger.info("Tear down complete")
        }
    }

    @Test
    fun `test isready, isalive og metrics`() {
        with(engine) {
            handleRequest(HttpMethod.Get, "/isready") {}.apply {
                assertEquals(HttpStatusCode.OK, response.status())
                handleRequest(HttpMethod.Get, "/isalive") {}.apply {
                    assertEquals(HttpStatusCode.OK, response.status())
                    handleRequest(HttpMethod.Get, "/metrics") {}.apply {
                        assertEquals(HttpStatusCode.OK, response.status())
                        handleRequest(HttpMethod.Get, "/health") {}.apply {
                            assertEquals(HttpStatusCode.OK, response.status())
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `gyldig melding til joark gir ok response med journalfoert jorunalpostID`() {
        val jpegDokumentId = "1234" // Default mocket som JPEG
        val pdfDokumentId = "4567"
        stubGetDokumentPdf(pdfDokumentId)
        val jsonDokumentId = "78910"
        stubGetDokumentJson(jsonDokumentId)

        stubMottaInngaaendeForsendelseOk(tilstand = "MIDLERTIDIG_JOURNALFOERT")

        val request = MeldingV1(
            aktoerId = "1234",
            mottatt = ZonedDateTime.now(),
            dokumenter = listOf(
                listOf(
                    getDokumentUrl(pdfDokumentId),
                    getDokumentUrl(jsonDokumentId)
                ),
                listOf(
                    getDokumentUrl(jpegDokumentId)
                )
            )
        )

        requestAndAssert(
            request = request,
            expectedResponse = """
                {
                    "journal_post_id" : "1234"
                }
            """.trimIndent(),
            expectedCode = HttpStatusCode.Created
        )
    }

    @Test
    fun `melding uten correlation id skal feile`() {
        val request = MeldingV1(
            aktoerId = "12345",
            mottatt = ZonedDateTime.now(),
            dokumenter = listOf(listOf(
                getDokumentUrl("1234"),
                getDokumentUrl("5678")
            ))
        )


        requestAndAssert(
            request = request,
            leggTilCorrelationId = false,
            expectedCode = HttpStatusCode.BadRequest,
            expectedResponse = """
                {
                    "type": "/problem-details/invalid-request-parameters",
                    "title": "invalid-request-parameters",
                    "detail": "Requesten inneholder ugyldige paramtere.",
                    "status": 400,
                    "instance": "about:blank",
                    "invalid_parameters" : [
                        {
                            "name" : "X-Correlation-ID",
                            "reason" : "Correlation ID må settes.",
                            "type": "header",
                            "invalid_value": null
                        }
                    ]
                }
            """.trimIndent()
        )
    }

    @Test
    fun `mangler authorization header`() {
        val request = MeldingV1(
            aktoerId = "12345",
            mottatt = ZonedDateTime.now(),
            dokumenter = listOf(listOf(
                getDokumentUrl("1234"),
                getDokumentUrl("5678")
            ))
        )

        requestAndAssert(
            request = request,
            leggTilAuthorization = false,
            expectedCode = HttpStatusCode.Unauthorized,
            expectedResponse = null
        )
    }

    @Test
    fun `request fra ikke tillatt system`() {
        val request = MeldingV1(
            aktoerId = "12345",
            mottatt = ZonedDateTime.now(),
            dokumenter = listOf(listOf(
                getDokumentUrl("1234"),
                getDokumentUrl("5678")
            ))
        )

        requestAndAssert(
            request = request,
            expectedCode = HttpStatusCode.Forbidden,
            accessToken = Azure.V1_0.generateJwt(clientId = "pleiepengesoknad-prosessering", audience = "feil-audience"),
            expectedResponse = """
            {
                "type": "/problem-details/unauthorized",
                "title": "unauthorized",
                "status": 403,
                "detail": "Requesten inneholder ikke tilstrekkelige tilganger.",
                "instance": "about:blank"
            }
            """.trimIndent()
        )
    }

    @Test
    fun `melding uten dokumenter skal feile`() {
        val aktoerId = "123456F"
        val request = MeldingV1(
            aktoerId = aktoerId,
            mottatt = ZonedDateTime.now(),
            dokumenter = listOf()
        )

        requestAndAssert(
            request = request,
            expectedCode = HttpStatusCode.BadRequest,
            expectedResponse = """
            {
                "type": "/problem-details/invalid-request-parameters",
                "title": "invalid-request-parameters",
                "status": 400,
                "detail": "Requesten inneholder ugyldige paramtere.",
                "instance": "about:blank",
                "invalid_parameters": [{
                    "type": "entity",
                    "name": "dokument",
                    "reason": "Det må sendes minst ett dokument",
                    "invalid_value": []
                }, {
                    "type": "entity",
                    "name": "aktoer_id",
                    "reason": "Ugyldig AktørID. Kan kun være siffer.",
                    "invalid_value": "123456F"
                }]
            }
            """.trimIndent()
        )
    }

    @Test
    fun `melding med tomme dokumentbolker skal feile`() {
        val aktoerId = "123456"
        val request = MeldingV1(
            aktoerId = aktoerId,
            mottatt = ZonedDateTime.now(),
            dokumenter = listOf(
                listOf(getDokumentUrl("1234")),
                listOf()
            )
        )

        requestAndAssert(
            request = request,
            expectedCode = HttpStatusCode.BadRequest,
            expectedResponse = """
                {
                    "type": "/problem-details/invalid-request-parameters",
                    "title": "invalid-request-parameters",
                    "detail": "Requesten inneholder ugyldige paramtere.",
                    "status": 400,
                    "instance": "about:blank",
                    "invalid_parameters" : [
                        {
                            "name" : "dokument_bolk",
                            "reason" : "Det må være minst et dokument i en dokument bolk.",
                            "type": "entity",
                            "invalid_value": []
                        }
                    ]
                }
            """.trimIndent()
        )
    }


    private fun getDokumentUrl(dokumentId : String) = URI("${wireMockServer.getPleiepengerDokumentUrl()}/$dokumentId")

    private fun requestAndAssert(request : MeldingV1,
                                 expectedResponse : String?,
                                 expectedCode : HttpStatusCode,
                                 leggTilCorrelationId : Boolean = true,
                                 leggTilAuthorization : Boolean = true,
                                 accessToken : String = authorizedAccessToken) {
        with(engine) {
            handleRequest(HttpMethod.Post, "/v1/journalforing") {
                if (leggTilAuthorization) {
                    addHeader(HttpHeaders.Authorization, "Bearer $accessToken")
                }
                if (leggTilCorrelationId) {
                    addHeader(HttpHeaders.XCorrelationId, "123156")
                }
                addHeader(HttpHeaders.ContentType, "application/json")
                setBody(objectMapper.writeValueAsString(request))
            }.apply {
                logger.info("Response Entity = ${response.content}")
                logger.info("Expected Entity = $expectedResponse")
                assertEquals(expectedCode, response.status())
                if (expectedResponse == null) assertEquals(expectedResponse, response.content)
                else JSONAssert.assertEquals(expectedResponse, response.content!!, true)
            }
        }
    }
}