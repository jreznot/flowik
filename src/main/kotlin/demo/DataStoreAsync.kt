package demo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import reaktor.core.*
import kotlin.coroutines.cancellation.CancellationException

data class DemoItem(val id: Int, val name: String)

class DataStore {
    val isLoading = observable(false)
    val items = Observables<DemoItem>()
    val error = ObservableValue<String?>("")

    val load = flowAction {
        runInAction {
            isLoading.value = true;
            error.value = null
        }

        try {
            val result = withContext(Dispatchers.IO) {
                listOf<DemoItem>()
            }
            runInAction {
                items.setAll(result);
                isLoading.value = false
            }
        } catch (e: CancellationException) {
            throw e // always re-throw
        } catch (e: Exception) {
            runInAction {
                error.value = e.message;
                isLoading.value = false
            }
        }
    }
}