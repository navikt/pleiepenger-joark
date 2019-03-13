package no.nav.helse

import io.prometheus.client.Counter
import java.time.Duration
import java.time.LocalDateTime
import kotlin.concurrent.fixedRateTimer

interface HealthCheck {
    suspend fun check() : Health
}

abstract class Health(val message: String)
data class Healthy(private val healthyMessage : String) : Health(healthyMessage)
data class Unhealthy(private val unhealthyMessage: String) : Health(unhealthyMessage)

private val THIRTY_SECONDS_IN_MILLIS = Duration.ofSeconds(30).toMillis()

class HealthCheckObserver(
    name : String,
    private val help : String
) {
    private var countersLastCleared: LocalDateTime = LocalDateTime.now()

    private val successCounter = Counter
        .build("${name}_success", "Antall ganger $help har gått bra.")
        .create()

    private val failureCounter = Counter
        .build("${name}_failure", "Antall ganger $help har feilet.")
        .create()

    init {
        fixedRateTimer(name = "${name}_cleaner", initialDelay = THIRTY_SECONDS_IN_MILLIS, period = THIRTY_SECONDS_IN_MILLIS) {
            when (health()) {
                is Healthy -> {
                    successCounter.clear()
                    failureCounter.clear()
                    countersLastCleared = LocalDateTime.now()
                }
            }
        }
    }

    fun success() = successCounter.inc()

    fun failure() = failureCounter.inc()

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

    private fun secondsSinceCountersLastCleared() : Long {
        val seconds = Duration.between(countersLastCleared, LocalDateTime.now()).toSeconds()
        return if (seconds < 1) 1L else seconds
    }

    fun health() : Health {
        val success = successCounter.get().toInt()
        val failure = failureCounter.get().toInt()
        val total = success + failure
        val message = "Resultater av $help de siste ${secondsSinceCountersLastCleared()} sekundene : Gått bra = [$success/$total], Feilet = [$failure/$total]"
        return if (failure > success) Unhealthy(message) else Healthy(message)
    }
}