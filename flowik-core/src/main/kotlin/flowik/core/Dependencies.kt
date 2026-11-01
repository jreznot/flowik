package flowik.core

/**
 * Something a [Tracker] can depend on: an atom ([ObservableValue]) or a derived
 * value ([Computed]). Reading it inside a tracker registers that tracker as one
 * of its observers; the tracker removes itself again when it stops reading it
 * or is disposed.
 */
interface Dependency {
    fun addObserver(tracker: Tracker)
    fun removeObserver(tracker: Tracker)
}

/**
 * The dependencies of one [owner], kept in step with what each run reads.
 *
 * Every tracker — [AutoRun], [Reaction], [When], [Computed] — goes through
 * this, so all of them release a [Computed] they no longer read, and with it
 * the computed's own subscriptions once nothing else observes it. Without that,
 * a disposed reaction would stay in the observer list of every computed it
 * ever read, and keep whatever its closure captured alive.
 *
 * Atoms and derived values are released at different points of a run:
 *
 * - an atom is released *before* the run, so a tracker does not react to its
 *   own write of an atom it has not read again yet;
 * - a derived value is released *after* the run, and only if the run did not
 *   read it again. Releasing it up front would leave a computed read on every
 *   run without observers for a moment, so it would suspend and recompute from
 *   scratch each time.
 */
internal class DependencySet(private val owner: Tracker) {

    private var current: MutableSet<Dependency> = linkedSetOf()

    /** Runs [block] as [owner], replacing the previous run's dependencies with the ones it reads. */
    fun <R> track(block: () -> R): R {
        val previous = current
        previous.forEach { if (it is ObservableValue<*>) it.removeObserver(owner) }
        current = linkedSetOf()

        Tracking.push(owner)
        try {
            return block()
        } finally {
            Tracking.pop()
            // Compared with whatever is current *now*: a re-entrant run of the
            // same tracker may have replaced the set in the meantime.
            val latest = current
            previous.forEach { if (it !is ObservableValue<*> && it !in latest) it.removeObserver(owner) }
        }
    }

    fun add(dependency: Dependency) {
        if (current.add(dependency)) dependency.addObserver(owner)
    }

    /** Releases every dependency — what disposing the owner does. */
    fun clear() {
        val released = current
        current = linkedSetOf()
        released.forEach { it.removeObserver(owner) }
    }
}
