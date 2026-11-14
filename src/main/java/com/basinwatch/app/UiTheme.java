package com.basinwatch.app;

import javax.swing.UIManager;
import java.awt.Color;

final class UiTheme {
    static final Color CANVAS = new Color(12, 21, 34);
    static final Color SURFACE = new Color(20, 33, 49);
    static final Color PANEL = new Color(27, 43, 61);
    static final Color ELEVATED = new Color(34, 52, 72);
    static final Color BORDER = new Color(55, 75, 96);
    static final Color TEXT = new Color(232, 240, 247);
    static final Color MUTED = new Color(156, 175, 193);
    static final Color ACCENT = new Color(58, 181, 201);
    static final Color ACCENT_DARK = new Color(24, 94, 116);
    static final Color NORMAL = new Color(82, 196, 158);
    static final Color WATCH = new Color(235, 193, 84);
    static final Color WARNING = new Color(242, 139, 74);
    static final Color CRITICAL = new Color(239, 91, 97);

    private UiTheme() {
    }

    static void install() {
        UIManager.put("Panel.background", CANVAS);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("Button.background", ELEVATED);
        UIManager.put("Button.foreground", TEXT);
        UIManager.put("Button.disabledText", MUTED);
        UIManager.put("ComboBox.background", ELEVATED);
        UIManager.put("ComboBox.foreground", TEXT);
        UIManager.put("ComboBox.selectionBackground", ACCENT_DARK);
        UIManager.put("ComboBox.selectionForeground", TEXT);
        UIManager.put("CheckBox.background", CANVAS);
        UIManager.put("CheckBox.foreground", TEXT);
        UIManager.put("List.background", PANEL);
        UIManager.put("List.foreground", TEXT);
        UIManager.put("List.selectionBackground", ACCENT_DARK);
        UIManager.put("List.selectionForeground", TEXT);
        UIManager.put("TabbedPane.background", SURFACE);
        UIManager.put("TabbedPane.foreground", MUTED);
        UIManager.put("TabbedPane.selected", PANEL);
        UIManager.put("TabbedPane.contentAreaColor", SURFACE);
        UIManager.put("Table.background", PANEL);
        UIManager.put("Table.foreground", TEXT);
        UIManager.put("Table.gridColor", BORDER);
        UIManager.put("Table.selectionBackground", ACCENT_DARK);
        UIManager.put("Table.selectionForeground", TEXT);
        UIManager.put("TableHeader.background", ELEVATED);
        UIManager.put("TableHeader.foreground", TEXT);
        UIManager.put("ScrollPane.background", SURFACE);
        UIManager.put("Viewport.background", SURFACE);
        UIManager.put("Slider.background", CANVAS);
        UIManager.put("TextArea.background", PANEL);
        UIManager.put("TextArea.foreground", TEXT);
        UIManager.put("OptionPane.background", SURFACE);
        UIManager.put("OptionPane.messageForeground", TEXT);
    }

}
