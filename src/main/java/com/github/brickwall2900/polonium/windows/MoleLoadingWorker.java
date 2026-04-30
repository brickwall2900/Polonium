package com.github.brickwall2900.polonium.windows;

import com.github.brickwall2900.app.ProgressIndicator;
import com.github.brickwall2900.polonium.PoloniumFileTracker;
import com.github.brickwall2900.polonium.PoloniumMole;
import org.tinylog.Logger;

import javax.swing.*;
import java.util.concurrent.ExecutionException;

import static com.github.brickwall2900.app.TranslatableText.text;

class MoleLoadingWorker extends SwingWorker<PoloniumMole, String> {
    private final PoloniumConfigEditor poloniumConfigEditor;
    private final ProgressIndicator progressIndicator;
    private final String moleName;

    MoleLoadingWorker(PoloniumConfigEditor poloniumConfigEditor, ProgressIndicator progressIndicator, String moleName) {
        this.poloniumConfigEditor = poloniumConfigEditor;
        this.progressIndicator = progressIndicator;
        this.moleName = moleName;
    }

    @Override
    protected PoloniumMole doInBackground() {
        return PoloniumFileTracker.loadMole(moleName, progressIndicator);
    }

    @Override
    protected void done() {
        super.done();
        try {
            PoloniumMole mole = get();
            if (!mole.entryList.isEmpty()) {
                PoloniumFileView fileView = new PoloniumFileView(moleName, mole, poloniumConfigEditor.config);
                PoloniumConfigEditor.openViewWindows.put(moleName, fileView);
                fileView.setVisible(true);
            } else {
                JOptionPane.showMessageDialog(poloniumConfigEditor, text("error.emptyMole"));
            }
        } catch (InterruptedException | ExecutionException e) {
            Logger.error(e, "Error during task execution!");
            JOptionPane.showMessageDialog(poloniumConfigEditor,
                    text("error.taskFailed") + '\n' + e,
                    poloniumConfigEditor.getTitle(), JOptionPane.ERROR_MESSAGE);
        }
    }
}
