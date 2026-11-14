package com.basinwatch.app;

import com.basinwatch.domain.ActivityEntry;
import com.basinwatch.domain.BasinSnapshot;
import com.basinwatch.domain.MissionSnapshot;
import com.basinwatch.domain.MissionType;
import com.basinwatch.domain.ResourceType;
import com.basinwatch.domain.RiskLevel;
import com.basinwatch.domain.ZoneSnapshot;
import com.basinwatch.engine.BasinEngine;
import com.basinwatch.engine.EngineDiagnostics;
import com.basinwatch.engine.ThreadDiagnostic;
import com.basinwatch.io.AppPaths;
import com.basinwatch.io.SaveCatalog;
import com.basinwatch.learning.ArchitectureCard;
import com.basinwatch.learning.LearningCatalog;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletionException;

public final class MainFrame extends JFrame {
    private static final Color INK = UiTheme.TEXT;
    private static final Color MUTED = UiTheme.MUTED;
    private static final Color BORDER = UiTheme.BORDER;
    private static final Color CANVAS = UiTheme.CANVAS;

    private final BasinEngine engine;
    private final AppPaths paths;
    private final BasinMapPanel mapPanel;
    private final JLabel statusLabel = new JLabel("Ready · observations paused");
    private final JLabel zoneTitle = new JLabel("Select a monitored zone");
    private final JLabel zoneSummary = new JLabel("Station observations and exposure will appear here.");
    private final JComboBox<String> zoneSelector =
            new JComboBox<>(new String[]{"UPR", "MIL", "NOR", "OLD", "MAR", "SOU"});
    private final JLabel riskSummary = new JLabel("6 ZONES  ·  SCHEMATIC MONITORING AREA");
    private final JLabel resourceSummary = new JLabel("Shared field inventory · allocated units return after mission completion");
    private final JComboBox<MissionType> missionType = new JComboBox<>(MissionType.values());
    private final DefaultListModel<MissionSnapshot> missionListModel = new DefaultListModel<>();
    private final DefaultListModel<String> activityListModel = new DefaultListModel<>();
    private final JList<MissionSnapshot> missionList = new JList<>(missionListModel);
    private final JList<String> activityList = new JList<>(activityListModel);
    private final JLabel eventSummary = new JLabel("Waiting for the first observation.");
    private final JLabel threadSummary = new JLabel("Background services are stopped.");
    private final JLabel queueSummary = new JLabel("Queue 0 / 96");
    private final JLabel archiveSummary = new JLabel("Sensor archive idle");
    private final JLabel[] metricValues = new JLabel[5];
    private JSplitPane workSplit;
    private JSplitPane boardSplit;
    private final JLabel[] stockLabels = new JLabel[ResourceType.values().length];
    private final JButton feedButton = new JButton("Start observations");
    private final JButton stepButton = new JButton("Advance one interval");
    private final JButton stormButton = new JButton("Inject storm cell");
    private final JButton dispatchButton = new JButton("Dispatch response");
    private final JCheckBox lowPriorityArchive = new JCheckBox("Run non-urgent archive at lower priority", true);
    private final JSlider intervalSlider = new JSlider(350, 5_000, 1_500);
    private final JSlider intensitySlider = new JSlider(2, 25, 10);
    private final Timer diagnosticTimer = new Timer(900, event -> refreshDiagnostics());
    private String selectedZoneId = "MIL";
    private boolean servicesStarted;
    private boolean closing;

