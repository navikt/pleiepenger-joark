package no.nav.helse

import io.ktor.config.ApplicationConfig
import io.ktor.util.KtorExperimentalAPI
import no.nav.helse.dusseldorf.ktor.auth.*
import no.nav.helse.dusseldorf.ktor.core.getOptionalList
import no.nav.helse.dusseldorf.ktor.core.getRequiredList
import no.nav.helse.dusseldorf.ktor.core.getRequiredString
import java.net.URI

@KtorExperimentalAPI
internal data class Configuration(private val config : ApplicationConfig) {
    companion object {
        internal const val NAIS_STS_ALIAS = "nais-sts"
        internal const val AZURE_V2_ALIAS = "azure-v2"
    }

    private fun getNaisStsAuthorizedClients(): List<String> {
        return config.getOptionalList(
            key = "nav.auth.nais-sts.authorized_clients",
            builder = { value -> value },
            secret = false
        )
    }

    internal fun getDokmotinngaaendeBaseUrl() = URI(config.getRequiredString("nav.dokmotinngaaende_base_url", secret = false))

    internal fun issuers(): Map<Issuer, Set<ClaimRule>> {
        return config.issuers().withAdditionalClaimRules(
            mapOf(
                NAIS_STS_ALIAS to setOf(
                    StandardClaimRules.Companion.EnforceSubjectOneOf(
                        getNaisStsAuthorizedClients().toSet()
                    )
                )
            )
        )
    }

    internal fun clients() = config.clients()

    internal fun getOppretteJournalpostScopes() = config.getRequiredList("nav.auth.scopes.opprette-journalpost", secret = false, builder = { it }).toSet()
    internal fun getHenteDokumentScopes() = config.getRequiredList("nav.auth.scopes.hente-dokument", secret = false, builder = { it }).toSet()
}