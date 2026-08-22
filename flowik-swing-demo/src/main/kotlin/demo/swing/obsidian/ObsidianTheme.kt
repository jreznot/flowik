package demo.swing.obsidian

import java.awt.Color
import java.awt.Insets
import javax.swing.UIManager

/**
 * The Obsidian-ish light palette every component in this package paints with.
 * Kept in one place so re-skinning the app — a different accent, a dark
 * variant — stays a single-file edit.
 */
internal val ACCENT = Color(0x7C, 0x5C, 0xE6)
internal val BG_RAIL = Color(0xEF, 0xEF, 0xEF)
internal val BG_SIDEBAR = Color(0xF6, 0xF6, 0xF6)
internal val BG_EDITOR = Color(0xFF, 0xFF, 0xFF)
internal val LINE = Color(0xE0, 0xE0, 0xE0)
internal val ICON_FG = Color(0x75, 0x75, 0x75)
internal val ICON_FG_ACTIVE = Color(0x2E, 0x2E, 0x2E)
internal val HOVER_BG = Color(0xE4, 0xE4, 0xE4)
internal val ACTIVE_BG = Color(0xD9, 0xD7, 0xE8)
internal val TEXT_FG = Color(0x33, 0x33, 0x33)
internal val TEXT_MUTED = Color(0x9B, 0x9B, 0x9B)
internal val SELECTION_BG = Color(0xE6, 0xE2, 0xF8)

/**
 * FlatLaf defaults that pull the stock theme towards Obsidian: card-style tabs
 * with a purple underline, slim scrollbars, quiet selection colours.
 *
 * Call once, after the look and feel has been installed.
 */
fun applyObsidianTheme() {
    UIManager.put("TabbedPane.tabHeight", 36)
    // Almost no tab padding of its own: a tab's horizontal padding comes from
    // NoteTabHeader instead, so a collapsing tab can animate down to nothing.
    UIManager.put("TabbedPane.tabInsets", Insets(3, 2, 3, 2))
    UIManager.put("TabbedPane.minimumTabWidth", 0)
    UIManager.put("TabbedPane.background", BG_SIDEBAR)
    UIManager.put("TabbedPane.selectedBackground", BG_EDITOR)
    UIManager.put("TabbedPane.contentAreaColor", BG_EDITOR)
    UIManager.put("TabbedPane.underlineColor", ACCENT)
    UIManager.put("TabbedPane.disabledUnderlineColor", LINE)
    UIManager.put("TabbedPane.hoverColor", HOVER_BG)
    UIManager.put("TabbedPane.focusColor", BG_EDITOR)
    UIManager.put("TabbedPane.contentSeparatorHeight", 1)

    UIManager.put("List.selectionBackground", SELECTION_BG)
    UIManager.put("List.selectionForeground", TEXT_FG)
    UIManager.put("List.selectionInactiveBackground", SELECTION_BG)
    UIManager.put("List.selectionInactiveForeground", TEXT_FG)

    UIManager.put("ScrollBar.width", 10)
    UIManager.put("ScrollBar.thumbArc", 8)
    UIManager.put("ScrollBar.thumbInsets", Insets(2, 2, 2, 2))
    UIManager.put("ScrollBar.showButtons", false)

    UIManager.put("TextComponent.arc", 8)
    UIManager.put("Component.focusWidth", 1)
    UIManager.put("Component.focusColor", ACCENT)
    UIManager.put("ToolTip.background", Color(0x3A, 0x3A, 0x3A))
    UIManager.put("ToolTip.foreground", Color.WHITE)
}
