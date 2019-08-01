package no.nav.helse

import com.github.tomakehurst.wiremock.WireMockServer
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
            map["nav.auth.clients.1.private_key_jwk"] = privateKeyJwk
            map["nav.auth.clients.1.certificate_hex_thumbprint"] = certificateHexThumbprint
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

    val certificateHexThumbprint = "3060471c65e5ec84c0563f9f1e00643c143e4f90"
    val privateKeyJwk = """
        {
            "kty": "RSA",
            "n": "vTs0mS-huLVOv7_EaaIHoqkM3Rz1TOImAEVdQK6PZqqQLnbRC5yszxuqOOFPvw8QFY3HT2iUrkxlVPkW3Z9LAXS3dmZKw0MJboLHvusdmLFn0FhIgbldRyAxJ4UcepLJdcR4xofW_MgIH34xkjEDY-dSeDB4fiKi1_8lPTJYuVP5vAywfV3Z_R7msK6rlvl0g28SsOZrxJ9OC6nH3cVsT75vZcmd2eip7LLGCkO8-V9qGgAYUjocn7x6-0XlPVilCF8ic6PNClwe4bmjDR2a_SbDSc3akE8vxaMtINt49CcPfUhkPPm_0mfWsayCXzuwBfUeTaXF_ABCxkipYYpu6w",
            "e": "AQAB",
            "d": "p-V0Eca1UtFrga6AcskUxToA897RttmgpfTlfJJlIc6MBu3dJNRqb4g4TCd9PiP7PWSCRu6fnNajwfUQWKsRPcV1UlQIWZ-NKsRWvgqWQ_iEB9OM4ay6GnVxp4LvdcHvhdJA5sV39uj0bBznlrJuM6H3BjTbc-7_VW5IeDfHiQZ2pWW4DSbiwBhIUYu13IspZcsyk-fLfU-asS_lpz-Mc7XdP56xmstc9D22rOhBCz2NWpamM2UaqRS1zdn1V0wULjfO-tRMGghef_LaAuEEeOhhd-rw_wS89MfPdAoHvJWEyBVgQAS9LqxwRfrcjIu5pEf51Q0VrlV7dij8K1-KQQ",
            "p": "3auUKk-tkDtyQFr8bW_aA74hkVIBuRbnWe5F-r3O66Vy40tWHhJnGCyncCWb5f_k146ZwxwNlooheJS6T3bi4hsI5bbj3ElE_jSR_7SBVEab4bHbGkYpER4AUCHxfZwD8PJEoLZ4f14U87cBYL1GNEyZnLUitSjCDDmfJfI5LBM",
            "q": "2omKIMQetGibKTS60lMAxcVn4kQiCX3_spnrBIxLuEgXGYrOAqbsRVdSc07wKAljE-ig2SN1EaLKHyWVBxdxd4KMZqxbmh6HNuYru412ilPwZM5csLasU9TAD1yYCtju1Bj2GMU6awnc3hKoTXeWZWBrK4eubX4nb2WZxvfwXMk",
            "dp": "Eh6NTOwYZtrFGweU7Kkg6_9lpQhMBcIehRZZ-AX93Ps4KeYlku20KaC0yxD37lP9c7U_Ulh_r9d4pu-ZTxeLsim9j3FkrMP8dL79VCaAD9B5u3gbTcmAX9rQ8bvkjnzrQY28GFrx_I9HLSi_XxX5oBrGz61qud4sBm3LWYG0NKs",
            "dq": "CxnLc2ii6qUZpJkyGDbxJhql8T9mvzawQ2FAJ-X8fqriyYBcgJP8EnWiEYtj9ZSsfLlnWkBL1Q6A194v2MFfGSP_f8Onj4eXdLlyZT-FUvd6kZRN7wgIbuWyr9UTQBHO5-UwswdptUA2AO3PsMevUwz3xKlKufMbi7QMgKfdhMk",
            "qi": "vTdHKVXjTPkhe5IIzv-YxhANMIsBZVT4OFF2a0eZr06anwM-tEJCkCJTjlkQmjBqKtjbYaubTXAnX6_uTRpfNVmhhpws_hRsN6fmGBdQXwe7wSlzudrctpv-02ABRnT0EGBi9r9LSRATzh22uGwoan2xfChuUKZhy4uZqxcicbI"
        }
        """.trimIndent()

}