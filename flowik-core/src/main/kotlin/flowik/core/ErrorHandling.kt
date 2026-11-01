package flowik.core

import org.slf4j.LoggerFactory
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.cancellation.CancellationException

/**
 * Logger for the reaction machinery. Everything a reaction fails to handle
 * itself is reported under the `flowik.core` category, so an application can
 * route or silence it with one line of logging configuration.
 */
private val log = LoggerFactory.getLogger("flowik.core")

/**
 * The handlers installed with [onReactionError]. Global rather than per thread:
 * an application installs them once, at startup, whichever thread its UI runs on.
 */
private val globalHandlers = CopyOnWriteArrayList<(Throwable, Any) -> Unit>()

/**
 * Installs a handler for every exception a reaction does not handle itself —
 * MobX's `onReactionError`.
 *
 * It receives what an [autoRun], [reaction], [whenThen], observation hook or
 * [lazyObservable] computation throws when that one was created without an
 * `onError` of its own, together with the source that threw it. That is the
 * place for an application-wide policy: show an error notification, report to
 * a crash tracker, fail a test.
 *
 * ```kotlin
 * onReactionError { error, source -> Notification.show("$source failed: ${error.message}") }
 * ```
 *
 * While at least one handler is installed, the handlers replace the default
 * logging, exactly as an `onError` lambda replaces it for one reaction; all of
 * them run, in installation order. A handler that throws is logged and does not
 * stop the others. Handlers run on the thread of the failing reaction.
 *
 * @return a [Disposable] that uninstalls [handler]
 */
fun onReactionError(handler: (error: Throwable, source: Any) -> Unit): Disposable {
    globalHandlers.add(handler)
    return object : Disposable {
        override fun dispose() {
            globalHandlers.remove(handler)
        }
    }
}

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
 * the logging; otherwise the handlers installed with [onReactionError] do, if
 * there are any. If a handler throws in turn, there is nobody left to delegate
 * to, so the failure is logged with the original exception attached as
 * suppressed.
 *
 * [CancellationException] is always re-thrown: swallowing it would turn
 * coroutine cancellation into a silent no-op.
 */
internal fun reportUncaught(source: Any, error: Throwable, onError: ((Throwable) -> Unit)?) {
    if (error is CancellationException) throw error

    if (onError != null) {
        runHandler(source, error) { onError(error) }
        return
    }

    if (globalHandlers.isEmpty()) {
        log.error("Uncaught exception in {}", source, error)
        return
    }

    for (handler in globalHandlers) {
        runHandler(source, error) { handler(error, source) }
    }
}

private inline fun runHandler(source: Any, error: Throwable, handler: () -> Unit) {
    try {
        handler()
    } catch (handlerError: Throwable) {
        if (handlerError is CancellationException) throw handlerError
        if (handlerError !== error) handlerError.addSuppressed(error)
        log.error("Error handler of {} failed", source, handlerError)
    }
}
