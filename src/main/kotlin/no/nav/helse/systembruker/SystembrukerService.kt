package no.nav.helse.systembruker

import no.nav.helse.Health
import no.nav.helse.HealthCheck
import no.nav.helse.Healthy
import no.nav.helse.Unhealthy
import java.time.LocalDateTime

class SystembrukerService (
    private val systembrukerGateway: SystembrukerGateway
) : HealthCheck {

    override suspend fun check(): Health {
        return try {
            getToken()
            Healthy("Henting av systembruker token OK.")
        } catch (cause: Throwable) {
            Unhealthy("Feil ved henting av systembruker token. ${cause.message}.")
        }
    }

    @Volatile private var cachedToken: String? = null
    @Volatile private var expiry: LocalDateTime? = null

    private suspend fun getToken() : String {
        if (hasCachedToken() && isCachedTokenValid()) {
            return cachedToken!!
        }

        clearCachedData()

        val response = systembrukerGateway.getToken()
        setCachedData(response)
        return cachedToken!!
    }

    suspend fun getAuthorizationHeader() : String {
        return "Bearer ${getToken()}"
    }

    private fun setCachedData(response: Response) {
        cachedToken = response.accessToken
        expiry = LocalDateTime.now()
            .plusSeconds(response.expiresIn)
            .minusSeconds(10L)
    }

    private fun clearCachedData() {
        cachedToken = null
        expiry = null
    }

    private fun hasCachedToken() : Boolean {
        return cachedToken != null && expiry != null
    }

    private fun isCachedTokenValid() : Boolean {
        return expiry!!.isAfter(LocalDateTime.now())
    }
}