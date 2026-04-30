package com.github.brickwall2900.polonium.models;

import com.github.brickwall2900.polonium.trash.PoloniumTrash;

import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import static com.github.brickwall2900.app.TranslatableText.text;

public class PoloniumTrashTableModel extends AbstractTableModel {
    public static final int COL_FILE_NAME = 0;
    public static final int COL_FILE_DECAY = 1;
    public static final int COL_FILE_LAST_MODIFIED = 2;
    public static final int COL_FILE_SIZE = 3;
    public static final int COL_FILE_PATH = 4;

    private final PoloniumTrash.TrashDatabase trashDatabase;
    private final List<String> sortedIds;

    public PoloniumTrashTableModel(PoloniumTrash.TrashDatabase trashDatabase) {
        this.trashDatabase = trashDatabase;

        sortedIds = new ArrayList<>();

        TreeSet<String> sortedEntry = new TreeSet<>(trashDatabase.entryMap.keySet());
        sortedIds.addAll(sortedEntry);
    }

    @Override
    public int getRowCount() {
        return trashDatabase.entryMap.size();
    }

    @Override
    public int getColumnCount() {
        return 5;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if (sortedIds.size() > rowIndex) {
            String id = sortedIds.get(rowIndex);
            PoloniumTrash.TrashDatabase.Entry entry = trashDatabase.entryMap.get(id);

            return switch (columnIndex) {
                case COL_FILE_NAME -> entry.filename;
                case COL_FILE_DECAY -> entry.getDecay();
                case COL_FILE_LAST_MODIFIED -> Instant.ofEpochSecond(entry.timestampTrashed);
                case COL_FILE_SIZE -> entry.size;
                case COL_FILE_PATH -> entry.path;
                default -> null;
            };
        } else {
            return null;
        }
    }

    @Override
    public String getColumnName(int column) {
        return switch (column) {
            case COL_FILE_NAME -> text("table.column.fileName");
            case COL_FILE_DECAY -> text("table.column.fileDecay");
            case COL_FILE_LAST_MODIFIED -> text("table.column.lastModified");
            case COL_FILE_SIZE -> text("table.column.fileSize");
            case COL_FILE_PATH -> text("table.column.filePath");
            default -> text("table.unknown");
        };
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return switch (columnIndex) {
            case COL_FILE_NAME, COL_FILE_PATH -> String.class;
            case COL_FILE_DECAY -> Long.class;
            case COL_FILE_LAST_MODIFIED -> Instant.class;
            case COL_FILE_SIZE -> Long.class;
            default -> super.getColumnClass(columnIndex);
        };
    }

    public PoloniumTrash.TrashDatabase.Entry getEntry(int index) {
        String id = sortedIds.get(index);
        return trashDatabase.entryMap.get(id);
    }

    public void forceUpdate() {
        fireTableDataChanged();
    }

    public static class PoloniumTableRowSorter extends TableRowSorter<PoloniumTrashTableModel> {
        public PoloniumTableRowSorter(PoloniumTrashTableModel model) {
            super(model);
            setComparator(COL_FILE_NAME, PoloniumTableRowSorter::sortString);
            setComparator(COL_FILE_DECAY, PoloniumTableRowSorter::sortLong);
            setComparator(COL_FILE_LAST_MODIFIED, PoloniumTableRowSorter::sortByDate);
            setComparator(COL_FILE_SIZE, PoloniumTableRowSorter::sortLong);
            setComparator(COL_FILE_PATH, PoloniumTableRowSorter::sortString);
        }

        private static int sortString(Object o1, Object o2) {
            if (o1 instanceof String b1 && o2 instanceof String b2) {
                return b1.compareTo(b2);
            } else {
                return 0;
            }
        }

        private static int sortLong(Object o1, Object o2) {
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
