package com.github.brickwall2900.polonium;

import javax.swing.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.github.brickwall2900.app.TranslatableText.text;

public class PoloniumNotifier {
    private static final int MAX_SIZE = 30;
    private static final String TITLE = text("notifier.title");

    public static boolean notifyFiles(String textIdMessage, List<String> files) {
        if (!files.isEmpty()) {
            List<String> displayedFiles = new ArrayList<>(files);
            Collections.sort(displayedFiles);
            int more = displayedFiles.size() - MAX_SIZE;
            while (displayedFiles.size() > MAX_SIZE) {
                displayedFiles.removeLast();
            }
            String displayedFilesString = displayedFiles.stream()
                    .reduce("", (x, y) -> String.join(x, "\n", y));
            if (more > 0) {
                displayedFilesString += "\n" + text("notifier.more", more);
            }

            JOptionPane optionPane = new JOptionPane();
            optionPane.setMessage(text(textIdMessage, displayedFilesString));
            optionPane.setMessageType(JOptionPane.WARNING_MESSAGE);

            String[] options = {text("notifier.options.open"), text("notifier.options.close")};
            optionPane.setOptionType(JOptionPane.DEFAULT_OPTION);
            optionPane.setOptions(options);
            optionPane.setValue(options[0]);

            JDialog dialog = optionPane.createDialog(TITLE);
            dialog.setAlwaysOnTop(true);
            dialog.pack();
            dialog.setLocationRelativeTo(null);
            dialog.setVisible(true);

            String option = String.valueOf(optionPane.getValue());
            return options[0].equals(option);
        }
        return false;
    }
}
