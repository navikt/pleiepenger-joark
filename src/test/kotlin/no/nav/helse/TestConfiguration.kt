package no.nav.helse

import com.github.tomakehurst.wiremock.WireMockServer

object TestConfiguration {

    fun asMap(
        wireMockServer: WireMockServer? = null,
        port : Int = 8080,
        jwkSetUrl : String? = wireMockServer?.getJwksUrl(),
        tokenUrl : String? = wireMockServer?.getTokenUrl(),
        dokmotinngaaendeUrl : String? = wireMockServer?.getDokmotinngaaendeUrl(),
        issuer : String? = wireMockServer?.baseUrl(),
        authorizedSystems : String? = wireMockServer?.getSubject(),
        clientSecret: String? = "foo"
    ) : Map<String, String>{
        val map = mutableMapOf(
            Pair("ktor.deployment.port","$port"),
            Pair("nav.auth.issuers.0.alias","nais-sts"),
            Pair("nav.auth.issuers.0.issuer","$issuer"),
            Pair("nav.auth.issuers.0.jwks_uri","$jwkSetUrl"),
            Pair("nav.auth.clients.0.alias", "nais-sts"),
            Pair("nav.auth.clients.0.client_id", "srvpleiepenger-joark"),
            Pair("nav.auth.clients.0.client_secret", "srvpleiepenger-joark"),
            Pair("nav.auth.clients.0.token_endpoint", "$tokenUrl"),
            Pair("nav.rest_api.authorized_systems","$authorizedSystems"),
            Pair("nav.dokmotinngaaende_base_url", "$dokmotinngaaendeUrl")
        )

        if (clientSecret != null) {
            map["nav.authorization.service_account.client_secret"] = clientSecret
        }

        return map.toMap()
    }

    fun asArray(map : Map<String, String>) : Array<String>  {
        val list = mutableListOf<String>()
        map.forEach { configKey, configValue ->
            list.add("-P:$configKey=$configValue")
        }
        return list.toTypedArray()
    }
}