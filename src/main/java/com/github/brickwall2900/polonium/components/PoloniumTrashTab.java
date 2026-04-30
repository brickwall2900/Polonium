package com.github.brickwall2900.polonium.components;

import com.github.brickwall2900.app.ApplicationUtil;
import com.github.brickwall2900.polonium.PoloniumConfig;
import com.github.brickwall2900.polonium.trash.PoloniumTrash;
import com.github.brickwall2900.polonium.windows.DirectoryFileFilter;
import com.github.brickwall2900.polonium.windows.PoloniumConfigEditor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.github.brickwall2900.app.TranslatableText.text;

public class PoloniumTrashTab extends JPanel {
    public PoloniumTrashTab(PoloniumConfig config) {
        super(new BorderLayout());

        scrollPane = new JScrollPane(table = new PoloniumTrashTable(PoloniumTrash.TRASH_DATABASE, config));
        add(scrollPane, BorderLayout.CENTER);

        initPopupMenu();
    }

    private void initPopupMenu() {
        popupMenu = new JPopupMenu();

        restoreMenuItem = new JMenuItem(text("popup.trash.restore"));
        restoreMenuItem.addActionListener(this::onRestoreMenuItemPressed);

        popupMenu.add(restoreMenuItem);
        table.setComponentPopupMenu(popupMenu);
    }

    // Methods
    public void setConfig(PoloniumConfig config) {
        table.setConfig(config);
    }

    private Path chooseFile(String filename) {
        File file = new File(System.getProperty("user.dir"));
        if (fileChooser == null) {
            fileChooser = new JFileChooser(file);
            fileChooser.setMultiSelectionEnabled(false);
            fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        }
        fileChooser.setCurrentDirectory(file);
        int returnVal = fileChooser.showSaveDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            return fileChooser.getSelectedFile().toPath();
        }
        return null;
    }

    // Events
    private void onRestoreMenuItemPressed(ActionEvent e) {
        int selected = table.getSelectedRow();
        if (selected >= 0) {
            PoloniumTrash.TrashDatabase.Entry entry = table.tableModel.getEntry(selected);
            String id = entry.id;
            String filename = entry.filename;
            Path chosenPath = chooseFile(filename);

            if (chosenPath == null) {
                return;
            }

            if (Files.exists(chosenPath)) {
                if (Files.isRegularFile(chosenPath)) {
                    int choice = JOptionPane.showConfirmDialog(
                            this,
                            text("mole.dialog.trash.replace", chosenPath, filename)
                    );
                    if (choice != JOptionPane.YES_OPTION) {
                        return;
                    }
                }
            } else {
                try {
                    Files.createFile(chosenPath);
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            }

            Path safeDestination = ApplicationUtil.resolveSafe(chosenPath, filename);
            PoloniumTrash.restore(id, safeDestination);

            JOptionPane.showMessageDialog(this, text("mole.dialog.trash.success", safeDestination));

            table.forceUpdate();
        }
    }

    // Controls
    public JScrollPane scrollPane;
    public PoloniumTrashTable table;

    public JFileChooser fileChooser;

    // Popup Menu
    public JPopupMenu popupMenu;
    public JMenuItem restoreMenuItem;
}
