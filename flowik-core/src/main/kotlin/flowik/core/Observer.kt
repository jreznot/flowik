package flowik.core

/**
 * A listener that is notified when an [Observable] changes.
 *
 * Implement this interface and pass it to [Observable.subscribe] to receive
 * change notifications.
 */
fun interface Observer {

    /** Called when the observed [Observable] has changed. */
    fun onChange()
}
