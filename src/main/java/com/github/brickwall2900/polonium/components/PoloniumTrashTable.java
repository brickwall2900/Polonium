package com.github.brickwall2900.polonium.components;

import com.github.brickwall2900.polonium.PoloniumConfig;
import com.github.brickwall2900.polonium.models.PoloniumTrashTableModel;
import com.github.brickwall2900.polonium.trash.PoloniumTrash;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

public class PoloniumTrashTable extends JTable {
    public final PoloniumTrashTableModel tableModel;

    private PoloniumConfig config;

    public PoloniumTrashTable(PoloniumTrash.TrashDatabase database, PoloniumConfig config) {
        this.config = config;

        setModel(tableModel = new PoloniumTrashTableModel(database));
        setRowSorter(new PoloniumTrashTableModel.PoloniumTableRowSorter(tableModel));
        setShowHorizontalLines(true);
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    }

    public void setConfig(PoloniumConfig config) {
        this.config = config;
    }

    public void forceUpdate() {
        tableModel.forceUpdate();
    }

    @Override
    public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
        Component c = super.prepareRenderer(renderer, row, column);
        PoloniumTrash.TrashDatabase.Entry entry = tableModel.getEntry(row);

        if (isRowSelected(row)) {
            c.setBackground(getSelectionBackground());
            c.setForeground(getSelectionForeground());
        } else {
            c.setBackground(getBackground());
            c.setForeground(getForeground());
        }

        if (entry != null) {
            if (entry.getDecay() > config.deleteDays) {
                if (isRowSelected(row)) {
                    c.setBackground(UIManager.getColor("Component.error.focusedBorderColor"));
                } else {
                    c.setBackground(UIManager.getColor("Component.error.borderColor"));
                }
            } else if (entry.getDecay() > config.warnDays) {
                if (isRowSelected(row)) {
                    c.setBackground(UIManager.getColor("Component.warning.focusedBorderColor"));
                } else {
                    c.setBackground(UIManager.getColor("Component.warning.borderColor"));
                }
            }
        }
        return c;
    }
}
