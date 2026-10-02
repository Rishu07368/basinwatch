package com.basinwatch.app;

import com.basinwatch.domain.BasinSnapshot;
import com.basinwatch.domain.RiskLevel;
import com.basinwatch.domain.ZoneSnapshot;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

final class BasinMapPanel extends JPanel {
    private static final Color BACKGROUND = new Color(14, 27, 44);
    private static final Color WATER = new Color(46, 130, 172);
    private BasinSnapshot snapshot;
    private String selectedZone = "MIL";
    private final Consumer<String> selectionHandler;
    private final Map<String, Rectangle> hitTargets = new HashMap<>();

    BasinMapPanel(Consumer<String> selectionHandler) {
        this.selectionHandler = selectionHandler;
        setOpaque(true);
        setBackground(BACKGROUND);
        setPreferredSize(new Dimension(690, 465));
        setMinimumSize(new Dimension(490, 360));
        setToolTipText("Select a basin zone to inspect its conditions.");
        getAccessibleContext().setAccessibleName(
                "Schematic flood-zone map; use the Zone selector for keyboard navigation.");
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                hitTargets.forEach((zoneId, bounds) -> {
                    if (bounds.contains(event.getPoint())) {
                        BasinMapPanel.this.selectionHandler.accept(zoneId);
                    }
                });
            }
        });
    }

    void update(BasinSnapshot snapshot, String selectedZone) {
        this.snapshot = snapshot;
        this.selectedZone = selectedZone;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            paintRiver(g);
            hitTargets.clear();
            if (snapshot == null) {
                return;
            }
            int margin = 24;
            int gapX = 14;
            int gapY = 14;
            int tileWidth = (getWidth() - margin * 2 - gapX * 2) / 3;
            int tileHeight = (getHeight() - margin * 2 - gapY) / 2;
            for (ZoneSnapshot zone : snapshot.zones()) {
                int x = margin + zone.column() * (tileWidth + gapX);
                int y = margin + zone.row() * (tileHeight + gapY);
                Rectangle bounds = new Rectangle(x, y, tileWidth, tileHeight);
                hitTargets.put(zone.id(), bounds);
                paintZone(g, zone, bounds, zone.id().equals(selectedZone));
            }
            g.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            g.setColor(new Color(182, 204, 221));
            g.drawString("RIVER STATIONS  •  Illustrative basin layout — not a geographic forecast",
                    25, getHeight() - 7);
        } finally {
            g.dispose();
        }
    }

    private void paintRiver(Graphics2D g) {
        int midY = getHeight() / 2;
        g.setColor(new Color(22, 57, 78));
        g.setStroke(new BasicStroke(30, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(-20, midY - 20, getWidth() / 3, midY + 6);
        g.drawLine(getWidth() / 3, midY + 6, getWidth() * 2 / 3, midY - 4);
        g.drawLine(getWidth() * 2 / 3, midY - 4, getWidth() + 20, midY + 19);
        g.setColor(new Color(WATER.getRed(), WATER.getGreen(), WATER.getBlue(), 145));
        g.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(-20, midY - 20, getWidth() / 3, midY + 6);
        g.drawLine(getWidth() / 3, midY + 6, getWidth() * 2 / 3, midY - 4);
        g.drawLine(getWidth() * 2 / 3, midY - 4, getWidth() + 20, midY + 19);
    }

    private void paintZone(Graphics2D g, ZoneSnapshot zone, Rectangle box, boolean selected) {
        Color riskColor = colorFor(zone.risk());
        g.setColor(new Color(19, 35, 54, 235));
        g.fillRoundRect(box.x, box.y, box.width, box.height, 18, 18);
        g.setColor(selected ? new Color(225, 238, 248) : new Color(94, 117, 141));
        g.setStroke(new BasicStroke(selected ? 2.4f : 1.2f));
        g.drawRoundRect(box.x, box.y, box.width, box.height, 18, 18);

        g.setColor(riskColor);
        g.fillRoundRect(box.x + 13, box.y + 14, 7, box.height - 28, 7, 7);
        g.setFont(new Font("Segoe UI", Font.BOLD, 16));
        g.setColor(Color.WHITE);
        g.drawString(zone.name(), box.x + 31, box.y + 33);

        g.setFont(new Font("Segoe UI", Font.BOLD, 12));
        g.setColor(riskColor);
        String riskLabel = zone.risk().label().toUpperCase();
        FontMetrics metrics = g.getFontMetrics();
        int riskWidth = metrics.stringWidth(riskLabel) + 20;
        g.fillRoundRect(box.x + box.width - riskWidth - 13, box.y + 17, riskWidth, 23, 11, 11);
        g.setColor(new Color(16, 28, 41));
        g.drawString(riskLabel, box.x + box.width - riskWidth - 3, box.y + 33);

        g.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        g.setColor(new Color(188, 204, 220));
        g.drawString("WATER LEVEL", box.x + 31, box.y + 59);
        g.setFont(new Font("Segoe UI", Font.BOLD, 25));
        g.setColor(Color.WHITE);
        g.drawString(String.format("%.2f m", zone.waterLevelMeters()), box.x + 31, box.y + 87);
        g.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        g.setColor(new Color(188, 204, 220));
        g.drawString(String.format("Rain  %.1f mm     Exposed  %,d",
                zone.lastRainMillimeters(), zone.exposedResidents()), box.x + 31, box.y + 110);

        int barY = box.y + box.height - 18;
        int barWidth = box.width - 45;
        g.setColor(new Color(255, 255, 255, 35));
        g.fillRoundRect(box.x + 31, barY, barWidth, 6, 6, 6);
        g.setColor(riskColor);
        g.fillRoundRect(box.x + 31, barY, (int) (barWidth * Math.min(1, zone.waterLevelMeters() / 4.5)),
                6, 6, 6);

        g.setColor(new Color(122, 203, 229));
        g.fillOval(box.x + box.width - 25, box.y + box.height - 30, 9, 9);
    }

    private static Color colorFor(RiskLevel risk) {
        return switch (risk) {
            case NORMAL -> new Color(82, 196, 158);
            case WATCH -> new Color(235, 193, 84);
            case WARNING -> new Color(242, 139, 74);
            case CRITICAL -> new Color(239, 91, 97);
        };
    }
}
