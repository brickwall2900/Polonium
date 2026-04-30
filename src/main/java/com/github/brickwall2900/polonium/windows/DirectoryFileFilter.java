package com.github.brickwall2900.polonium.windows;

import javax.swing.filechooser.FileFilter;
import java.io.File;

public class DirectoryFileFilter extends FileFilter {
    @Override
    public boolean accept(File f) {
        return f.isDirectory();
    }

    @Override
    public String getDescription() {
        return "Directory";
    }
}
