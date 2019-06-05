package no.nav.helse

import io.ktor.config.ApplicationConfig
import io.ktor.util.KtorExperimentalAPI
import no.nav.helse.dusseldorf.ktor.auth.*
import no.nav.helse.dusseldorf.ktor.core.getOptionalList
import no.nav.helse.dusseldorf.ktor.core.getRequiredString
import java.net.URI

private const val NAIS_STS_ALIAS = "nais-sts"

@KtorExperimentalAPI
data class Configuration(private val config : ApplicationConfig) {
    private val clients = config.clients()

    private fun getAuthorizedSystemsForRestApi(): List<String> {
        return config.getOptionalList(
            key = "nav.rest_api.authorized_systems",
            builder = { value -> value },
            secret = false
        )
    }

    fun getDokmotinngaaendeBaseUrl() = URI(config.getRequiredString("nav.dokmotinngaaende_base_url", secret = false))

    fun issuers(): Map<Issuer, Set<ClaimRule>> {
        return config.issuers().withAdditionalClaimRules(
            mapOf(NAIS_STS_ALIAS to setOf(StandardClaimRules.Companion.EnforceSubjectOneOf(getAuthorizedSystemsForRestApi().toSet())))
        )
    }

    fun naisStsClient() : ClientSecretClient  {
        val client = clients.getOrElse(NAIS_STS_ALIAS) {
            throw IllegalStateException("Client[$NAIS_STS_ALIAS] må være satt opp.")
        }
        return client as ClientSecretClient
    }
}