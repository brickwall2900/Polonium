package com.github.brickwall2900.polonium.windows;

import com.github.brickwall2900.app.ProgressIndicator;
import com.github.brickwall2900.polonium.PoloniumFileTracker;
import com.github.brickwall2900.polonium.PoloniumMole;

import javax.swing.*;

class MoleSavingWorker extends SwingWorker<Void, String> {
    private final PoloniumFileView poloniumFileView;
    private final ProgressIndicator progressIndicator;
    private final String moleName;
    private final PoloniumMole mole;

    MoleSavingWorker(PoloniumFileView poloniumFileView, ProgressIndicator progressIndicator, String moleName, PoloniumMole mole) {
        this.poloniumFileView = poloniumFileView;
        this.progressIndicator = progressIndicator;
        this.moleName = moleName;
        this.mole = mole;
    }

    @Override
    protected Void doInBackground() {
        PoloniumFileTracker.saveMole(moleName, mole, progressIndicator);
        return null;
    }

    @Override
    protected void done() {
        super.done();
        poloniumFileView.dispose();
    }
}
