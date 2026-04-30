package com.github.brickwall2900.polonium.windows;

import com.github.brickwall2900.app.ConfigManager;
import com.github.brickwall2900.app.ProgressIndicator;
import com.github.brickwall2900.app.gui.LoadDialog;
import com.github.brickwall2900.polonium.PoloniumConfig;
import com.github.brickwall2900.polonium.PoloniumFileTracker;
import com.github.brickwall2900.polonium.components.PoloniumTrashTab;
import org.httprpc.sierra.UILoader;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Objects;

import static com.github.brickwall2900.app.TranslatableText.BUNDLE;
import static com.github.brickwall2900.app.TranslatableText.text;

public class PoloniumConfigEditor extends JFrame {
    public static final Dimension SIZE = new Dimension(700, 400);
    public static final String TITLE = text("mole.title");

    public PoloniumConfigEditor(PoloniumConfig config, Path configPath) {
        super(TITLE);

        this.config = config;
        this.configPath = configPath;

        setContentPane(UILoader.load(this, "/ui/configEditor.xml", BUNDLE));

        list = new JList<>(listModel = new DefaultListModel<>());
        listScrollPane = new JScrollPane();
        listScrollPane.setViewportView(list);

        tabbedPane.addTab(text("mole.tab.paths"), listScrollPane);
        tabbedPane.addTab(text("mole.tab.behavior"), behaviorForm = new BehaviorForm());
        tabbedPane.addTab(text("mole.tab.trash"), trashTab = new PoloniumTrashTab(config));
        behaviorForm.initForm();

        listModel.addAll(config.paths);
        warnDaysField.setText(Long.toString(config.warnDays));
        deleteDaysField.setText(Long.toString(config.deleteDays));

        warnDaysFeedback.setForeground(UIManager.getColor("Actions.Red"));
        deleteDaysFeedback.setForeground(UIManager.getColor("Actions.Red"));

        removeButton.setEnabled(false);
//        editButton.setEnabled(false);
        viewButton.setEnabled(false);

        tabbedPane.addChangeListener(this::onTabChanged);

        closeButton.addActionListener(this::onCloseButtonPressed);
        addButton.addActionListener(this::onAddButtonPressed);
        removeButton.addActionListener(this::onRemoveButtonPressed);
//        editButton.addActionListener(this::onEditButtonPressed);
        viewButton.addActionListener(this::onViewButtonPressed);

        list.getSelectionModel().addListSelectionListener(this::onListSelectionChanged);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (saveConfig()) {
                    System.exit(0);
                }
            }
        });

        setSize(SIZE);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    }

    // Methods
    private Path chooseFile() {
        File file = new File(System.getProperty("user.dir"));
        if (fileChooser == null) {
            fileChooser = new JFileChooser(file);
            fileChooser.setMultiSelectionEnabled(false);
            fileChooser.setAcceptAllFileFilterUsed(false);
            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fileChooser.setFileFilter(new DirectoryFileFilter());
        }
        fileChooser.setCurrentDirectory(file);
        int returnVal = fileChooser.showOpenDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            return fileChooser.getSelectedFile().toPath();
        }
        return null;
    }

    private boolean saveConfig() {
        String error = null;
        PoloniumConfig tmpConfig = new PoloniumConfig();

        tmpConfig.paths.clear();
        for (Object o : listModel.toArray()) {
            // mm yes
            tmpConfig.paths.add(o.toString());
        }

        warnDaysFeedback.setText("");
        deleteDaysFeedback.setText("");

        try {
            tmpConfig.warnDays = Long.parseLong(warnDaysField.getText());
        } catch (NumberFormatException e) {
            error = e.toString();
            warnDaysFeedback.setText(text("error.numberParse", e.getMessage()));
        }

        try {
            tmpConfig.deleteDays = Long.parseLong(deleteDaysField.getText());
        } catch (NumberFormatException e) {
            error = e.toString();
            deleteDaysFeedback.setText(text("error.numberParse", e.getMessage()));
        }

        if (tmpConfig.warnDays >= tmpConfig.deleteDays) {
            error = text("error.invalidDays");
        }

        if (error == null) {
            if (!Objects.equals(tmpConfig, config)) {
                config = tmpConfig;
                ConfigManager.save(configPath, config);
                trashTab.setConfig(config);
                openViewWindows.values().forEach(SwingUtilities::updateComponentTreeUI);
            }
        } else {
            JOptionPane.showMessageDialog(this, text("error.cantSave", error),
                    getTitle(), JOptionPane.ERROR_MESSAGE);
        }
        return error == null;
    }

    // Events
    private void onCloseButtonPressed(ActionEvent e) {
        // simulate closing a window
        // closing events won't work without this
        dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
    }

    private void onTabChanged(ChangeEvent e) {
        saveConfig();
    }

    private void onAddButtonPressed(ActionEvent e) {
        Path path = chooseFile();
        if (path != null) {
            String mole = path.toString();
            listModel.addElement(mole);
            String[] options = { text("button.keep"), text("button.forget") };
            if (PoloniumFileTracker.doesMoleExist(mole) && JOptionPane.showOptionDialog(this,
                    text("mole.dialog.add.databaseExists", mole), getTitle(),
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null, options, options[0]) == 1) {
                PoloniumFileTracker.removeMole(mole);
            }
        }
    }

    private void onRemoveButtonPressed(ActionEvent e) {
        String selected = list.getSelectedValue();
        if (selected != null && JOptionPane.showConfirmDialog(this,
                        text("mole.dialog.remove", selected), getTitle(), JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
            listModel.removeElement(selected);
            if (JOptionPane.showConfirmDialog(this,
                    text("mole.dialog.remove.database", selected), getTitle(), JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                PoloniumFileTracker.removeMole(selected);
            }
        }
    }

    private void onEditButtonPressed(ActionEvent e) {
        String selected = list.getSelectedValue();
        int index = listModel.indexOf(selected);
        if (index >= 0) {
            Path path = chooseFile();
            if (path != null) {
                listModel.setElementAt(path.toString(), index);
            }
        }
    }

    static HashMap<String, PoloniumFileView> openViewWindows = new HashMap<>();
    private void onViewButtonPressed(ActionEvent e) {
        if (!saveConfig()) {
            return;
        }

        String selected = list.getSelectedValue();
        if (openViewWindows.containsKey(selected)) {
            openViewWindows.get(selected).requestFocus();
        } else {
            LoadDialog loadDialog = new LoadDialog(this);
            ProgressIndicator progressIndicator = new ProgressIndicator(loadDialog);
            progressIndicator.setMaxProgress(1);
            progressIndicator.updateProgress(text("task.mole.load"), 0);
            MoleLoadingWorker worker = getMoleLoadingWorker(progressIndicator, selected, loadDialog);
            worker.execute();
        }
    }

    private MoleLoadingWorker getMoleLoadingWorker(ProgressIndicator progressIndicator, String selected, LoadDialog loadDialog) {
        MoleLoadingWorker worker = new MoleLoadingWorker(this, progressIndicator, selected);
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
        return worker;
    }

    private void onListSelectionChanged(ListSelectionEvent e) {
        String selected = list.getSelectedValue();
        removeButton.setEnabled(selected != null);
//        editButton.setEnabled(selected != null);
        viewButton.setEnabled(selected != null);
    }

    // Fields
    public final Path configPath;
    public PoloniumConfig config;

    // Models
    public DefaultListModel<String> listModel;

    // Controls
    public JScrollPane scrollPane;

    public JTabbedPane tabbedPane;

    public JScrollPane listScrollPane;
    public JList<String> list;

    public BehaviorForm behaviorForm;
    public JTextField warnDaysField, deleteDaysField;
    public JLabel warnDaysFeedback, deleteDaysFeedback;

    public JButton addButton, removeButton, editButton, viewButton;
    public JButton aboutButton, closeButton;

    public JFileChooser fileChooser;

    public PoloniumTrashTab trashTab;

    public class BehaviorForm extends JPanel {
        public static final int FORM_INSETS = 2;
        public BehaviorForm() {
            super(new GridBagLayout());
        }

        public void initForm() {
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(FORM_INSETS, FORM_INSETS, FORM_INSETS, FORM_INSETS);

            newField(text("mole.fields.warn"), this, warnDaysField = new JTextField(), warnDaysFeedback = new JLabel(), c);
            newField(text("mole.fields.delete"), this, deleteDaysField = new JTextField(), deleteDaysFeedback = new JLabel(), c);
        }

        static <T extends JComponent, X extends JComponent> void newField(String fieldName, JPanel where, T component, X feedbackComponent, GridBagConstraints c) {
            c.gridx = 0;
            c.gridy += 1;
            c.fill = GridBagConstraints.NONE;
            c.anchor = GridBagConstraints.WEST;
            c.weightx = 0;
            where.add(new JLabel(fieldName), c);

            c.gridx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.weightx = 1;
            where.add(component, c);

            c.gridy += 1;
            c.gridx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.weightx = 1;
            where.add(Objects.requireNonNullElseGet(feedbackComponent, Box::createHorizontalGlue), c); // WHAT THAT'S A THING??
        }
    }
}
