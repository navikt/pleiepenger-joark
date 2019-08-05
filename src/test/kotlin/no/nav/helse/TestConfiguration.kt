package no.nav.helse

import com.github.tomakehurst.wiremock.WireMockServer
import no.nav.helse.dusseldorf.ktor.testsupport.jws.ClientCredentials
import no.nav.helse.dusseldorf.ktor.testsupport.wiremock.getAzureV1WellKnownUrl
import no.nav.helse.dusseldorf.ktor.testsupport.wiremock.getAzureV2WellKnownUrl
import no.nav.helse.dusseldorf.ktor.testsupport.wiremock.getNaisStsWellKnownUrl

object TestConfiguration {

    fun asMap(
        wireMockServer: WireMockServer? = null,
        port : Int = 8080,
        dokmotinngaaendeUrl : String? = wireMockServer?.getDokmotinngaaendeUrl(),

        konfigurerNaiStsClient: Boolean = true,
        konfigurerAzureClient: Boolean = true,

        konfigurerAzureIssuer: Boolean = true,
        azureAuthorizedClients: Set<String> = setOf("azure-client-1", "azure-client-2","azure-client-3"),
        pleiepengerJoarkAzureClientId: String? = "pleiepenger-joark",
        konfigurerNaisStsIssuer: Boolean = true,
        naisStsAuthoriedClients: Set<String> = setOf("srvpps-prosessering")

    ) : Map<String, String>{
        val map = mutableMapOf(
            Pair("ktor.deployment.port","$port"),
            Pair("nav.dokmotinngaaende_base_url", "$dokmotinngaaendeUrl")
        )

        // Clients
        if (wireMockServer != null && konfigurerNaiStsClient) {
            map["nav.auth.clients.0.alias"] = "nais-sts"
            map["nav.auth.clients.0.client_id"] = "srvpleiepenger-joark"
            map["nav.auth.clients.0.client_secret"] = "very-secret"
            map["nav.auth.clients.0.discovery_endpoint"] = wireMockServer.getNaisStsWellKnownUrl()
        }

        if (wireMockServer != null && konfigurerAzureClient) {
            map["nav.auth.clients.1.alias"] = "azure-v2"
            map["nav.auth.clients.1.client_id"] = "srvpleiepenger-joark"
            map["nav.auth.clients.1.private_key_jwk"] = ClientCredentials.ClientA.privateKeyJwk
            map["nav.auth.clients.1.certificate_hex_thumbprint"] = ClientCredentials.ClientA.certificateHexThumbprint
            map["nav.auth.clients.1.discovery_endpoint"] = wireMockServer.getAzureV2WellKnownUrl()
            map["nav.auth.scopes.hente-dokument"] = "srvpleiepenger-dokument/.default"
        }

        // Issuers
        if (wireMockServer != null && konfigurerNaisStsIssuer) {
            map["nav.auth.issuers.0.alias"] = "nais-sts"
            map["nav.auth.issuers.0.discovery_endpoint"] = wireMockServer.getNaisStsWellKnownUrl()
            map["nav.auth.nais-sts.authorized_clients"] = naisStsAuthoriedClients.joinToString(", ")
        }
        if (wireMockServer != null && konfigurerAzureIssuer) {
            if (pleiepengerJoarkAzureClientId == null) throw IllegalStateException("pleiepengerJoarkAzureClientId må settes når Azure skal konfigureres.")

            map["nav.auth.issuers.1.type"] = "azure"
            map["nav.auth.issuers.1.alias"] = "azure-v1"
            map["nav.auth.issuers.1.discovery_endpoint"] = wireMockServer.getAzureV1WellKnownUrl()
            map["nav.auth.issuers.1.audience"] = pleiepengerJoarkAzureClientId
            map["nav.auth.issuers.1.azure.require_certificate_client_authentication"] = "true"
            map["nav.auth.issuers.1.azure.authorized_clients"] = azureAuthorizedClients.joinToString(",")

            map["nav.auth.issuers.2.type"] = "azure"
            map["nav.auth.issuers.2.alias"] = "azure-v2"
            map["nav.auth.issuers.2.discovery_endpoint"] = wireMockServer.getAzureV2WellKnownUrl()
            map["nav.auth.issuers.2.audience"] = pleiepengerJoarkAzureClientId
            map["nav.auth.issuers.2.azure.require_certificate_client_authentication"] = "true"
            map["nav.auth.issuers.2.azure.authorized_clients"] = azureAuthorizedClients.joinToString(",")
        }

        return map.toMap()
    }
}