package flowik.core

import java.math.BigDecimal
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals

class ComparerOverloadsTest {

    @Test
    fun `observable with a comparer drops writes it reports as equal`() {
        val price = observable(BigDecimal("1.0"), Comparer { a, b -> a.compareTo(b) == 0 })
        val seen = mutableListOf<BigDecimal>()
        autoRun { seen += price.value }

        price.value = BigDecimal("1.00")
        price.value = BigDecimal("2")

        assertEquals(listOf(BigDecimal("1.0"), BigDecimal("2")), seen)
    }

    @Test
    fun `observable with a comparer works as a delegate`() {
        class Store {
            var name: String by observable("a", Comparer<String> { x, y -> x.equals(y, ignoreCase = true) })
        }
        val store = Store()
        var runs = 0
        autoRun { runs++; store.name }

        store.name = "A"
        store.name = "b"

        assertEquals(2, runs)
        assertEquals("b", store.name)
    }

    @Test
    fun `computed with a comparer notifies only on a change it recognises`() {
        val sensor = observable(1.0)
        val rounded = computed(Comparer<Double> { a, b -> abs(a - b) < 0.1 }) { sensor.value }
        val seen = mutableListOf<Double>()
        autoRun { seen += rounded.value }

        sensor.value = 1.05
        sensor.value = 2.0

        assertEquals(listOf(1.0, 2.0), seen)
        rounded.dispose()
    }
}
