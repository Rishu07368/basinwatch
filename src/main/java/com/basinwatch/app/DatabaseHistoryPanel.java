package com.basinwatch.app;

import com.basinwatch.db.DatabaseHistoryEntry;
import com.basinwatch.db.DatabaseHistoryType;
import com.basinwatch.engine.BasinEngine;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicLong;

final class DatabaseHistoryPanel extends JPanel {
    private static final DateTimeFormatter DISPLAY_TIME =
            DateTimeFormatter.ofPattern("MMM d, HH:mm:ss").withZone(ZoneId.systemDefault());

    private final BasinEngine engine;
    private final JComboBox<DatabaseHistoryType> historyType =
            new JComboBox<>(DatabaseHistoryType.values());
    private final JLabel databaseStatus = new JLabel();
    private final JLabel resultStatus = new JLabel("Choose a history category and refresh.");
    private final HistoryTableModel tableModel = new HistoryTableModel();
    private final AtomicLong requestNumber = new AtomicLong();

    DatabaseHistoryPanel(BasinEngine engine) {
        this.engine = engine;
        setLayout(new BorderLayout(0, 10));
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JPanel heading = new JPanel(new BorderLayout(0, 5));
        heading.setOpaque(false);
        JLabel title = new JLabel("Database history");
        title.setFont(new Font("Segoe UI", Font.BOLD, 19));
        title.setForeground(new Color(26, 42, 61));
        JLabel description = new JLabel(
                "Recent readings and response records stored in the local SQLite database.");
        description.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        description.setForeground(new Color(100, 117, 137));
        heading.add(title, BorderLayout.NORTH);
        heading.add(description, BorderLayout.SOUTH);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        controls.setOpaque(false);
        historyType.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        JButton refresh = new JButton("Refresh history");
        refresh.setFont(new Font("Segoe UI", Font.BOLD, 12));
        refresh.addActionListener(event -> refreshHistory());
        controls.add(new JLabel("Records"));
        controls.add(historyType);
        controls.add(refresh);

        databaseStatus.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        databaseStatus.setForeground(new Color(51, 87, 106));
        resultStatus.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        resultStatus.setForeground(new Color(100, 117, 137));
        JPanel statusPanel = new JPanel(new BorderLayout(0, 4));
        statusPanel.setOpaque(false);
        statusPanel.add(databaseStatus, BorderLayout.NORTH);
        statusPanel.add(resultStatus, BorderLayout.SOUTH);

        JPanel top = new JPanel(new BorderLayout(0, 9));
        top.setOpaque(false);
        top.add(heading, BorderLayout.NORTH);
        JPanel controlsAndStatus = new JPanel(new BorderLayout(0, 4));
        controlsAndStatus.setOpaque(false);
        controlsAndStatus.add(controls, BorderLayout.NORTH);
        controlsAndStatus.add(statusPanel, BorderLayout.SOUTH);
        top.add(controlsAndStatus, BorderLayout.SOUTH);

        JTable table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        table.setRowHeight(25);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        table.getAccessibleContext().setAccessibleName("Persisted BasinWatch history");
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(220, 228, 237)));

        add(top, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        engine.addDatabaseStatusListener(message ->
                SwingUtilities.invokeLater(() -> databaseStatus.setText(message)));
        databaseStatus.setText(engine.databaseStatus());
        historyType.addActionListener(event -> refreshHistory());
        refreshHistory();
    }

    private void refreshHistory() {
        DatabaseHistoryType selected = (DatabaseHistoryType) historyType.getSelectedItem();
        if (selected == null) {
            return;
        }
        long request = requestNumber.incrementAndGet();
        resultStatus.setText("Loading recent records…");
        engine.loadDatabaseHistory(selected).whenComplete((entries, failure) ->
                SwingUtilities.invokeLater(() -> {
                    if (request != requestNumber.get()) {
                        return;
                    }
                    if (failure != null) {
                        tableModel.setEntries(List.of());
                        resultStatus.setText("History unavailable · "
                                + rootCause(failure).getMessage());
                    } else {
                        tableModel.setEntries(entries);
                        resultStatus.setText(entries.size() + " recent record(s) · "
                                + engine.queuedDatabaseOperations() + " database write(s) pending");
                    }
                }));
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof CompletionException
                || cause instanceof java.util.concurrent.ExecutionException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static final class HistoryTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Timestamp", "Type", "Zone", "Details"};
        private List<DatabaseHistoryEntry> entries = List.of();

        void setEntries(List<DatabaseHistoryEntry> entries) {
            this.entries = List.copyOf(entries);
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return entries.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            DatabaseHistoryEntry entry = entries.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> DISPLAY_TIME.format(entry.timestamp());
                case 1 -> entry.category();
                case 2 -> entry.zone() == null ? "—" : entry.zone();
                case 3 -> entry.details();
                default -> throw new IndexOutOfBoundsException("Unknown history column " + columnIndex);
            };
        }
    }
}
