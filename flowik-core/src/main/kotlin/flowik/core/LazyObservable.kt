package flowik.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KProperty

// A lazy observable is started by a *read*, not by a write upstream — the one
// thing neither `computed` nor `flowAction` does:
//
//   • computed { }  re-derives from tracked atoms; it cannot fetch.
//   • flowAction { } is explicit; somebody has to invoke it.
//   • lazyObservable { } runs the first time anybody looks at it, and keeps
//     pushing values in for as long as it has something to say.
//
// The computation receives a sink and may call it any number of times, so a
// progressive load — first page, then the rest — is the normal case rather than
// a special one.

/** The callback a [lazyObservable] computation pushes values into. */
fun interface Sink<in T> {
    operator fun invoke(value: T)
}

/**
 * The callback a [lazyObservableAsync] computation pushes values into.
 *
 * Suspending, so a push made from a background dispatcher hops to the main
 * context and batches itself — there is no need to wrap it in [runInAction].
 */
fun interface SuspendSink<in T> {
    suspend operator fun invoke(value: T)
}

/**
 * A value that is computed on first read and updated by pushes afterwards —
 * the core equivalent of mobx-utils' `lazyObservable`.
 *
 * Reading [value] (or its alias [current]) starts the computation and returns
 * `initial` until the first push arrives; every later push notifies dependents,
 * so a reaction reading it simply re-runs. Because this is a
 * [ReadableObservable], all the one-way bindings work with it unchanged — and
 * displaying the value is what triggers the load:
 *
 * ```kotlin
 * class PlanetStore(scope: CoroutineScope) : Store {
 *     val planets = lazyObservableAsync(emptyList<Planet>(), scope) { sink ->
 *         val page1 = withContext(Dispatchers.IO) { api.page(1) }
 *         sink(page1)                                    // partial result, UI paints
 *         sink(page1 + withContext(Dispatchers.IO) { api.page(2) })
 *     }
 *     val summary by computed { "${planets.value.size} planet(s)" }
 * }
 *
 * Label(store.summary)                                   // this read starts the fetch
 * Panel(visible = store.planets.pending) { progressBar() }
 * Button("Reload") { store.planets.refresh() }
 * ```
 *
 * ### Laziness is read-driven
 *
 * Only a read starts the computation. [subscribe] deliberately does not, so
 * wiring up a listener never triggers work.
 *
 * ### Reading causes a write
 *
 * The computation may push synchronously, from inside the very read that
 * started it. Two things make that safe: the body runs [untracked], so
 * observables it reads do not become dependencies of whoever read this; and the
 * caller is registered as a dependent only *after* the body has run, so a
 * synchronous push cannot schedule the reaction that is currently running.
 *
 * ### Threading
 *
 * Like the rest of flowik-core, the handle is confined to the UI thread: push
 * from there, or use [lazyObservableAsync], which marshals pushes to
 * `mainContext` for you.
 */
