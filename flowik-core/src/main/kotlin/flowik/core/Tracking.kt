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
    /** Stack of currently-evaluating trackers (supports nesting). */
    private val stack = ThreadLocal.withInitial { ArrayDeque<Tracker>() }

    /** Action-batching depth counter. */
    private val batchDepth = ThreadLocal.withInitial { 0 }

    /** Pending reactions to notify when the outermost batch ends. */
    private val pendingReactions = ThreadLocal.withInitial { linkedSetOf<Reaction>() }

    /** Pending autoRuns to notify when the outermost batch ends. */
    private val pendingAutoRuns = ThreadLocal.withInitial { linkedSetOf<AutoRun>() }

    val current: Tracker? get() = stack.get().lastOrNull()

    fun push(tracker: Tracker) = stack.get().addLast(tracker)
    fun pop() = stack.get().removeLast()

    // ── Batching (action scope) ──────────────────────────────────────

    fun beginBatch() {
        batchDepth.set(batchDepth.get() + 1)
    }

    fun endBatch() {
        val depth = batchDepth.get() - 1
        batchDepth.set(depth)
        if (depth == 0) {
            flushPending()
        }
    }

    val isBatching: Boolean get() = batchDepth.get() > 0

    fun schedule(reaction: Reaction) {
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
    }
}

/**
 * Marker interface for anything that can track observable reads.
 */
interface Tracker {
    fun addDependency(observable: ObservableValue<*>)
}
