package no.nav.helse

import io.prometheus.client.Summary

interface HealthCheck {
    suspend fun check() : Health
}

abstract class Health(val message: String)
data class Healthy(private val healthyMessage : String) : Health(healthyMessage)
data class Unhealthy(private val unhealthyMessage: String) : Health(unhealthyMessage)

class HealthCheckObserver(
    name : String,
    private val help : String,
    private val windowInSeconds : Long = 10L
) {

    private val successSummary = Summary
        .build("${name}_success", "Antall ganger $help har gått bra siste $windowInSeconds sekundene.")
        .maxAgeSeconds(windowInSeconds)
        .create()

    private val failureSummary = Summary
        .build("${name}_failure", "Antall ganger $help har feilet siste $windowInSeconds sekundene.")
        .maxAgeSeconds(windowInSeconds)
        .create()

    fun success() = successSummary.observe(1.0)

    fun failure() = failureSummary.observe(1.0)

    inline fun <reified T> observe (operation : () -> T) : T {
        return try {
            val result = operation.invoke()
            success()
            result
        } catch (cause: Throwable) {
            failure()
            throw cause
        }
    }

    fun health() : Health {
        val success = successSummary.get().count.toInt()
        val failure = failureSummary.get().count.toInt()
        val total = success + failure
        val message = "Resultater av $help de siste $windowInSeconds sekundene : Gått bra = [$success/$total], Feilet = [$failure/$total]"
        return if (failure > success) Unhealthy(message) else Healthy(message)
    }
}