sealed class LazyObservable<T> protected constructor(
    private val initial: T,
    private val name: String?,
    private val onError: ((Throwable) -> Unit)?,
) : ReadableObservable<T>, Disposable {

    /** The atom dependents observe. Holds `initial` until the first push. */
    private val atom = ObservableValue(initial, name)

    private val pendingAtom = ObservableValue(false, name?.let { "$it-pending" })

    /** `true` once a read has started the computation; cleared by [reset]. */
    private var started = false

    private var disposed = false

    /**
     * Stamps the current run. A push carrying an older stamp comes from a run
     * that [refresh], [reset] or [dispose] has already superseded, and is dropped
     * — cancellation is never instantaneous.
     */
    private var generation = 0L

    /**
     * `true` while a started computation has not produced a value yet. Bind it
     * to a spinner. Auto-tracks.
     *
     * The first push clears it; [lazyObservableAsync], where completion is
     * observable, also clears it when the body finishes or fails.
     */
    val pending: ReadableObservable<Boolean> get() = pendingAtom

    /**
     * The current value — `initial` until the computation pushes. Reading starts
     * the computation and auto-tracks.
     */
    override val value: T
        get() {
            // Start before touching the atom: at this point the caller is not yet
            // a dependent, so a synchronous push cannot schedule it against its
            // own read (the same ordering PolicyComputed.value relies on).
            startIfNeeded()
            return atom.value
        }

    /** Alias of [value] — mobx-utils spells it `current()`. */
    fun current(): T = value

    /**
     * Re-invokes the computation, cancelling a run that is still in flight.
     * Pushes from the cancelled run are ignored.
     *
     * Does nothing if nobody has read the value yet: there is nothing to refresh
     * until the first read has started it, and that read will run the current
     * computation anyway.
     */
    fun refresh() {
        if (started && !disposed) startRun()
    }

    /**
     * Cancels any run in flight and returns to the unstarted state: the value is
     * `initial` again and the next read recomputes.
     */
    fun reset() {
        cancelRun()
        started = false
        generation++
        action {
            atom.value = initial
            pendingAtom.value = false
        }
    }

    /**
     * Cancels any run in flight and makes this handle inert — later pushes are
     * dropped, reads no longer restart it, and the value stays at the last one
     * observed.
     */
    override fun dispose() {
        cancelRun()
        disposed = true
        pendingAtom.value = false
    }

    /** Notified on every push. Subscribing does *not* start the computation. */
    override fun subscribe(observer: Observer): Disposable = atom.subscribe(observer)

    override fun getValue(thisRef: Any?, property: KProperty<*>): T = value

    override fun get(): T = value

    override fun toString(): String = "LazyObservable(${name ?: "?"}=${atom.untrackedValue})"

    // Implementation hooks

    /** Runs the computation of generation [gen]. Called inside [untracked]. */
    protected abstract fun run(gen: Long)

    /** Cancels the run in flight, if the flavour has one to cancel. */
    protected open fun cancelRun() {}

    /**
     * Applies a push, ignoring anything from a superseded run or from a disposed
     * handle. One [action], so value and [pending] change together.
     */
    protected fun emit(gen: Long, value: T) {
        if (disposed || gen != generation) return
        action {
            atom.value = value
            pendingAtom.value = false
        }
    }

    /** Marks the run of generation [gen] finished, whether or not it pushed. */
    protected fun settle(gen: Long) {
        if (disposed || gen != generation) return
        pendingAtom.value = false
    }

    /** Reports a failure of the computation — [onError], or the log. */
    protected fun fail(error: Throwable) {
        reportUncaught(this, error, onError)
    }

    private fun startIfNeeded() {
        if (started || disposed) return
        started = true
        startRun()
    }

    private fun startRun() {
        cancelRun()
        val gen = ++generation
        pendingAtom.value = true
        // Untracked: the body's own reads belong to the body, not to whoever
        // happened to read this observable first.
        untracked { run(gen) }
    }
}

/**
 * The push-from-anywhere flavour: [compute] returns as soon as it has arranged
 * for values to arrive — typically by registering a callback — and [pending]
 * stays `true` until the first one does.
 */
private class DirectLazyObservable<T>(
    initial: T,
    name: String?,
    onError: ((Throwable) -> Unit)?,
    private val compute: (Sink<T>) -> Unit,
) : LazyObservable<T>(initial, name, onError) {

    override fun run(gen: Long) {
        try {
            compute(Sink { value -> emit(gen, value) })
        } catch (error: Throwable) {
            // The run is over, so it will never push: stop pending before reporting.
            settle(gen)
            fail(error)
        }
    }
}

/**
 * The coroutine flavour: one run at a time under [scope], cancelled and
 * restarted by `refresh`, exactly like [FlowAction].
 */
