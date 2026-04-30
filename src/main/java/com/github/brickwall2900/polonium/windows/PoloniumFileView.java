package com.github.brickwall2900.polonium.windows;

import com.github.brickwall2900.app.ProgressIndicator;
import com.github.brickwall2900.app.gui.LoadDialog;
import com.github.brickwall2900.polonium.PoloniumConfig;
import com.github.brickwall2900.polonium.PoloniumMole;
import com.github.brickwall2900.polonium.components.PoloniumTable;
import org.httprpc.sierra.UILoader;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;

import static com.github.brickwall2900.app.TranslatableText.BUNDLE;
import static com.github.brickwall2900.app.TranslatableText.text;

public class PoloniumFileView extends JFrame {
    public static final Dimension SIZE = new Dimension(800, 500);
    public static final String TITLE = text("file.title");

    public PoloniumFileView(String moleName, PoloniumMole mole, PoloniumConfig config) {
        super(TITLE);

        this.moleName = moleName;
        this.mole = mole;

        hashCodeEntryList = mole.entryList.hashCode();

        setContentPane(UILoader.load(this, "/ui/fileView.xml", BUNDLE));

        table = new PoloniumTable(mole, config);
        scrollPane.setViewportView(table);

        initPopupMenu();

//        moreButton.addActionListener(this::onMoreButtonPressed);
        closeButton.addActionListener(this::onCloseButtonPressed);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                onWindowClosing();
            }
        });

        setSize(SIZE);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }

    private void initPopupMenu() {
        popupMenu = new JPopupMenu();
        preemptDecayMenuItem = new JMenuItem(text("popup.file.preempt"));

        preemptDecayMenuItem.addActionListener(this::onPreemptDecayMenuPressed);
        popupMenu.add(preemptDecayMenuItem);

        table.setComponentPopupMenu(popupMenu);
    }

    // Events
    private void onCloseButtonPressed(ActionEvent e) {
        // simulate closing a window
        // closing events won't work without this
        dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
    }

    private void onWindowClosing() {
        if (hashCodeEntryList != mole.entryList.hashCode()) {
            LoadDialog loadDialog = new LoadDialog(this);
            ProgressIndicator progressIndicator = new ProgressIndicator(loadDialog);
            progressIndicator.setMaxProgress(1);
            progressIndicator.updateProgress(text("task.mole.save"), 0);
            MoleSavingWorker worker = new MoleSavingWorker(this, progressIndicator, moleName, mole);
            worker.addPropertyChangeListener(evt -> {
                if ("state".equals(evt.getPropertyName())) {
                    if (SwingWorker.StateValue.DONE.equals(evt.getNewValue())) {
                        loadDialog.closeLoadDialog();
                        progressIndicator.close();
                    } else if (SwingWorker.StateValue.STARTED.equals(evt.getNewValue())) {
                        loadDialog.openLoadDialog();
                    }
                }
            });
            worker.execute();
        }
    }

    private void onMoreButtonPressed(ActionEvent e) {

    }

    private void onPreemptDecayMenuPressed(ActionEvent e) {
        int selectedRow = table.getSelectedRow();
        if (selectedRow >= 0) {
            int modelRow = table.convertRowIndexToModel(selectedRow);
            PoloniumMole.Entry entry = table.tableModel.getEntry(modelRow);
            if (entry != null) {
                entry.timestamp = Instant.now().getEpochSecond();
            }
        }
    }

    @Override
    public void dispose() {
        super.dispose();
        PoloniumConfigEditor.openViewWindows.remove(moleName);
    }

    // Fields
    public String moleName;
    public PoloniumMole mole;
    public int hashCodeEntryList;

    // Controls
    public JScrollPane scrollPane;
    public PoloniumTable table;

    public JButton moreButton;
    public JButton closeButton;

    // Popup Menu
    public JPopupMenu popupMenu;
    public JMenuItem preemptDecayMenuItem;
}
