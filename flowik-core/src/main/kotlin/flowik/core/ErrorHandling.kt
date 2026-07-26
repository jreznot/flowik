package flowik.core

import org.slf4j.LoggerFactory
import kotlin.coroutines.cancellation.CancellationException

/**
 * Logger for the reaction machinery. Everything a reaction fails to handle
 * itself is reported under the `flowik.core` category, so an application can
 * route or silence it with one line of logging configuration.
 */
private val log = LoggerFactory.getLogger("flowik.core")

/**
 * Reports an exception that escaped the body of [source] — an [AutoRun],
 * [Reaction] or [When].
 *
 * Mirrors MobX: by default the exception is logged and *not* re-thrown, so a
 * failing reaction never breaks the write that triggered it, nor the other
 * reactions scheduled alongside it. Tracking is left intact, so the reaction
 * runs again — and may well succeed — on the next dependency change.
 *
 * When the reaction was created with an `onError` lambda, that lambda replaces
 * the logging. If it throws in turn, there is nobody left to delegate to, so the
 * failure is logged with the original exception attached as suppressed.
 *
 * [CancellationException] is always re-thrown: swallowing it would turn
 * coroutine cancellation into a silent no-op.
 */
internal fun reportUncaught(source: Any, error: Throwable, onError: ((Throwable) -> Unit)?) {
    if (error is CancellationException) throw error

    if (onError == null) {
        log.error("Uncaught exception in {}", source, error)
        return
    }

    try {
        onError(error)
    } catch (handlerError: Throwable) {
        if (handlerError is CancellationException) throw handlerError
        if (handlerError !== error) handlerError.addSuppressed(error)
        log.error("Error handler of {} failed", source, handlerError)
    }
}
