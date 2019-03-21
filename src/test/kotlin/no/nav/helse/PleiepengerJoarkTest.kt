package no.nav.helse

import com.fasterxml.jackson.module.kotlin.readValue
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
import no.nav.helse.journalforing.api.JournalforingResponse
import no.nav.helse.journalforing.api.ManglerCorrelationId
import no.nav.helse.journalforing.v1.MeldingV1
import no.nav.helse.validering.Valideringsfeil
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.FixMethodOrder
import org.junit.runners.MethodSorters
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL
import java.time.ZonedDateTime
import kotlin.test.*

private val logger: Logger = LoggerFactory.getLogger("nav.PleiepengerJoarkTest")

@KtorExperimentalAPI
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PleiepengerJoarkTest {

    @KtorExperimentalAPI
    private companion object {

        private val wireMockServer: WireMockServer = WiremockWrapper.bootstrap()
        private val objectMapper = ObjectMapper.server()
        private val authorizedAccessToken = Authorization.getAccessToken(wireMockServer.baseUrl(), wireMockServer.getSubject())

        fun getConfig() : ApplicationConfig {
            val fileConfig = ConfigFactory.load()
            val testConfig = ConfigFactory.parseMap(TestConfiguration.asMap(wireMockServer = wireMockServer))
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
    fun `z_ test isready, isalive og metrics`() {
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
        WiremockWrapper.stubGetDokumentPdf(pdfDokumentId)
        val jsonDokumentId = "78910"
        WiremockWrapper.stubGetDokumentJson(jsonDokumentId)

        WiremockWrapper.stubMottaInngaaendeForsendelseOk(tilstand = "MIDLERTIDIG_JOURNALFOERT")

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
        val expectedResponse = JournalforingResponse(journalPostId = "1234")

        requestAndAssert(
            request = request,
            expectedResponse = expectedResponse,
            expectedCode = HttpStatusCode.Created
        )
    }

    @Test(expected = ManglerCorrelationId::class)
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
            leggTilCorrelationId = false
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
            expectedCode = HttpStatusCode.Unauthorized
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
            expectedCode = HttpStatusCode.Unauthorized,
            accessToken = Authorization.getAccessToken(wireMockServer.baseUrl(), "srvnotauthorized")
        )
    }

    @Test(expected = Valideringsfeil::class)
    fun `ugyldig aktoerID skal feile`() {
        val request = MeldingV1(
            aktoerId = "",
            mottatt = ZonedDateTime.now(),
            dokumenter = listOf(listOf(
                getDokumentUrl("1234"),
                getDokumentUrl("5678")
            ))
        )

        requestAndAssert(
            request = request
        )
    }

    @Test(expected = Valideringsfeil::class)
    fun `melding maa inneholde minst et dokument`() {
        val request = MeldingV1(
            aktoerId = "123456",
            mottatt = ZonedDateTime.now(),
            dokumenter = listOf()
        )

        requestAndAssert(
            request = request
        )
    }

    private fun getDokumentUrl(dokumentId : String) : URL{
        return URL("${wireMockServer.getPleiepengerDokumentUrl()}/$dokumentId")
    }

    private fun requestAndAssert(request : MeldingV1,
                                 expectedResponse : JournalforingResponse? = null,
                                 expectedCode : HttpStatusCode? = null,
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
                assertEquals(expectedCode, response.status())
                if (expectedResponse != null) {
                    assertEquals(expectedResponse, objectMapper.readValue(response.content!!))
                }
            }
        }
    }
}