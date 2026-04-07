package demo

import com.formdev.flatlaf.FlatLightLaf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.withContext
import flowik.core.*
import flowik.layout.uiFrame
import flowik.swing.*
import java.awt.Font
import javax.swing.SwingUtilities

data class Planet(val name: String, val distanceAu: Double, val moons: Int)

private val CATALOGUE = listOf(
    Planet("Mercury", 0.39,  0),
    Planet("Venus",   0.72,  0),
    Planet("Earth",   1.00,  1),
    Planet("Mars",    1.52,  2),
    Planet("Jupiter", 5.20, 95),
    Planet("Saturn",  9.58, 146),
    Planet("Uranus",  19.2, 28),
    Planet("Neptune", 30.1, 16),
)

class PlanetStore {
    val query    = observable("", name = "query")
    val results  = observables<Planet>()
    val progress = observable(0,  name = "progress")
    val errorMsg = observable("", name = "errorMsg")

    // Display list — unboxed mapValues; recomputes whenever results change.
    val displayItems: Computed<List<String>> = results.mapValues { p ->
        "%-10s  %5.2f AU   %d moon%s".format(p.name, p.distanceAu, p.moons, if (p.moons == 1) "" else "s")
    }

    val statusText: Computed<String> = computed {
        when {
            fetch.isRunning.value && progress.value == 0 ->
                "Connecting to solar catalogue…"
            fetch.isRunning.value ->
                "Fetching — ${progress.value}% complete"
            errorMsg.value.isNotEmpty() ->
                "Error: ${errorMsg.value}"
            results.size > 0 ->
                "${results.size} planet(s) found"
            else ->
                "Ready — enter a query and press Fetch (type \"error\" to simulate a failure)"
        }
    }

    /**
     * Cancellable async fetch.  Demonstrates the three-step async pattern:
     *
     *  1. [runInAction] before the first suspension — batch-reset all state on EDT.
     *  2. [withContext] to simulate blocking IO off the EDT.
     *  3. [runInAction] after each resumption — mutations are dispatched back to EDT.
     *
     * Pressing Fetch while a fetch is already in-flight automatically cancels
     * the previous operation before starting a new one ([FlowAction] semantics).
     */
    val fetch = flowAction {
        val q = query.value.trim().lowercase()

        // 1. Reset state before leaving the EDT
        runInAction {
            results.clear()
            progress.value = 0
            errorMsg.value = ""
        }

        try {
            // 2. Simulate DNS / connection latency on an IO thread
            withContext(Dispatchers.IO) { Thread.sleep(700) }

            if (q == "error") error("HTTP 503 — solar catalogue unavailable")

            val matching = CATALOGUE.filter { q.isEmpty() || it.name.lowercase().contains(q) }
            val mid      = (matching.size + 1) / 2
            val first    = matching.take(mid)
            val second   = matching.drop(mid)

            // 3. First page — IO delay, then runInAction pushes results back to EDT
            withContext(Dispatchers.IO) { Thread.sleep(600) }
            runInAction {
                first.forEach { results.add(it) }
                progress.value = 50
            }

            // 4. Second page
            withContext(Dispatchers.IO) { Thread.sleep(600) }
            runInAction {
                second.forEach { results.add(it) }
                progress.value = 100
            }

        } catch (e: CancellationException) {
            throw e  // always re-throw so coroutine machinery can clean up
        } catch (e: Exception) {
            runInAction { errorMsg.value = e.message ?: "Unknown error" }
        }
    }
}

fun dataStoreAsyncDemo() {
    SwingUtilities.invokeLater {
        FlatLightLaf.setup()

        val store    = PlanetStore()
        val appScope = MainScope()

        uiFrame("Planet Explorer — Async Demo", width = 580, height = 500) {
            north {
                vbox(gap = 2) {
                    Label("Planet Explorer").apply {
                        font = Font("SansSerif", Font.BOLD, 20)
                    }
                    Label("Demonstrates flowAction · runInAction · cancellation").apply {
                        font = Font("SansSerif", Font.ITALIC, 11)
                    }
                }
            }

            center {
                borderPanel(gap = 6) {
                    north {
                        vbox(gap = 4) {

                            // Search row ──────────────────────────────────────
                            hbox(gap = 6) {
                                Label("Query:")
                                TextField(store.query, columns = 18)
                                spacer(width = 4)

                                // Re-invoking while running cancels the previous fetch automatically.
                                Button("Fetch") {
                                    store.fetch(appScope)
                                }.apply {
                                    bindEnabled { !store.fetch.isRunning.value }
                                }

                                Button("Cancel") {
                                    store.fetch.cancel()
                                }.apply {
                                    bindEnabled { store.fetch.isRunning.value }
                                }
                            }

                            // Progress bar — hidden when idle ─────────────────
                            rpanel(visible = store.fetch.isRunning) {
                                progressBar(store.progress)
                            }

                            // Status line ─────────────────────────────────────
                            Label(store.statusText)

                            separator()
                        }
                    }

                    center {
                        ListBox(store.displayItems)
                    }
                }
            }

            south {
                Label("Leave blank to load all · \"m\" for rocky planets · \"error\" to simulate failure")
                    .apply { font = Font("SansSerif", Font.ITALIC, 11) }
            }
        }
    }
}

fun main() {
    dataStoreAsyncDemo()
}