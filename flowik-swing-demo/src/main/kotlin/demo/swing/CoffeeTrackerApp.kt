package demo.swing

import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.FlatDarkLaf
import flowik.core.*
import flowik.layout.PanelScope
import flowik.layout.uiFrame
import flowik.swing.*
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.coreui.CoreUiFree
import org.kordamp.ikonli.swing.FontIcon
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridLayout
import java.awt.RenderingHints
import javax.swing.*

/**
 * A coffee tracker: seven days of cups, a daily limit, and a verdict on today.
 *
 * Run with `./gradlew :flowik-swing-demo:runCoffeeTracker`.
 */
fun main() {
    coffeeTrackerDemo()
}

data class DayCups(val day: String, val cups: Int)

/** Pixels of bar per cup. */
private const val CUP_HEIGHT = 28
private const val BAR_WIDTH = 40
private const val BAR_ARC = 16

private val COFFEE = Color(0xA0, 0x69, 0x3A)
private val TOO_MUCH = Color(0xE5, 0x48, 0x4D)
private val ON_TRACK = Color(0x4C, 0xB0, 0x5A)
private val CAPTION_FG: Color get() = UIManager.getColor("Label.foreground") ?: Color.LIGHT_GRAY

private fun glyph(ikon: Ikon, size: Int, color: Color): FontIcon = FontIcon.of(ikon, size, color)

class CoffeeStore : Store {
    /** The week, Monday first; the last day is today. Each day's cups is its own atom. */
    val week = observables(
        DayCups("Mon", 2), DayCups("Tue", 3), DayCups("Wed", 1), DayCups("Thu", 4),
        DayCups("Fri", 2), DayCups("Sat", 3), DayCups("Sun", 1)
    )

    var limit by observable(3, name = "limit")

    private val todayEntry get() = week[week.size - 1]

    val total by computed { week.sumOf { it[DayCups::cups] } }
    val today by computed { todayEntry[DayCups::cups] }
    val overLimit by computed { today > limit }

    val summary by computed { "$total cups this week · $today today" }
    val limitText by computed { "Daily limit: $limit cups" }
    val verdict by computed { if (overLimit) "Easy there, champ." else "You're on track." }

    fun oneMore() = action { todayEntry[DayCups::cups] = todayEntry[DayCups::cups] + 1 }

    fun resetToday() = action { todayEntry[DayCups::cups] = 0 }
}

/**
 * One column of the chart: the count, a rounded bar `cups * CUP_HEIGHT` tall,
 * and the day underneath, painted bottom-up so every column shares a baseline.
 *
 * The column's reaction tracks only its own day's cups and the limit, so
 * pouring a cup today repaints the Sunday column and nothing else.
 */
context(bindings: Bindings)
private fun CupBar(day: ObservableEntity<DayCups>, limit: () -> Int): JComponent {
    val column = object : JComponent() {
        var cups = 0
        var over = false

        override fun getPreferredSize(): Dimension {
            val fm = getFontMetrics(font)
            return Dimension(BAR_WIDTH + 16, cups * CUP_HEIGHT + fm.height * 2 + 16)
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                g2.font = font
                val fm = g2.fontMetrics

                // Day caption on the baseline.
                val caption = day[DayCups::day]
                g2.color = CAPTION_FG
                val captionY = height - fm.descent
                g2.drawString(caption, (width - fm.stringWidth(caption)) / 2, captionY)

                // The bar, sitting on top of the caption.
                val barBottom = captionY - fm.ascent - 6
                val barHeight = cups * CUP_HEIGHT
                val barX = (width - BAR_WIDTH) / 2
                if (barHeight > 0) {
                    g2.color = if (over) TOO_MUCH else COFFEE
                    g2.fillRoundRect(barX, barBottom - barHeight, BAR_WIDTH, barHeight, BAR_ARC, BAR_ARC)
                }

                // The count, above the bar.
                val count = cups.toString()
                g2.color = CAPTION_FG
                g2.drawString(count, (width - fm.stringWidth(count)) / 2, barBottom - barHeight - 8)
            } finally {
                g2.dispose()
            }
        }
    }
    column.font = UIManager.getFont("Label.font")

    bindings.autoRun("cupBar.${day[DayCups::day]}") {
        column.cups = day[DayCups::cups]
        column.over = column.cups > limit()
        column.revalidate()
        column.repaint()
    }
    return column
}

/** A rounded, slightly raised panel, styled through FlatLaf. */
private fun PanelScope.Card(init: PanelScope.() -> Unit): JPanel {
    // Capped at its preferred height, so a growing bar grows the card instead
    // of the card soaking up the window's spare space.
    val card = object : JPanel() {
        override fun getMaximumSize() = Dimension(Int.MAX_VALUE, preferredSize.height)
    }
    card.layout = BoxLayout(card, BoxLayout.Y_AXIS)
    card.border = BorderFactory.createEmptyBorder(16, 16, 16, 16)
    card.putClientProperty(FlatClientProperties.STYLE, "arc: 16; background: lighten(@background,5%)")
    PanelScope(card, this).init()
    add(card)
    return card
}

/** BoxLayout lines children up by alignmentX; left-align everything in a column. */
private fun JPanel.alignLeft(): JPanel = apply {
    components.forEach { (it as? JComponent)?.alignmentX = JComponent.LEFT_ALIGNMENT }
}

fun coffeeTrackerDemo() {
    SwingUtilities.invokeLater {
        FlatDarkLaf.setup()

        val store = CoffeeStore()
        val bindings = Bindings()

        uiFrame("Coffee Tracker", width = 640, height = 600, bindings = bindings) {
            center {
                vbox(gap = 20) {
                    Label("Coffee Tracker").apply {
                        font = font.deriveFont(Font.BOLD, 30f)
                        icon = glyph(CoreUiFree.COFFEE, 32, COFFEE)
                        iconTextGap = 12
                    }
                    spacer(height = 12)

                    Label(store::summary).apply { font = font.deriveFont(15f) }
                    spacer(height = 16)

                    Card {
                        ForEach(store.week, GridLayout(1, 0, 8, 0)) { day ->
                            CupBar(day, store::limit)
                        }.isOpaque = false
                    }
                    spacer(height = 20)

                    Label(store::limitText).apply { font = font.deriveFont(15f) }
                    spacer(height = 8)
                    Slider(store::limit.asObservable(), min = 1, max = 8).apply {
                        snapToTicks = true
                        minorTickSpacing = 1
                    }
                    spacer(height = 16)

                    Label(store::verdict).apply {
                        font = font.deriveFont(15f)
                        iconTextGap = 8
                        icon {
                            if (store.overLimit) glyph(CoreUiFree.WARNING, 16, TOO_MUCH)
                            else glyph(CoreUiFree.CHECK_CIRCLE, 16, ON_TRACK)
                        }
                    }
                    spacer(height = 12)

                    hbox(gap = 0) {
                        Button("One more") { store.oneMore() }.apply {
                            icon = glyph(CoreUiFree.COFFEE, 15, COFFEE)
                            iconTextGap = 6
                        }
                        spacer(width = 8)
                        Button("Reset today") { store.resetToday() }
                        hglue()
                    }
                    vglue()
                }.alignLeft()
            }
        }.disposeOnClose(bindings)
    }
}
