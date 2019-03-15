package no.nav.helse

import io.ktor.application.ApplicationCall
import io.ktor.application.ApplicationCallPipeline
import io.ktor.application.ApplicationFeature
import io.ktor.request.httpMethod
import io.ktor.request.path
import io.ktor.util.AttributeKey
import io.ktor.util.pipeline.PipelineContext
import io.prometheus.client.Counter
import io.prometheus.client.Histogram
import java.lang.IllegalStateException

class MonitorReceivedHttpRequestsFeature (
    private val configure: Configuration
) {

    init {
        if (configure.app.isNullOrBlank()) {
            throw IllegalStateException("app må settes.")
        }
    }

    private val histogram = Histogram
        .build(
            "received_http_requests_histogram",
            "Histogram for alle HTTP-requester som treffer ${configure.app}")
        .labelNames("app", "verb", "path")
        .register()

    private val counter = Counter
        .build(
            "received_http_requests_counter",
            "Teller for alle HTTP-requester som treffer ${configure.app}")
        .labelNames("app", "verb", "path", "status")
        .register()

    class Configuration {
        var app : String? = null
        var skipPaths : List<String> = listOf("/isready", "/isalive", "/metrics", "/health")
    }

    private suspend fun intercept(context: PipelineContext<Unit, ApplicationCall>) {
        val verb = context.context.request.httpMethod.value
        val path = context.context.request.path()

        if (!configure.skipPaths.contains(path)) {
            try {
                histogram.labels(configure.app, verb, path).startTimer().use {
                    context.proceed()
                }
            } finally {
                counter.labels(configure.app, verb, path, context.context.response.status()?.value?.toString() ?: "200").inc()
            }
        } else {
            context.proceed()
        }
    }

    companion object Feature : ApplicationFeature<ApplicationCallPipeline, Configuration, MonitorReceivedHttpRequestsFeature> {

        override fun install(pipeline: ApplicationCallPipeline, configure: Configuration.() -> Unit): MonitorReceivedHttpRequestsFeature {
            val result = MonitorReceivedHttpRequestsFeature(
                Configuration().apply(configure)
            )

            pipeline.intercept(ApplicationCallPipeline.Call) {
                result.intercept(this)
            }
            return result
        }

        override val key = AttributeKey<MonitorReceivedHttpRequestsFeature>("MonitorReceivedHttpRequestsFeature")
    }
}