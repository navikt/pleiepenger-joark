package no.nav.helse

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.extension.Extension
import com.github.tomakehurst.wiremock.matching.ContainsPattern
import no.nav.security.oidc.test.support.JwkGenerator
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

private val logger: Logger = LoggerFactory.getLogger("nav.WiremockWrapper")
private const val jwkSetPath = "/auth-mock/jwk-set"
private const val tokenPath = "/auth-mock/token"
private const val getAccessTokenPath = "/auth-mock/get-test-access-token"

private const val dokmotinngaaendeBasePath = "/dokmotinngaaende-mock"
private const val dokmotinngaaendeMottaInngaaendeForsendelsePath = "$dokmotinngaaendeBasePath/rest/mottaInngaaendeForsendelse"

private const val pleiepengerDokumentPath = "/pleiepenger-dokument-mock"

private const val subject = "srvpps-prosessering"


object WiremockWrapper {

    fun bootstrap(
        port: Int? = null,
        extensions : Array<Extension> = arrayOf()) : WireMockServer {

        val wireMockConfiguration = WireMockConfiguration.options()

        extensions.forEach {
            wireMockConfiguration.extensions(it)
        }

        if (port == null) {
            wireMockConfiguration.dynamicPort()
        } else {
            wireMockConfiguration.port(port)
        }

        val wireMockServer = WireMockServer(wireMockConfiguration)

        wireMockServer.start()
        WireMock.configureFor(wireMockServer.port())

        // Authorization
        stubGetSystembrukerToken()
        stubJwkSet()
        provideGetAccessTokenEndPoint(wireMockServer.baseUrl())

        // Dokument
        stubGetDokument()

        // dokmotinngaaende

        logger.info("Mock available on '{}'", wireMockServer.baseUrl())
        return wireMockServer
    }

    fun stubMottaInngaaendeForsendelseOk(
        sakId: String,
        tilstand: String) {
        WireMock.stubFor(
            WireMock.post(WireMock.urlMatching(".*$dokmotinngaaendeMottaInngaaendeForsendelsePath"))
                .withRequestBody(ContainsPattern("""
                    "arkivSakId" : "$sakId"
                    """.trimIndent()))
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                                "journalpostId": "1234",
                                "journalTilstand": "$tilstand"
                            }
                        """.trimIndent())
                )
        )
    }

    private fun stubGetDokument() {
        val content = Base64.getEncoder().encodeToString("iPhone_6.jpg".fromResources())
        WireMock.stubFor(
            WireMock.get(WireMock.urlPathMatching(".*$pleiepengerDokumentPath.*"))
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                                "content": "$content",
                                "content_type": "image/jpeg",
                                "title": "Dette er en tittel"
                            }
                        """.trimIndent()
                        )
                )
        )
    }

    fun stubGetDokumentJson(
        dokumentId: String
    ) {
        val content = Base64.getEncoder().encodeToString("jwkset.json".fromResources())
        WireMock.stubFor(
            WireMock.get(WireMock.urlPathMatching(".*$pleiepengerDokumentPath.*/$dokumentId"))
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                                "content": "$content",
                                "content_type": "application/json",
                                "title": "Dette er en tittel"
                            }
                        """.trimIndent()
                        )
                )
        )
    }

    fun stubGetDokumentPdf(
        dokumentId: String
    ) {
        val content = Base64.getEncoder().encodeToString("test.pdf".fromResources())
        WireMock.stubFor(
            WireMock.get(WireMock.urlPathMatching(".*$pleiepengerDokumentPath.*/$dokumentId"))
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                            {
                                "content": "$content",
                                "content_type": "application/pdf",
                                "title": "Dette er en tittel"
                            }
                        """.trimIndent()
                        )
                )
        )
    }

    private fun stubGetSystembrukerToken() {
        WireMock.stubFor(
            WireMock.get(WireMock.urlPathMatching(".*$tokenPath.*"))
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"i-am-an-access-token\", \"expires_in\": 5000}")
                )
        )
    }

    private fun stubJwkSet() {
        WireMock.stubFor(
            WireMock.get(WireMock.urlPathMatching(".*$jwkSetPath.*"))
                .willReturn(
                    WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(200)
                        .withBody(WiremockWrapper::class.java.getResource(JwkGenerator.DEFAULT_JWKSET_FILE).readText())
                )
        )
    }

    private fun provideGetAccessTokenEndPoint(issuer: String) {
        val jwt = Authorization.getAccessToken(issuer, subject)
        WireMock.stubFor(
            WireMock.get(WireMock.urlPathMatching(".*$getAccessTokenPath.*"))
                .willReturn(
                    WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"$jwt\", \"expires_in\": 5000}")
                )
        )
    }
}

fun WireMockServer.getJwksUrl() : String {
    return baseUrl() + jwkSetPath
}

fun WireMockServer.getTokenUrl() : String {
    return baseUrl() + tokenPath
}

fun WireMockServer.getDokmotinngaaendeUrl() : String {
    return baseUrl() + dokmotinngaaendeBasePath
}

fun WireMockServer.getPleiepengerDokumentUrl() : String {
    return baseUrl() + pleiepengerDokumentPath
}

fun WireMockServer.getSubject() : String {
    return subject
}

fun String.fromResources() : ByteArray {
    return Thread.currentThread().contextClassLoader.getResource(this).readBytes()
}