    public MainFrame(BasinEngine engine, AppPaths paths) {
        super("BasinWatch | Floodplain Operations");
        this.engine = engine;
        this.paths = paths;
        mapPanel = new BasinMapPanel(this::selectZone);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(1100, 720));
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setSize(Math.min(1_440, screen.width - 70), Math.min(820, screen.height - 110));
        setLocationRelativeTo(null);
        getContentPane().setBackground(CANVAS);
        setLayout(new BorderLayout(0, 0));
        add(buildHeader(), BorderLayout.NORTH);
        add(buildWorkArea(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);
        engine.addSnapshotListener(snapshot ->
                SwingUtilities.invokeLater(() -> updateSnapshot(snapshot)));
        engine.addStatusListener(message ->
                SwingUtilities.invokeLater(() -> statusLabel.setText(message)));
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                closeApplication();
            }
        });
        SwingUtilities.invokeLater(() -> {
            if (isDisplayable()) {
                workSplit.setDividerLocation(Math.max(520, workSplit.getWidth() - 470));
                boardSplit.setDividerLocation(0.79);
            }
        });
        updateSnapshot(engine.snapshot());
        diagnosticTimer.start();
    }

    private JPanel buildHeader() {
        JPanel outer = new JPanel(new BorderLayout());
        outer.setBackground(UiTheme.SURFACE);
        outer.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));
        JPanel brand = new JPanel(new BorderLayout(12, 0));
        brand.setBackground(UiTheme.SURFACE);
        brand.setBorder(BorderFactory.createEmptyBorder(15, 23, 13, 18));
        JLabel mark = new JLabel("BW", SwingConstants.CENTER);
        mark.setOpaque(true);
        mark.setBackground(UiTheme.ACCENT_DARK);
        mark.setForeground(Color.WHITE);
        mark.setFont(new Font("Segoe UI", Font.BOLD, 17));
        mark.setPreferredSize(new Dimension(48, 46));
        JLabel title = new JLabel("<html><b>BASINWATCH</b><br><span style='color:#9cafc1;font-size:11px'>FLOODPLAIN OPERATIONS CONSOLE</span></html>");
        title.setForeground(INK);
        title.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        brand.add(mark, BorderLayout.WEST);
        brand.add(title, BorderLayout.CENTER);
        JLabel scope = new JLabel("OFFLINE PLANNING SIMULATION");
        scope.setFont(new Font("Segoe UI", Font.BOLD, 11));
        scope.setForeground(UiTheme.ACCENT);
        scope.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.BORDER),
                BorderFactory.createEmptyBorder(7, 10, 7, 10)));
        scope.setOpaque(true);
        scope.setBackground(UiTheme.PANEL);
        JPanel scopeWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 18, 20));
        scopeWrap.setBackground(UiTheme.SURFACE);
        scopeWrap.add(scope);
        brand.add(scopeWrap, BorderLayout.EAST);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 9));
        toolbar.setBackground(UiTheme.PANEL);
        toolbar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER));
        styleButton(feedButton, true);
        styleButton(stepButton, false);
        styleButton(stormButton, false);
        JButton save = new JButton("Save session");
        JButton restore = new JButton("Restore session");
        JButton report = new JButton("Export situation report");
        styleButton(save, false);
        styleButton(restore, false);
        styleButton(report, false);
        toolbar.add(feedButton);
        toolbar.add(stepButton);
        toolbar.add(stormButton);
        toolbar.add(separator());
        toolbar.add(save);
        toolbar.add(restore);
        toolbar.add(report);
        feedButton.setToolTipText("Start or resume live synthetic station observations.");
        stormButton.setToolTipText("Inject a clearly marked simulated high-rainfall observation into the selected zone.");
        stepButton.setEnabled(false);
        stormButton.setEnabled(false);
        feedButton.addActionListener(event -> toggleFeed());
        stepButton.addActionListener(event -> {
            if (!engine.step()) {
                showWarning("The event queue is full. Let pending observations finish, then step again.");
            }
        });
        stormButton.addActionListener(event -> {
            if (!engine.injectStorm(selectedZoneId)) {
                showWarning("The event queue is full. The storm-cell observation was not accepted.");
            } else {
                setStatus("Storm-cell observation queued for " + selectedZoneId);
            }
        });
        save.addActionListener(event -> chooseSave());
        restore.addActionListener(event -> chooseRestore());
        report.addActionListener(event -> exportReport());
        JPanel north = new JPanel(new BorderLayout());
        north.add(brand, BorderLayout.NORTH);
        north.add(toolbar, BorderLayout.SOUTH);
        outer.add(north, BorderLayout.CENTER);
        return outer;
    }

    private JPanel buildWorkArea() {
        JPanel mapSection = new JPanel(new BorderLayout(0, 8));
        mapSection.setOpaque(true);
        mapSection.setBackground(CANVAS);
        mapSection.setBorder(BorderFactory.createEmptyBorder(17, 20, 9, 10));
        JPanel mapHeading = new JPanel(new BorderLayout());
        mapHeading.setOpaque(false);
        JLabel title = heading("Basin conditions");
        mapHeading.add(title, BorderLayout.WEST);
        riskSummary.setForeground(MUTED);
        riskSummary.setFont(new Font("Segoe UI", Font.BOLD, 11));
        mapHeading.add(riskSummary, BorderLayout.EAST);
        mapSection.add(mapHeading, BorderLayout.NORTH);
        mapSection.add(mapPanel, BorderLayout.CENTER);
        JPanel caption = new JPanel(new BorderLayout());
        caption.setOpaque(false);
        JLabel note = new JLabel("Select a zone to review local conditions and dispatch a response.");
        note.setForeground(MUTED);
        note.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        caption.add(note, BorderLayout.WEST);
        mapSection.add(caption, BorderLayout.SOUTH);

        JTabbedPane operations = new JTabbedPane();
        operations.setUI(new BasicTabbedPaneUI());
        operations.setOpaque(true);
        operations.setBackground(UiTheme.SURFACE);
        operations.setForeground(MUTED);
        operations.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        operations.addTab("Response", buildResponsePanel());
        operations.addTab("Operations", buildMissionsPanel());
        operations.addTab("Insights", buildInspectorPanel());
        operations.addTab("Learn", buildLearningPanel());
        operations.addTab("Database history", new DatabaseHistoryPanel(engine));
        operations.setPreferredSize(new Dimension(425, 550));
        JPanel rightWrap = new JPanel(new BorderLayout());
        rightWrap.setOpaque(true);
        rightWrap.setBackground(CANVAS);
        rightWrap.setBorder(BorderFactory.createEmptyBorder(14, 7, 9, 17));
        rightWrap.setPreferredSize(new Dimension(440, 550));
        rightWrap.setMinimumSize(new Dimension(420, 450));
        rightWrap.add(operations);

        workSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, mapSection, rightWrap);
        workSplit.setBorder(null);
        workSplit.setOpaque(true);
        workSplit.setBackground(CANVAS);
        workSplit.setResizeWeight(0.62);
        workSplit.setDividerLocation(0.62);
        workSplit.setContinuousLayout(true);

        JPanel eventsPanel = new JPanel(new BorderLayout(12, 0));
        eventsPanel.setBackground(UiTheme.SURFACE);
        eventsPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER),
                BorderFactory.createEmptyBorder(10, 20, 10, 18)));
        JPanel eventHeading = new JPanel(new BorderLayout());
        eventHeading.setOpaque(false);
        JLabel eventTitle = heading("Operations timeline");
        eventSummary.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        eventSummary.setForeground(MUTED);
        eventHeading.add(eventTitle, BorderLayout.NORTH);
        eventHeading.add(eventSummary, BorderLayout.SOUTH);
        eventHeading.setPreferredSize(new Dimension(190, 54));
        activityList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        activityList.setFont(new Font("Consolas", Font.PLAIN, 12));
        activityList.setForeground(INK);
        activityList.setBackground(UiTheme.PANEL);
        activityList.setVisibleRowCount(4);
        JScrollPane activityScroll = new JScrollPane(activityList);
        activityScroll.setBorder(BorderFactory.createLineBorder(BORDER));
        eventsPanel.add(eventHeading, BorderLayout.WEST);
        eventsPanel.add(activityScroll, BorderLayout.CENTER);

        boardSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, workSplit, eventsPanel);
        boardSplit.setBorder(null);
        boardSplit.setOpaque(true);
        boardSplit.setBackground(CANVAS);
        boardSplit.setResizeWeight(0.79);
        boardSplit.setDividerLocation(0.79);
        boardSplit.setContinuousLayout(true);
        JPanel dashboard = new JPanel(new BorderLayout(0, 0));
        dashboard.setBackground(CANVAS);
        dashboard.add(buildMetrics(), BorderLayout.NORTH);
        dashboard.add(boardSplit, BorderLayout.CENTER);
        return dashboard;
    }

    private JPanel buildMetrics() {
        String[] labels = {
                "MONITORED ZONES", "CRITICAL ZONES", "ACTIVE MISSIONS",
                "PUMPS AVAILABLE", "POPULATION EXPOSED"
        };
        JPanel strip = new JPanel(new GridLayout(1, labels.length, 9, 0));
        strip.setBackground(CANVAS);
        strip.setBorder(BorderFactory.createEmptyBorder(11, 20, 0, 20));
        for (int index = 0; index < labels.length; index++) {
            JPanel card = new JPanel(new BorderLayout(0, 4));
            card.setBackground(UiTheme.SURFACE);
            card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER),
                    BorderFactory.createEmptyBorder(8, 11, 8, 11)));
            JLabel title = new JLabel(labels[index]);
            title.setFont(new Font("Segoe UI", Font.BOLD, 10));
            title.setForeground(MUTED);
            JLabel value = new JLabel("—");
            value.setFont(new Font("Segoe UI", Font.BOLD, 21));
            value.setForeground(index == 1 ? UiTheme.CRITICAL : INK);
            metricValues[index] = value;
            card.add(title, BorderLayout.NORTH);
            card.add(value, BorderLayout.CENTER);
            strip.add(card);
        }
        return strip;
    }

    private JPanel buildResponsePanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setBackground(UiTheme.SURFACE);
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        JPanel top = new JPanel(new BorderLayout(0, 5));
        top.setOpaque(false);
        JLabel eyebrow = new JLabel("SELECTED RESPONSE ZONE");
        eyebrow.setFont(new Font("Segoe UI", Font.BOLD, 10));
        eyebrow.setForeground(MUTED);
        JLabel zoneLabel = new JLabel("Zone");
        zoneLabel.setLabelFor(zoneSelector);
        zoneLabel.setForeground(MUTED);
        zoneLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        zoneSelector.setUI(new BasicComboBoxUI());
        zoneSelector.setOpaque(true);
        zoneSelector.setBackground(UiTheme.ELEVATED);
        zoneSelector.setForeground(INK);
        zoneSelector.setSelectedItem(selectedZoneId);
        zoneSelector.getAccessibleContext().setAccessibleName("Select a flood zone");
        zoneSelector.addActionListener(event -> {
            String chosen = (String) zoneSelector.getSelectedItem();
            if (chosen != null && !chosen.equals(selectedZoneId)) {
                selectZone(chosen);
            }
        });
        zoneTitle.setFont(new Font("Segoe UI", Font.BOLD, 22));
        zoneTitle.setForeground(INK);
        zoneSummary.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        zoneSummary.setForeground(MUTED);
        JPanel zoneHead = new JPanel(new BorderLayout(8, 0));
        zoneHead.setOpaque(false);
        zoneHead.add(zoneTitle, BorderLayout.CENTER);
        JPanel zoneChoose = new JPanel(new BorderLayout(5, 0));
        zoneChoose.setOpaque(false);
        zoneChoose.add(zoneLabel, BorderLayout.WEST);
        zoneChoose.add(zoneSelector, BorderLayout.CENTER);
        zoneHead.add(zoneChoose, BorderLayout.EAST);
        top.add(eyebrow, BorderLayout.NORTH);
        top.add(zoneHead, BorderLayout.CENTER);
        top.add(zoneSummary, BorderLayout.SOUTH);

        JPanel dispatch = titledPanel("Plan a field response");
        JLabel dispatchHint = new JLabel("<html>Units are reserved together and return to stock when the operation finishes.</html>");
        dispatchHint.setForeground(MUTED);
        dispatchHint.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        dispatchHint.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        missionType.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        missionType.setUI(new BasicComboBoxUI());
        missionType.setOpaque(true);
        missionType.setBackground(UiTheme.ELEVATED);
        missionType.setForeground(INK);
        dispatchButton.setUI(new BasicButtonUI());
        dispatchButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
        dispatchButton.setBackground(UiTheme.ACCENT_DARK);
        dispatchButton.setForeground(Color.WHITE);
        dispatchButton.setOpaque(true);
        dispatchButton.setBorderPainted(false);
        dispatchButton.setFocusPainted(false);
        dispatchButton.setMargin(new Insets(9, 14, 9, 14));
        dispatchButton.setEnabled(false);
        dispatchButton.addActionListener(event -> {
            MissionType type = (MissionType) missionType.getSelectedItem();
            if (type != null && engine.dispatch(selectedZoneId, type)) {
                setStatus(type.label() + " requested for " + selectedZoneId);
            } else {
                showWarning("The event queue is full. The dispatch request was not accepted.");
            }
        });
        JPanel dispatchControls = new JPanel(new BorderLayout(0, 8));
        dispatchControls.setOpaque(false);
        dispatchControls.add(missionType, BorderLayout.NORTH);
        dispatchControls.add(dispatchButton, BorderLayout.SOUTH);
        JPanel dispatchContent = new JPanel(new BorderLayout(0, 8));
        dispatchContent.setOpaque(false);
        dispatchContent.add(dispatchHint, BorderLayout.NORTH);
        dispatchContent.add(dispatchControls, BorderLayout.CENTER);
        dispatch.add(dispatchContent, BorderLayout.CENTER);

        JPanel inventory = new JPanel(new BorderLayout(0, 7));
        inventory.setOpaque(false);
        inventory.add(heading("Available field units"), BorderLayout.NORTH);
        JPanel stockList = new JPanel(new GridLayout(0, 1, 0, 4));
        stockList.setOpaque(false);
        for (int index = 0; index < ResourceType.values().length; index++) {
            stockLabels[index] = new JLabel(ResourceType.values()[index].label() + "   —");
            stockLabels[index].setFont(new Font("Segoe UI", Font.PLAIN, 13));
            stockLabels[index].setForeground(INK);
            stockList.add(stockLabels[index]);
        }
        inventory.add(stockList, BorderLayout.CENTER);
        JPanel inventoryWrap = new JPanel(new BorderLayout(0, 6));
        inventoryWrap.setOpaque(false);
        resourceSummary.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        resourceSummary.setForeground(MUTED);
        inventoryWrap.add(inventory, BorderLayout.CENTER);
        inventoryWrap.add(resourceSummary, BorderLayout.SOUTH);

        JPanel content = new JPanel(new BorderLayout(0, 13));
        content.setOpaque(false);
        content.add(top, BorderLayout.NORTH);
        JPanel middle = new JPanel(new BorderLayout(0, 13));
        middle.setOpaque(false);
        middle.add(dispatch, BorderLayout.NORTH);
        middle.add(inventoryWrap, BorderLayout.CENTER);
        content.add(middle, BorderLayout.CENTER);
        JLabel clarification = new JLabel("<html><b>Planning only.</b> Levels and exposure are scenario estimates, not live forecasts.</html>");
        clarification.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        clarification.setForeground(MUTED);
        clarification.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        content.add(clarification, BorderLayout.SOUTH);
        JScrollPane scroll = new JScrollPane(content,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildMissionsPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(UiTheme.SURFACE);
        panel.setBorder(BorderFactory.createEmptyBorder(14, 13, 12, 13));
        JLabel heading = heading("Field operations");
        JLabel subheading = new JLabel("Assigned work advances with observation intervals.");
        subheading.setForeground(MUTED);
        subheading.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(heading, BorderLayout.NORTH);
        top.add(subheading, BorderLayout.SOUTH);
        missionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        missionList.setCellRenderer(new MissionRenderer());
        missionList.setBackground(UiTheme.PANEL);
        missionList.setFixedCellHeight(62);
        JScrollPane scroll = new JScrollPane(missionList);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER));
        panel.add(top, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildInspectorPanel() {
        JPanel content = new JPanel();
        content.setLayout(new javax.swing.BoxLayout(content, javax.swing.BoxLayout.Y_AXIS));
        content.setBackground(UiTheme.SURFACE);
        content.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        content.add(heading("Background activity"));
        content.add(gap(6));
        threadSummary.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        threadSummary.setForeground(INK);
        content.add(threadSummary);
        content.add(gap(7));
        queueSummary.setFont(new Font("Segoe UI", Font.BOLD, 13));
        content.add(queueSummary);
        archiveSummary.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        archiveSummary.setForeground(MUTED);
        content.add(archiveSummary);
        content.add(gap(14));
        content.add(heading("Scenario controls"));
        content.add(gap(3));
        content.add(new JLabel("Observation interval (ms)"));
        intervalSlider.setMajorTickSpacing(1_000);
        intervalSlider.setPaintTicks(true);
        intervalSlider.setToolTipText("Slower rates create more time to inspect a changing scenario.");
        intervalSlider.addChangeListener(event -> {
            if (!intervalSlider.getValueIsAdjusting()) {
                engine.settings().setIntervalMillis(intervalSlider.getValue());
                refreshDiagnostics();
            }
        });
        content.add(intervalSlider);
        content.add(new JLabel("Storm intensity"));
        intensitySlider.setMajorTickSpacing(5);
        intensitySlider.setPaintTicks(true);
        intensitySlider.setToolTipText("Scale simulated rainfall and observed rise; it is not a forecast parameter.");
        intensitySlider.addChangeListener(event -> {
            if (!intensitySlider.getValueIsAdjusting()) {
                engine.settings().setStormIntensity(intensitySlider.getValue() / 10.0);
            }
        });
        content.add(intensitySlider);
        lowPriorityArchive.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lowPriorityArchive.setToolTipText("Thread priority is a best-effort hint and does not order response events.");
        lowPriorityArchive.addActionListener(event ->
                runFuture(engine.setArchivePriority(lowPriorityArchive.isSelected()),
                        "Updating the archive scheduling hint…",
                        ignored -> setStatus("Archive scheduling hint updated.")));
        content.add(lowPriorityArchive);
        JTextArea priorityNote = new JTextArea(
                "Priority only affects the non-urgent archive worker as a scheduler hint. Severity and mission order come from explicit application rules.");
        priorityNote.setLineWrap(true);
        priorityNote.setWrapStyleWord(true);
        priorityNote.setEditable(false);
        priorityNote.setOpaque(false);
        priorityNote.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        priorityNote.setForeground(MUTED);
        priorityNote.setBorder(BorderFactory.createEmptyBorder(2, 2, 8, 2));
        content.add(priorityNote);
        content.add(heading("Safe resource reservation"));
        content.add(gap(5));
        content.add(insight("ONE INVENTORY LOCK",
                "A dispatch validates and reserves all required units in one short critical section."));
        content.add(insight("NO LOCK HELD DURING I/O",
                "Logging, waiting, and UI callbacks happen after shared inventory state is released."));
        content.add(insight("NO INTENTIONAL DEADLOCK",
                "The allocator avoids nested resource locks; the normal scenario remains responsive."));
        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(UiTheme.SURFACE);
        panel.add(scroll);
        return panel;
    }

    private JPanel buildLearningPanel() {
        JPanel cards = new JPanel();
        cards.setLayout(new javax.swing.BoxLayout(cards, javax.swing.BoxLayout.Y_AXIS));
        cards.setBackground(UiTheme.SURFACE);
        cards.setBorder(BorderFactory.createEmptyBorder(10, 11, 10, 11));
        for (ArchitectureCard card : LearningCatalog.cards()) {
            JPanel item = new JPanel(new BorderLayout(0, 6));
            item.setBackground(UiTheme.PANEL);
            item.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER),
                    BorderFactory.createEmptyBorder(10, 11, 10, 11)));
            JLabel title = new JLabel(card.feature());
            title.setFont(new Font("Segoe UI", Font.BOLD, 14));
            title.setForeground(INK);
            JTextArea body = new JTextArea(
                    "What it does: " + card.purpose()
                            + "\nConcept: " + card.concept()
                            + "\nWhere: " + card.location()
                            + "\nWhy this choice: " + card.decision()
                            + "\nWithout it: " + card.consequence());
            body.setEditable(false);
            body.setLineWrap(true);
            body.setWrapStyleWord(true);
            body.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            body.setForeground(MUTED);
            body.setBackground(UiTheme.PANEL);
            body.setBorder(null);
            item.add(title, BorderLayout.NORTH);
            item.add(body, BorderLayout.CENTER);
            item.setAlignmentX(LEFT_ALIGNMENT);
            cards.add(item);
            cards.add(gap(8));
        }
        JScrollPane scroll = new JScrollPane(cards);
        scroll.setBorder(null);
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(UiTheme.SURFACE);
        panel.add(scroll);
        return panel;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(UiTheme.SURFACE);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER),
                BorderFactory.createEmptyBorder(8, 20, 8, 20)));
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusLabel.setForeground(UiTheme.ACCENT);
        JLabel storage = new JLabel("LOCAL DATA  ·  " + paths.root());
        storage.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        storage.setForeground(MUTED);
        footer.add(statusLabel, BorderLayout.WEST);
        footer.add(storage, BorderLayout.EAST);
        return footer;
    }

    private void toggleFeed() {
        feedButton.setEnabled(false);
        if (!servicesStarted || !"Pause observations".equals(feedButton.getText())) {
            runFuture(engine.start(), "Starting observation network…", error -> {
                servicesStarted = true;
                feedButton.setText("Pause observations");
                feedButton.setEnabled(true);
                stepButton.setEnabled(true);
                stormButton.setEnabled(true);
                dispatchButton.setEnabled(true);
            });
        } else {
            runFuture(engine.pause(), "Pausing incoming observations…", error -> {
                feedButton.setText("Resume observations");
                feedButton.setEnabled(true);
            });
        }
    }

    private void chooseSave() {
        JFileChooser chooser = new JFileChooser(paths.saves().toFile());
        chooser.setDialogTitle("Save current basin session");
        chooser.setFileFilter(new FileNameExtensionFilter("BasinWatch session (*.bws)", "bws"));
        chooser.setSelectedFile(new File(paths.saves().resolve("basin-session.bws").toString()));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path target = ensureExtension(chooser.getSelectedFile().toPath());
        runFuture(engine.save(target), "Saving a consistent session snapshot…",
                error -> setStatus("Session saved · " + target.getFileName()));
    }

    private void chooseRestore() {
        JFileChooser chooser = new JFileChooser(paths.saves().toFile());
        chooser.setDialogTitle("Restore a BasinWatch session");
        chooser.setFileFilter(new FileNameExtensionFilter("BasinWatch session (*.bws)", "bws"));
        try {
            List<Path> saves = SaveCatalog.list(paths.saves());
            if (!saves.isEmpty()) {
                chooser.setSelectedFile(saves.get(0).toFile());
            }
        } catch (java.io.IOException ex) {
            showError("Could not list saved sessions.", ex);
            return;
        }
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path selected = chooser.getSelectedFile().toPath();
        runFuture(engine.load(selected), "Checking and restoring the selected session…",
                error -> {
                    servicesStarted = engine.diagnostics().threads().stream()
                            .anyMatch(thread -> thread.name().startsWith("basin-response-"));
                    feedButton.setText(servicesStarted
                            ? "Resume observations" : "Start observations");
                    stepButton.setEnabled(servicesStarted);
                    stormButton.setEnabled(servicesStarted);
                    dispatchButton.setEnabled(servicesStarted);
                });
    }

    private void exportReport() {
        runFuture(engine.exportReport(), "Writing situation report…",
                path -> {
                    setStatus("Report exported · " + path);
                    JOptionPane.showMessageDialog(this, "Situation report saved to:\n" + path,
                            "Report exported", JOptionPane.INFORMATION_MESSAGE);
                });
    }

    private void runFuture(java.util.concurrent.CompletableFuture<?> future, String progress,
                           java.util.function.Consumer<Object> success) {
        setStatus(progress);
        future.whenComplete((value, failure) -> SwingUtilities.invokeLater(() -> {
            if (failure != null) {
                showError("The requested operation could not be completed.", unwrap(failure));
                refreshFeedControls();
            } else {
                success.accept(value);
            }
        }));
    }

    private void refreshFeedControls() {
        List<ThreadDiagnostic> threads = engine.diagnostics().threads();
        servicesStarted = threads.stream()
                .anyMatch(thread -> thread.name().startsWith("basin-response-"));
        boolean feedActive = threads.stream()
                .anyMatch(thread -> thread.name().equals("basin-sensor-feed") && thread.alive());
        feedButton.setText(feedActive ? "Pause observations"
                : servicesStarted ? "Resume observations" : "Start observations");
        feedButton.setEnabled(true);
        stepButton.setEnabled(servicesStarted);
        stormButton.setEnabled(servicesStarted);
        dispatchButton.setEnabled(servicesStarted);
    }

    private void updateSnapshot(BasinSnapshot snapshot) {
        mapPanel.update(snapshot, selectedZoneId);
        ZoneSnapshot zone = snapshot.zone(selectedZoneId);
        zoneTitle.setText(zone.name());
        zoneTitle.setForeground(riskColor(zone.risk()));
        zoneSummary.setText(String.format("%s  ·  %.2f m water  ·  %.1f mm latest rain",
                zone.risk().label().toUpperCase(), zone.waterLevelMeters(),
                zone.lastRainMillimeters()));
        long critical = snapshot.zones().stream().filter(value -> value.risk() == RiskLevel.CRITICAL).count();
        long warning = snapshot.zones().stream().filter(value -> value.risk() == RiskLevel.WARNING).count();
        long watch = snapshot.zones().stream().filter(value -> value.risk() == RiskLevel.WATCH).count();
        long active = snapshot.missions().stream()
                .filter(mission -> mission.status().name().equals("ACTIVE")).count();
        int exposedResidents = snapshot.zones().stream()
                .mapToInt(ZoneSnapshot::exposedResidents).sum();
        metricValues[0].setText(Integer.toString(snapshot.zones().size()));
        metricValues[1].setText(Long.toString(critical));
        metricValues[2].setText(Long.toString(active));
        metricValues[3].setText(Integer.toString(
                snapshot.availableResources().getOrDefault(ResourceType.PUMP, 0)));
        metricValues[4].setText(String.format("%,d", exposedResidents));
        riskSummary.setText(snapshot.zones().size() + " ZONES  ·  " + critical + " CRITICAL  ·  "
                + warning + " WARNING  ·  " + watch + " WATCH");
        for (ResourceType type : ResourceType.values()) {
            int index = type.ordinal();
            int available = snapshot.availableResources().getOrDefault(type, 0);
            int committed = snapshot.missions().stream()
                    .filter(mission -> mission.status().name().equals("ACTIVE"))
                    .mapToInt(mission -> mission.type().requirements().getOrDefault(type, 0))
                    .sum();
            stockLabels[index].setText(type.label() + "   " + available
                    + " available   ·   " + committed + " committed");
            stockLabels[index].setForeground(available == 0 ? UiTheme.CRITICAL : INK);
        }
        resourceSummary.setText("TICK " + snapshot.tick()
                + "  ·  Active operations " + snapshot.missions().stream()
                .filter(mission -> mission.status().name().equals("ACTIVE")).count());
        missionListModel.clear();
        snapshot.missions().stream().sorted(Comparator.comparingLong(MissionSnapshot::startedAtTick).reversed())
                .forEach(missionListModel::addElement);
        activityListModel.clear();
        List<ActivityEntry> entries = snapshot.activity();
        for (int index = entries.size() - 1; index >= Math.max(0, entries.size() - 80); index--) {
            activityListModel.addElement(entries.get(index).toString());
        }
        if (!activityListModel.isEmpty()) {
            activityList.ensureIndexIsVisible(0);
        }
        eventSummary.setText("Interval " + snapshot.tick() + "  ·  " + active + " active field operation(s)");
    }

    private void refreshDiagnostics() {
        EngineDiagnostics diagnostics = engine.diagnostics();
        StringBuilder states = new StringBuilder("<html>");
        if (diagnostics.threads().isEmpty()) {
            states.append("Background services are stopped.");
        } else {
            for (ThreadDiagnostic thread : diagnostics.threads()) {
                states.append("<b>").append(thread.name()).append("</b> · ")
                        .append(thread.state()).append(" · priority ")
                        .append(thread.priority()).append("<br>");
            }
        }
        states.append("</html>");
        threadSummary.setText(states.toString());
        queueSummary.setText("Event queue  " + diagnostics.queuedEvents() + " / "
                + diagnostics.queueCapacity() + "    ·    processed "
                + diagnostics.processedEvents());
        archiveSummary.setText("Archive backlog  " + diagnostics.pendingArchiveRecords()
                + "    ·    waiting readers " + diagnostics.waitingConsumers()
                + "    ·    waiting writers " + diagnostics.waitingProducers()
                + "    ·    processing " + diagnostics.activeEvents());
        if (lowPriorityArchive.isSelected() != engine.settings().lowPriorityArchive()) {
            lowPriorityArchive.setSelected(engine.settings().lowPriorityArchive());
        }
    }

    private void selectZone(String zoneId) {
        selectedZoneId = zoneId;
        if (!zoneId.equals(zoneSelector.getSelectedItem())) {
            zoneSelector.setSelectedItem(zoneId);
        }
        mapPanel.update(engine.snapshot(), selectedZoneId);
        updateSnapshot(engine.snapshot());
    }

    private void closeApplication() {
        if (closing) {
            return;
        }
        closing = true;
        setEnabled(false);
        setStatus("Stopping observation and response services; saving the last session…");
        engine.closeAsync().whenComplete((ignored, failure) -> SwingUtilities.invokeLater(() -> {
            if (failure != null) {
                closing = false;
                setEnabled(true);
                showError("BasinWatch could not finish a safe shutdown.", unwrap(failure));
                return;
            }
            diagnosticTimer.stop();
            dispose();
        }));
    }

    private static Path ensureExtension(Path path) {
        String filename = path.getFileName().toString();
        return filename.toLowerCase().endsWith(".bws")
                ? path : path.resolveSibling(filename + ".bws");
    }

    private void showWarning(String message) {
        statusLabel.setText("Attention · " + message);
        JOptionPane.showMessageDialog(this, message, "Operation not accepted",
                JOptionPane.WARNING_MESSAGE);
    }

    private void showError(String message, Throwable error) {
        String detail = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        statusLabel.setText("Attention · " + detail);
        JOptionPane.showMessageDialog(this, message + "\n\n" + detail,
                "BasinWatch error", JOptionPane.ERROR_MESSAGE);
    }

    private void setStatus(String message) {
        statusLabel.setText(message);
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable result = failure;
        while (result instanceof CompletionException && result.getCause() != null) {
            result = result.getCause();
        }
        return result;
    }

    private static JButton separator() {
        JButton spacer = new JButton("·");
        spacer.setEnabled(false);
        spacer.setBorderPainted(false);
        spacer.setContentAreaFilled(false);
        spacer.setForeground(MUTED);
        return spacer;
    }

    private static JPanel titledPanel(String title) {
        JPanel panel = new JPanel(new BorderLayout(0, 7));
        panel.setOpaque(false);
        JLabel label = heading(title);
        panel.add(label, BorderLayout.NORTH);
        return panel;
    }

    private static JLabel heading(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.BOLD, 16));
        label.setForeground(INK);
        return label;
    }

    private static JPanel insight(String title, String description) {
        JPanel panel = new JPanel(new BorderLayout(0, 3));
        panel.setBackground(UiTheme.PANEL);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(7, 8, 7, 8)));
        JLabel heading = new JLabel(title);
        heading.setFont(new Font("Segoe UI", Font.BOLD, 10));
        heading.setForeground(UiTheme.ACCENT);
        JLabel text = new JLabel("<html>" + description + "</html>");
        text.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        text.setForeground(MUTED);
        panel.add(heading, BorderLayout.NORTH);
        panel.add(text, BorderLayout.CENTER);
        panel.setAlignmentX(LEFT_ALIGNMENT);
        return panel;
    }

    private static JPanel gap(int height) {
        JPanel gap = new JPanel();
        gap.setOpaque(false);
        gap.setPreferredSize(new Dimension(1, height));
        gap.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        return gap;
    }

    private static void styleButton(JButton button, boolean primary) {
        button.setUI(new BasicButtonUI());
        button.setFont(new Font("Segoe UI", Font.BOLD, 12));
        button.setMargin(new Insets(8, 12, 8, 12));
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setFocusPainted(false);
        if (primary) {
            button.setBackground(UiTheme.ACCENT_DARK);
            button.setForeground(Color.WHITE);
            button.setOpaque(true);
            button.setBorderPainted(false);
        } else {
            button.setBackground(UiTheme.ELEVATED);
            button.setForeground(INK);
            button.setBorder(BorderFactory.createLineBorder(BORDER));
        }
    }

    private static Color riskColor(RiskLevel risk) {
        return switch (risk) {
            case NORMAL -> UiTheme.NORMAL;
            case WATCH -> UiTheme.WATCH;
            case WARNING -> UiTheme.WARNING;
            case CRITICAL -> UiTheme.CRITICAL;
        };
    }

    private static final class MissionRenderer extends DefaultListCellRenderer {
        @Override
        public java.awt.Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean selected, boolean focus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(
                    list, value, index, selected, focus);
            label.setBorder(BorderFactory.createEmptyBorder(5, 9, 5, 6));
            if (value instanceof MissionSnapshot mission) {
                String progress = mission.status().name().equals("ACTIVE")
                        ? mission.progressPercent() + "% complete"
                        : mission.status().label();
                label.setText("<html><b>" + mission.id() + " · "
                        + mission.type().label() + "</b><br>"
                        + mission.zoneId() + "  ·  " + progress + "</html>");
            }
            return label;
        }
    }
}
