package no.nav.arbeidsgiver.notifikasjon.infrastruktur

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.util.*
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Timer
import java.util.concurrent.atomic.AtomicInteger


/**
 * inspired by [io.ktor.metrics.micrometer.MicrometerMetrics], but for clients.
 * this feature/plugin generates the following metrics:
 * (x = ktor.http.client, but can be overridden)
 *
 * x.requests.active: a gauge that counts the amount of concurrent HTTP requests. This metric doesn't provide any tags
 * x.requests: a timer for measuring the time of each request. This metric provides a set of tags for monitoring request data, including http method, path, status
 *
 */
class HttpClientMetricsFeature internal constructor(
    private val registry: MeterRegistry,
    private val clientName: String,
) {

    private val active = registry.gauge(activeRequestsGaugeName, AtomicInteger(0))

    /**
     * [HttpClientMetricsFeature] configuration that is used during installation
     */
    class Config {
        var clientName: String = "ktor.http.client"
        lateinit var registry: MeterRegistry
    }

    private fun before(context: HttpRequestBuilder) {
        active?.incrementAndGet()

        context.attributes.put(measureKey, ClientCallMeasure(Timer.start(registry), context.url.encodedPath))
    }

    private fun throwable(context: HttpRequestBuilder, t: Throwable) {
        context.attributes.getOrNull(measureKey)?.apply {
            throwable = t
        }
    }

    private fun throwable(context: HttpClientCall, t: Throwable) {
        context.attributes.getOrNull(measureKey)?.apply {
            throwable = t
        }
    }

    private fun after(context: HttpRequestBuilder) {
        val clientCallMeasure = context.attributes.getOrNull(measureKey)
        if (clientCallMeasure?.throwable != null) {
            // send av request feilet

            active?.decrementAndGet()
            val builder = Timer.builder(requestTimeTimerName).tags(
                listOf(
                    Tag.of("method", context.method.value),
                    Tag.of("url", context.urlTagValue()),
                    Tag.of("status", "n/a"),
                    Tag.of("throwable", clientCallMeasure.throwableTagValue())
                )
            )
            clientCallMeasure.timer.stop(builder.register(registry))
        }
    }



    private fun after(context: HttpClientCall) {
        active?.decrementAndGet()

        val clientCallMeasure = context.attributes.getOrNull(measureKey)
        if (clientCallMeasure != null) {
            val builder = Timer.builder(requestTimeTimerName).tags(
                listOf(
                    Tag.of("method", context.request.method.value),
                    Tag.of("url", context.urlTagValue()),
                    Tag.of("status", context.response.status.value.toString()),
                    Tag.of("throwable", clientCallMeasure.throwableTagValue())
                )
            )
            clientCallMeasure.timer.stop(builder.register(registry))
        }
    }

    /**
     * Companion object for feature installation
     */
    @Suppress("EXPERIMENTAL_API_USAGE_FUTURE_ERROR")
    companion object Feature : HttpClientFeature<Config, HttpClientMetricsFeature> {
        private var clientName: String = "ktor.http.client"

        val requestTimeTimerName: String
            get() = "$clientName.requests"
        val activeRequestsGaugeName: String
            get() = "$clientName.requests.active"

        private val measureKey = AttributeKey<ClientCallMeasure>("HttpClientMetricsFeature")
        override val key: AttributeKey<HttpClientMetricsFeature> = AttributeKey("HttpClientMetricsFeature")

        override fun prepare(block: Config.() -> Unit): HttpClientMetricsFeature {
            val config = Config().apply(block)
            // validate config?

            return HttpClientMetricsFeature(config.registry, config.clientName)
        }

        override fun install(feature: HttpClientMetricsFeature, scope: HttpClient) {
            clientName = feature.clientName

            scope.requestPipeline.intercept(HttpRequestPipeline.Phases.Send) {
                feature.before(context)
                try {
                    proceed()
                } catch (e: Throwable) {
                    feature.throwable(context, e)
                    throw e
                } finally {
                    feature.after(context)
                }
            }

            scope.responsePipeline.intercept(HttpResponsePipeline.Phases.Receive) {
                try {
                    proceed()
                } catch (e: Throwable) {
                    feature.throwable(context, e)
                    throw e
                } finally {
                    feature.after(context)
                }
            }
        }
    }

    private fun HttpClientCall.urlTagValue() =
        "${request.url.let { "${it.host}:${it.port}" }}${attributes[measureKey].path ?: request.url.encodedPath}"

    private fun HttpRequestBuilder.urlTagValue() =
        "${url.let { "${it.host}:${it.port}" }}${attributes[measureKey].path ?: url.encodedPath}"
}

private data class ClientCallMeasure(
    val timer: Timer.Sample,
    var path: String? = null,
    var throwable: Throwable? = null
) {
    fun throwableTagValue() : String = throwable?.let { it::class.qualifiedName } ?: "n/a"
}
