package com.github.brickwall2900.polonium.models;

import com.github.brickwall2900.polonium.PoloniumMole;
import org.tinylog.Logger;

import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static com.github.brickwall2900.app.TranslatableText.text;

public class PoloniumTableModel extends AbstractTableModel {
    public static final int COL_FILE_PATH = 0;
    public static final int COL_FILE_DECAY = 1;
    public static final int COL_FILE_LAST_MODIFIED = 2;
    public static final int COL_FILE_EXCLUDED = 3;

    private final PoloniumMole mole;
    private final List<Path> sortedFilePaths;

    public PoloniumTableModel(PoloniumMole mole) {
        this.mole = mole;

        sortedFilePaths = new ArrayList<>();

        TreeSet<Path> sortedEntry = new TreeSet<>(mole.entryList.keySet());
        sortedFilePaths.addAll(sortedEntry);
    }

    @Override
    public int getRowCount() {
        return mole.entryList.size();
    }

    @Override
    public int getColumnCount() {
        return 4;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if (sortedFilePaths.size() > rowIndex) {
            Path path = sortedFilePaths.get(rowIndex);
            PoloniumMole.Entry entry = mole.entryList.get(path);

            return switch (columnIndex) {
                case COL_FILE_PATH -> path.toString();
                case COL_FILE_DECAY -> entry.getDecay();
                case COL_FILE_LAST_MODIFIED -> Instant.ofEpochSecond(entry.timestamp);
                case COL_FILE_EXCLUDED -> entry.excluded;
                default -> null;
            };
        } else {
            return null;
        }
    }

    @Override
    public String getColumnName(int column) {
        return switch (column) {
            case COL_FILE_PATH -> text("table.column.filePath");
            case COL_FILE_DECAY -> text("table.column.fileDecay");
            case COL_FILE_LAST_MODIFIED -> text("table.column.lastModified");
            case COL_FILE_EXCLUDED -> text("table.column.excluded");
            default -> text("table.unknown");
        };
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return switch (columnIndex) {
            case COL_FILE_PATH -> String.class;
            case COL_FILE_DECAY -> Long.class;
            case COL_FILE_LAST_MODIFIED -> Instant.class;
            case COL_FILE_EXCLUDED -> Boolean.class;
            default -> super.getColumnClass(columnIndex);
        };
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return columnIndex == COL_FILE_EXCLUDED;
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        if (sortedFilePaths.size() > rowIndex) {
            Path path = sortedFilePaths.get(rowIndex);
            PoloniumMole.Entry entry = mole.entryList.get(path);
            switch (columnIndex) {
                case COL_FILE_EXCLUDED -> entry.excluded = (boolean) aValue;
            }
        }
    }

    public PoloniumMole.Entry getEntry(int index) {
        Path path = sortedFilePaths.get(index);
        return mole.entryList.get(path);
    }

    public static class PoloniumTableRowSorter extends TableRowSorter<PoloniumTableModel> {
        public PoloniumTableRowSorter(PoloniumTableModel model) {
            super(model);
            setComparator(COL_FILE_PATH, PoloniumTableRowSorter::sortByName);
            setComparator(COL_FILE_DECAY, PoloniumTableRowSorter::sortByDecay);
            setComparator(COL_FILE_LAST_MODIFIED, PoloniumTableRowSorter::sortByDate);
        }

        private static int sortByName(Object o1, Object o2) {
            if (o1 instanceof String b1 && o2 instanceof String b2) {
                return b1.compareTo(b2);
            } else {
                return 0;
            }
        }

        private static int sortByDecay(Object o1, Object o2) {
            if (o1 instanceof Long b1 && o2 instanceof Long b2) {
                return b1.compareTo(b2);
            } else {
                return 0;
            }
        }

        private static int sortByDate(Object o1, Object o2) {
            if (o1 instanceof LocalDate b1 && o2 instanceof LocalDate b2) {
                return b1.compareTo(b2);
            } else {
                return 0;
            }
        }
    }
}
