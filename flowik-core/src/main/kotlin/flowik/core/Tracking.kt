package flowik.core

/**
 * Global dependency tracking context.
 *
 * When a [Reaction] or [Computed] is evaluating, it pushes itself onto
 * the tracker stack. Any [ObservableValue] that is read during that evaluation
 * registers the tracker as a dependent. This is the automatic dependency
 * tracking mechanism — the MobX-style "magic".
 */
object Tracking {
    /** Stack of currently evaluating trackers (supports nesting). */
    private val stack = ThreadLocal.withInitial { ArrayDeque<Tracker>() }

    /** Action-batching depth counter. */
    private val batchDepth = ThreadLocal.withInitial { 0 }

    /** Pending reactions to notify when the outermost batch ends. */
    private val pendingReactions = ThreadLocal.withInitial { linkedSetOf<Reaction<*>>() }

    /** Pending autoRuns to notify when the outermost batch ends. */
    private val pendingAutoRuns = ThreadLocal.withInitial { linkedSetOf<AutoRun>() }

    /** Pending [When] reactions to evaluate when the outermost batch ends. */
    private val pendingWhens = ThreadLocal.withInitial { linkedSetOf<When>() }

    /** Pending [PolicyComputed] refreshes, evaluated *before* the reactions of a batch. */
    private val pendingRefreshes = ThreadLocal.withInitial { linkedSetOf<PolicyComputed<*>>() }

    /** Guards against a non-converging derived graph in [flushRefreshes]. */
    private const val MAX_REFRESH_ROUNDS = 100

    val current: Tracker? get() = stack.get().lastOrNull()

    fun push(tracker: Tracker) = stack.get().addLast(tracker)
    fun pop() = stack.get().removeLast()

    /**
     * Runs [block] with dependency tracking suspended — observables read inside
     * register nothing, and the enclosing reaction (if any) is unaffected.
     */
    fun <R> untracked(block: () -> R): R {
        val saved = stack.get()
        stack.set(ArrayDeque())
        return try {
            block()
        } finally {
            stack.set(saved)
        }
    }

    // Batching (action scope)

    fun beginBatch() {
        batchDepth.set(batchDepth.get() + 1)
    }

    fun endBatch() {
        val depth = batchDepth.get() - 1
        try {
            if (depth == 0) {
                // Refresh derived values while the batch is still open, so the reactions
                // they schedule coalesce into the single flush below instead of running
                // once per refresh.
                flushRefreshes()
            }
        } finally {
            // Always leave the batch, even if a derivation threw — otherwise the
            // thread would stay in batching mode and swallow every later reaction.
            batchDepth.set(depth)
        }
        if (depth == 0) {
            flushPending()
        }
    }

    val isBatching: Boolean get() = batchDepth.get() > 0

    fun schedule(reaction: Reaction<*>) {
        if (isBatching) {
            pendingReactions.get().add(reaction)
        } else {
            reaction.run()
        }
    }

    fun schedule(autoRun: AutoRun) {
        if (isBatching) {
            pendingAutoRuns.get().add(autoRun)
        } else {
            autoRun.run()
        }
    }

    fun schedule(`when`: When) {
        if (isBatching) {
            pendingWhens.get().add(`when`)
        } else {
            `when`.run()
        }
    }

    internal fun schedule(computed: PolicyComputed<*>) {
        if (isBatching) {
            pendingRefreshes.get().add(computed)
        } else {
            computed.refresh()
        }
    }

    /**
     * Re-evaluates invalidated [PolicyComputed]s until none is left. A refresh can
     * invalidate another derived value downstream, hence the loop.
     */
    private fun flushRefreshes() {
        val pending = pendingRefreshes.get()
        var rounds = 0
        while (pending.isNotEmpty()) {
            check(++rounds <= MAX_REFRESH_ROUNDS) {
                "Derived values did not settle after $MAX_REFRESH_ROUNDS rounds — likely a cycle between computed values"
            }
            val snapshot = pending.toList()
            pending.clear()
            snapshot.forEach { it.refresh() }
        }
    }

    private fun flushPending() {
        val pending = pendingReactions.get()
        // Copy and clear to allow re-entrant scheduling
        val snapshot = pending.toList()
        pending.clear()
        snapshot.forEach { it.run() }

        val pendingAuto = pendingAutoRuns.get()
        val autoSnapshot = pendingAuto.toList()
        pendingAuto.clear()
        autoSnapshot.forEach { it.run() }

        val pendingW = pendingWhens.get()
        val whenSnapshot = pendingW.toList()
        pendingW.clear()
        whenSnapshot.forEach { it.run() }
    }
}

/**
 * Marker interface for anything that can track observable reads.
 */
interface Tracker {
    fun addDependency(observable: ObservableValue<*>)
}
