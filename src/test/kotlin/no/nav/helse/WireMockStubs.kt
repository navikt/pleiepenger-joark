package no.nav.helse

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import no.nav.helse.dusseldorf.ktor.core.fromResources
import java.util.*

private const val dokmotinngaaendeBasePath = "/dokmotinngaaende-mock"
private const val dokmotinngaaendeMottaInngaaendeForsendelsePath = "$dokmotinngaaendeBasePath/rest/mottaInngaaendeForsendelse"
private const val pleiepengerDokumentPath = "/pleiepenger-dokument-mock"

internal fun stubMottaInngaaendeForsendelseOk(
    tilstand: String) {
    WireMock.stubFor(
        WireMock.post(WireMock.urlMatching(".*$dokmotinngaaendeMottaInngaaendeForsendelsePath"))
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

internal fun WireMockServer.stubDomotInngaaendeIsReady() : WireMockServer {
    WireMock.stubFor(
        WireMock.get(WireMock.urlMatching(".*$dokmotinngaaendeBasePath/isReady"))
            .willReturn(
                WireMock.aResponse().withStatus(200)
            )
    )
    return this
}

internal fun WireMockServer.stubGetDokument() : WireMockServer {
    val content = Base64.getEncoder().encodeToString("iPhone_6.jpg".fromResources().readBytes())
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
    return this
}

internal fun stubGetDokumentJson(
    dokumentId: String
) {
    val content = Base64.getEncoder().encodeToString("jwkset.json".fromResources().readBytes())
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

internal fun stubGetDokumentPdf(
    dokumentId: String
) {
    val content = Base64.getEncoder().encodeToString("test.pdf".fromResources().readBytes())
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


internal fun WireMockServer.getDokmotinngaaendeUrl() = baseUrl() + dokmotinngaaendeBasePath
internal fun WireMockServer.getPleiepengerDokumentUrl() = baseUrl() + pleiepengerDokumentPath