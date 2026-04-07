package reaktor.core

/** Create a new observable value. */
fun <T> observable(initial: T, name: String? = null): ObservableValue<T> =
    ObservableValue(initial, name)

/** Wrap an instance of [T], exposing each property as an [ObservableValue]. */
fun <T : Any> observableObject(initial: T): ObservableObject<T> =
    ObservableObject(initial)

/** Create an [ObservableObjectList] pre-populated with [items]. */
fun <T : Any> observableObjectList(vararg items: T): ObservableObjectList<T> =
    ObservableObjectList(items.toList())

/** Create a computed (derived) value with auto-tracking. */
fun <T> derived(compute: () -> T): Derived<T> =
    Derived(compute)

/**
 * Create and immediately run a reaction. Returns the [Reaction] so it can
 * be disposed later (e.g. when a component is removed from the hierarchy).
 */
fun reaction(name: String? = null, effect: () -> Unit): Reaction {
    val r = Reaction(name, effect)
    r.run()
    return r
}

/**
 * Batch multiple observable writes so that reactions fire only once,
 * after the block completes.
 */
inline fun <R> action(block: () -> R): R {
    Tracking.beginBatch()
    return try {
        block()
    } finally {
        Tracking.endBatch()
    }
}
