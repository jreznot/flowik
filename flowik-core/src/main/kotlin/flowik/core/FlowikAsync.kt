package flowik.core

import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

//
// In MobX, leaving an async function at an `await` point exits the current
// action scope.  Any observable mutations made after resumption must be wrapped
// in `runInAction` so they are batched and reactions fire exactly once.
//
// In Kotlin the identical problem occurs: after a `suspend` point, the original
// `action { }` batch is no longer active.  `runInAction` is a named alias for
// `action` that signals intent in async call sites.

/**
 * Switch to the Swing EDT ([Dispatchers.Main]) and batch observable mutations.
 *
 * Always call this after every `suspend` point when writing to observables,
 * because after a suspension you may be on a background thread (e.g., IO).
 */
suspend fun <R> runInAction(block: () -> R): R =
    withContext(Dispatchers.Main) { action(block) }

//
// Analogous to MobX's `flow` utility.  A FlowAction wraps a suspend block and:
//
//   • Runs on [context] (defaults to Dispatchers.Main — the Swing EDT when
//     kotlinx-coroutines-swing is on the classpath).
//   • Cancels any previous in-flight invocation automatically when called
//     again, matching MobX flow's "only one run at a time" guarantee.
//   • Exposes [isRunning] so the UI can bind a loading indicator.
//
// Typical usage inside a Store:
//
//   val loadItems = flowAction {
//       runInAction { isLoading.value = true; error.value = null }
//       try {
//           val items = withContext(Dispatchers.IO) { api.fetch() }
//           runInAction { data.setAll(items); isLoading.value = false }
//       } catch (e: CancellationException) { throw e }          // always re-throw
//       catch (e: Exception) {
//           runInAction { error.value = e.message; isLoading.value = false }
//       }
//   }
//
//   // UI:
//   Button("Reload") { loadItems(viewScope) }
//   Button("Cancel") { loadItems.cancel() }

/**
 * A cancellable async action.  Call [invoke] to start (or restart) the action;
 * any previously running invocation is canceled first.
 *
 * @param context  The [CoroutineContext] the block launches on.  Defaults to
 *                 [Dispatchers.Main], which on Swing dispatches to the EDT.
 * @param block    The suspend body.  Use [runInAction] inside to batch mutations.
 */
class FlowAction(
    private val context: CoroutineContext = Dispatchers.Main,
    private val block: suspend () -> Unit,
) {
    private val _isRunning = ObservableValue(false, name = "FlowAction.isRunning")

    /** Reactive flag — `true` while the action is executing. Bind to a spinner or progress bar. */
    val isRunning: ObservableValue<Boolean> get() = _isRunning

    private var currentJob: Job? = null

    /**
     * Cancel the previous run (if any) and start a new one under [scope].
     * Returns the new [Job] so callers can `join()` or await cancellation.
     */
    operator fun invoke(scope: CoroutineScope): Job {
        currentJob?.cancel()
        return scope.launch(context) {
            _isRunning.value = true
            try {
                block()
            } finally {
                // Use NonCancellable so this runs even when canceled mid-suspend
                // (e.g., while inside withContext(Dispatchers.IO)), and Dispatchers.Main
                // to guarantee the mutation happens on the EDT.
                withContext(NonCancellable + Dispatchers.Main) {
                    _isRunning.value = false
                }
            }
        }.also { currentJob = it }
    }

    /** Cancel the in-flight run without starting a new one. */
    fun cancel() {
        currentJob?.cancel()
        currentJob = null
    }
}

/**
 * Create a [FlowAction] — a cancellable async action with MobX-`flow` semantics.
 *
 * @param context  Coroutine context for the launched job (default: [Dispatchers.Main]).
 * @param block    Suspend body; wrap observable mutations in [runInAction].
 */
fun flowAction(
    context: CoroutineContext = Dispatchers.Main,
    block: suspend () -> Unit,
): FlowAction = FlowAction(context, block)
