package no.nav.helse

import io.ktor.config.ApplicationConfig
import io.ktor.util.KtorExperimentalAPI
import no.nav.helse.dusseldorf.ktor.auth.*
import no.nav.helse.dusseldorf.ktor.core.getRequiredList
import no.nav.helse.dusseldorf.ktor.core.getRequiredString
import java.net.URI

@KtorExperimentalAPI
internal data class Configuration(private val config : ApplicationConfig) {
    companion object {
        internal const val NAIS_STS_ALIAS = "nais-sts"
        internal const val AZURE_V2_ALIAS = "azure-v2"
    }

    private val clients = config.clients()

    internal fun getDokmotinngaaendeBaseUrl() = URI(config.getRequiredString("nav.dokmotinngaaende_base_url", secret = false))

    internal fun issuers() = config.issuers().withoutAdditionalClaimRules()

    internal fun clients() = clients

    private fun azureClientConfigured() = clients().containsKey(AZURE_V2_ALIAS)

    internal fun getOppretteJournalpostScopes() = config.getRequiredList("nav.auth.scopes.opprette-journalpost", secret = false, builder = { it }).toSet()

    internal fun getHenteDokumentScopes() : Set<String> {
        return if (azureClientConfigured()) config.getRequiredList("nav.auth.scopes.hente-dokument", secret = false, builder = { it }).toSet()
        else setOf("openid")
    }
}