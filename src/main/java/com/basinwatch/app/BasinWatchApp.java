package com.basinwatch.app;

import com.basinwatch.domain.BasinModel;
import com.basinwatch.domain.BasinSnapshot;
import com.basinwatch.engine.BasinEngine;
import com.basinwatch.io.AppPaths;
import com.basinwatch.io.SnapshotStore;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class BasinWatchApp {
    private BasinWatchApp() {
    }

    public static void main(String[] arguments) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException ex) {
            System.err.println("Using the standard Swing appearance: " + ex.getMessage());
        }
        String[] suppliedArguments = arguments.clone();
        SwingUtilities.invokeLater(() -> openApplication(suppliedArguments));
    }

    private static void openApplication(String[] arguments) {
        AppPaths paths;
        try {
            paths = AppPaths.create(AppPaths.resolveDataRoot(arguments));
        } catch (IOException | IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(null,
                    "BasinWatch could not open its writable data folder.\n\n" + ex.getMessage(),
                    "Startup failed", JOptionPane.ERROR_MESSAGE);
            return;
        }
        BasinModel model = new BasinModel();
        Path lastSession = paths.saves().resolve("last-session.bws");
        if (Files.exists(lastSession)) {
            try {
                BasinSnapshot saved = new SnapshotStore().load(lastSession);
                model = BasinModel.restore(saved);
            } catch (IOException | IllegalArgumentException ex) {
                int choice = JOptionPane.showConfirmDialog(null,
                        "The previous session could not be restored:\n"
                                + ex.getMessage() + "\n\nStart a fresh scenario? The saved file "
                                + "will be retained until a later clean shutdown.",
                        "Session restore issue", JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (choice != JOptionPane.YES_OPTION) {
                    return;
                }
            }
        }
        try {
            BasinEngine engine = new BasinEngine(paths, model);
            MainFrame frame = new MainFrame(engine, paths);
            frame.setVisible(true);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(null,
                    "BasinWatch could not open its operational log.\n\n" + ex.getMessage(),
                    "Startup failed", JOptionPane.ERROR_MESSAGE);
        }
    }
}
