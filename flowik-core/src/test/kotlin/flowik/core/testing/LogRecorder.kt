package flowik.core.testing

import org.slf4j.ILoggerFactory
import org.slf4j.IMarkerFactory
import org.slf4j.Logger
import org.slf4j.Marker
import org.slf4j.event.Level
import org.slf4j.helpers.BasicMDCAdapter
import org.slf4j.helpers.BasicMarkerFactory
import org.slf4j.helpers.LegacyAbstractLogger
import org.slf4j.helpers.MessageFormatter
import org.slf4j.spi.MDCAdapter
import org.slf4j.spi.SLF4JServiceProvider

/** A single captured log call. */
data class LogEvent(
    val logger: String,
    val level: Level,
    val message: String,
    val error: Throwable?
)

/**
 * Captures everything logged through SLF4J so tests can assert on it.
 *
 * flowik-core deliberately depends on the SLF4J *facade* only, so there is no
 * binding on the test classpath either — this recorder is registered as the
 * SLF4J provider (see `META-INF/services`) and takes its place.
 */
object LogRecorder {
    private val recorded = mutableListOf<LogEvent>()

    val events: List<LogEvent> get() = recorded.toList()

    fun clear() {
        recorded.clear()
    }

    internal fun record(event: LogEvent) {
        recorded += event
    }
}

private class RecordingLogger(name: String) : LegacyAbstractLogger() {
    init {
        this.name = name
    }

    override fun isTraceEnabled(): Boolean = true
    override fun isDebugEnabled(): Boolean = true
    override fun isInfoEnabled(): Boolean = true
    override fun isWarnEnabled(): Boolean = true
    override fun isErrorEnabled(): Boolean = true

    override fun getFullyQualifiedCallerName(): String? = null

    override fun handleNormalizedLoggingCall(
        level: Level,
        marker: Marker?,
        messagePattern: String?,
        arguments: Array<out Any>?,
        throwable: Throwable?
    ) {
        val message = MessageFormatter.basicArrayFormat(messagePattern, arguments)
        LogRecorder.record(LogEvent(name, level, message, throwable))
    }
}

/** Registered via `META-INF/services/org.slf4j.spi.SLF4JServiceProvider`. */
class RecordingServiceProvider : SLF4JServiceProvider {
    private val loggers = mutableMapOf<String, Logger>()
    private val loggerFactory = ILoggerFactory { name -> loggers.getOrPut(name) { RecordingLogger(name) } }
    private val markerFactory = BasicMarkerFactory()
    private val mdcAdapter = BasicMDCAdapter()

    override fun getLoggerFactory(): ILoggerFactory = loggerFactory
    override fun getMarkerFactory(): IMarkerFactory = markerFactory
    override fun getMDCAdapter(): MDCAdapter = mdcAdapter
    override fun getRequestedApiVersion(): String = "2.0.99"
    override fun initialize() = Unit
}
