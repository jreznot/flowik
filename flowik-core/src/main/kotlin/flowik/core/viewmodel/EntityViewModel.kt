package flowik.core.viewmodel

import flowik.core.MutableObservable
import flowik.core.ObservableEntity

/**
 * A [ViewModel] over an [flowik.core.ObservableEntity] — what [createViewModel] returns for
 * a decomposed data class, and the usual way to edit one as a transaction.
 *
 * ```kotlin
 * data class Address(val city: String, val zip: String)
 *
 * val address = observable(Address("Munich", "80331"))
 * val form = createViewModel(address)
 *
 * form[Address::city] = "Berlin"
 * form.isDirty                       // true
 * address[Address::city]             // still "Munich"
 * form.submit()                      // now "Berlin", and the form is clean again
 * ```
 *
 * Properties are taken from the entity as scalar atoms, so the entity's rule of
 * one access pattern per property applies: a property that is already exposed
 * with `nested` / `nestedList` cannot also be buffered here, and vice versa. To
 * edit a nested object, make a view model of the nested entity —
 * `createViewModel(team.nested(Team::address))`.
 */
class EntityViewModel<T : Any> internal constructor(
    override val model: ObservableEntity<T>
) : ViewModel<T>() {

    override fun atom(name: String): MutableObservable<Any?> = model.get(name)
}
