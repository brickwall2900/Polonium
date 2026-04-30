package com.github.brickwall2900.app.gui;

import com.github.brickwall2900.app.ProgressIndicator;
import org.httprpc.sierra.ActivityIndicator;
import org.httprpc.sierra.TextPane;
import org.httprpc.sierra.UILoader;
import org.tinylog.Logger;

import javax.swing.*;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

import static com.github.brickwall2900.app.TranslatableText.BUNDLE;
import static com.github.brickwall2900.app.TranslatableText.text;
import static java.awt.Dialog.ModalityType.MODELESS;

public class LoadDialog extends JDialog implements ProgressIndicator.ProgressUpdater {
    public Window parent;

    public JProgressBar progressBar;
    public TextPane taskDoing;
    public ActivityIndicator loadingIcon;

    public JScrollPane subtasksScrollPane;
    public JPanel subtasksPanel;

    private final Map<ProgressIndicator, SubtaskThing> progressSubtaskThingMap = new HashMap<>();

    public LoadDialog(Window parent) {
        super(parent);
        this.parent = parent;

        setContentPane(UILoader.load(this, "/ui/loading.xml", BUNDLE));

        progressBar.setStringPainted(true);

        subtasksPanel = new JPanel();
        subtasksPanel.setLayout(new BoxLayout(subtasksPanel, BoxLayout.Y_AXIS));
        subtasksScrollPane.setViewportView(subtasksPanel);
        subtasksScrollPane.setBorder(BorderFactory.createTitledBorder("Subtasks"));
        subtasksScrollPane.setVisible(false);

        setModal(false);
        setModalityType(MODELESS);
        setTitle(parent != null ? "Task" : "???");
        setSize(/* 320 */ 500, 100);
        setResizable(true);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setLocationRelativeTo(parent);
    }

    private static class SubtaskThing extends JPanel {
        JLabel descriptionLabel;
        JProgressBar subProgressBar;

        public SubtaskThing() {
            super(new GridBagLayout());
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(2, 5, 2, 5);
            c.fill = GridBagConstraints.HORIZONTAL;
            c.weightx = 1.0;

            descriptionLabel = new JLabel("Task Name?");
            subProgressBar = new JProgressBar(0, 100);
            subProgressBar.setStringPainted(true);

            c.gridx = 0;
            c.gridy = 0;
            add(descriptionLabel, c);

            c.gridy = 1;
            add(subProgressBar, c);

            c.gridy = 2;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            add(Box.createVerticalStrut(5), c);
        }

        public void setProgress(float progress) {
            subProgressBar.setValue((int) (progress * subProgressBar.getMaximum()));
            if (progress < 0) {
                subProgressBar.setIndeterminate(true);
                subProgressBar.setString(BUNDLE.getString("ui.task.indeterminate"));
            } else {
                subProgressBar.setIndeterminate(false);
                subProgressBar.setString(BUNDLE.getString("ui.task.determinate").formatted(progress * 100f));
            }
        }

        public void setMaxProgress(int maxProgress) {
            subProgressBar.setMaximum(maxProgress);
        }

        public void setDescription(String description) {
            descriptionLabel.setText(description);
        }

        public void markAsComplete() {
            subProgressBar.setValue(subProgressBar.getMaximum());
            subProgressBar.setString(text("ui.task.complete"));
            descriptionLabel.setEnabled(false);
            subProgressBar.setEnabled(false);

        }
    }

    public void openLoadDialog(String task, int progress) {
        SwingUtilities.invokeLater(() -> {
            loadingIcon.start();
            setTaskName(task);
            setProgress(progress);
            setVisible(true);
        });
    }

    public void openLoadDialog() {
        SwingUtilities.invokeLater(() -> {
            loadingIcon.start();
            setVisible(true);
        });
    }

    public void openLoadDialog(boolean open) {
        SwingUtilities.invokeLater(() -> {
            if (open) openLoadDialog();
            else closeLoadDialog();
        });
    }

    public void setTaskName(String task) {
        SwingUtilities.invokeLater(() -> {
            if (task != null) {
                setTitle(task);
                taskDoing.setText(task);
            }
        });
    }

    public void setProgress(float progress) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setValue((int) (progress * progressBar.getMaximum()));
            if (progress < 0) {
                progressBar.setIndeterminate(true);
                progressBar.setString(BUNDLE.getString("ui.task.indeterminate"));
            } else {
                progressBar.setIndeterminate(false);
                progressBar.setString(BUNDLE.getString("ui.task.determinate").formatted(progress * 100f));
            }
        });
    }

    public void setMaxProgress(int progress) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setMaximum(progress);
        });
    }

    public void closeLoadDialog() {
        SwingUtilities.invokeLater(() -> {
            loadingIcon.stop();
            dispose();
        });
    }


    @Override
    public void updateOverallProgress(ProgressIndicator source) {
        SwingUtilities.invokeLater(() -> {
            float overallProgress = ((float) source.getProgress() / source.getMaxProgress());
            setTaskName(source.getTaskDescription());
            setProgress(overallProgress);

            SubtaskThing thing = progressSubtaskThingMap.get(source);
            if (thing == null) {
                thing = new SubtaskThing();
                progressSubtaskThingMap.put(source, thing);
                subtasksScrollPane.setVisible(true);
                subtasksPanel.add(thing);
            }
            if (source.isCompleted()) {
                thing.markAsComplete();
                subtasksPanel.remove(thing);
                progressSubtaskThingMap.remove(source);
            }
            subtasksScrollPane.setVisible(!progressSubtaskThingMap.isEmpty());
            subtasksPanel.revalidate();
            subtasksPanel.repaint();
            revalidate();
//                int width = getWidth();
//                setSize(width, getPreferredSize().height);
            thing.setDescription(source.getTaskDescription());
            thing.setProgress(overallProgress);
        });
    }
}