private class CoroutineLazyObservable<T>(
    initial: T,
    name: String?,
    onError: ((Throwable) -> Unit)?,
    private val scope: CoroutineScope,
    private val context: CoroutineContext,
    private val mainContext: CoroutineContext,
    private val compute: suspend (SuspendSink<T>) -> Unit,
) : LazyObservable<T>(initial, name, onError) {

    private var job: Job? = null

    override fun run(gen: Long) {
        job = scope.launch(context) {
            try {
                compute(SuspendSink { value -> withContext(mainContext) { emit(gen, value) } })
            } catch (e: CancellationException) {
                throw e  // always re-throw so coroutine machinery can clean up
            } catch (e: Throwable) {
                withContext(NonCancellable + mainContext) { fail(e) }
            } finally {
                // NonCancellable so the flag settles even when cancelled mid-suspend,
                // mainContext so the mutation happens on the UI thread.
                withContext(NonCancellable + mainContext) { settle(gen) }
            }
        }
    }

    override fun cancelRun() {
        job?.cancel()
        job = null
    }
}

/**
 * Create a [LazyObservable] whose [compute] pushes values through a plain
 * [Sink] — the right flavour for a callback or listener API:
 *
 * ```kotlin
 * val size = lazyObservable(0L) { sink -> watcher.onChange { sink(dir.size()) } }
 * ```
 *
 * [compute] runs on the thread that first reads the value, and so must the
 * pushes: writing observables off the UI thread is unsafe here as everywhere
 * else in flowik. Use [lazyObservableAsync] for work that leaves that thread.
 *
 * @param initial  The value reads return until the first push.
 * @param onError  Receives anything [compute] throws. Without it the failure is
 *                 logged and dropped, as in [autoRun] and [reaction].
 */
fun <T> lazyObservable(
    initial: T,
    name: String? = null,
    onError: ((Throwable) -> Unit)? = null,
    compute: (Sink<T>) -> Unit,
): LazyObservable<T> = DirectLazyObservable(initial, name, onError, compute)

/**
 * Create a [LazyObservable] backed by a coroutine: the first read launches
 * [compute] under [scope], and every `sink(…)` inside it lands on [mainContext]
 * as an [action].
 *
 * ```kotlin
 * val planets = lazyObservableAsync(emptyList<Planet>(), scope) { sink ->
 *     sink(withContext(Dispatchers.IO) { api.fetch() })
 * }
 * ```
 *
 * Only one run exists at a time: [LazyObservable.refresh] cancels the previous
 * one, and pushes that arrive from it afterwards are dropped.
 *
 * The scope is bound here rather than taken at the call site the way
 * [FlowAction] takes it, because a lazy observable has no call site — it starts
 * from a read, which may happen inside a binding, a `computed`, or a repaint.
 * It is deliberately required: a defaulted `MainScope()` would be a scope nobody
 * ever cancels.
 *
 * @param scope        Scope the computation is launched in.
 * @param context      Context the body starts on (default: [Dispatchers.Main]).
 * @param mainContext  Context the pushes are applied on (default: [Dispatchers.Main]).
 * @param onError      Receives anything [compute] throws, apart from
 *                     cancellation. Without it the failure is logged and dropped.
 */
fun <T> lazyObservableAsync(
    initial: T,
    scope: CoroutineScope,
    name: String? = null,
    context: CoroutineContext = Dispatchers.Main,
    mainContext: CoroutineContext = Dispatchers.Main,
    onError: ((Throwable) -> Unit)? = null,
    compute: suspend (SuspendSink<T>) -> Unit,
): LazyObservable<T> = CoroutineLazyObservable(initial, name, onError, scope, context, mainContext, compute)

/**
 * Expose a [Flow] as a [LazyObservable]: collection starts on the first read and
 * every emission becomes a push, so the value tracks the latest element.
 *
 * [LazyObservable.refresh] restarts the collection, [LazyObservable.dispose]
 * ends it.
 */
fun <T> Flow<T>.toLazyObservable(
    initial: T,
    scope: CoroutineScope,
    name: String? = null,
    context: CoroutineContext = Dispatchers.Main,
    mainContext: CoroutineContext = Dispatchers.Main,
    onError: ((Throwable) -> Unit)? = null,
): LazyObservable<T> =
    lazyObservableAsync(initial, scope, name, context, mainContext, onError) { sink ->
        collect { sink(it) }
    }
