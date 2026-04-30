package com.github.brickwall2900.polonium.components;

import com.github.brickwall2900.polonium.PoloniumConfig;
import com.github.brickwall2900.polonium.PoloniumMole;
import com.github.brickwall2900.polonium.models.PoloniumTableModel;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

public class PoloniumTable extends JTable {
    public final PoloniumTableModel tableModel;

    private final PoloniumConfig config;

    public PoloniumTable(PoloniumMole mole, PoloniumConfig config) {
        this.config = config;

        setModel(tableModel = new PoloniumTableModel(mole));
        setRowSorter(new PoloniumTableModel.PoloniumTableRowSorter(tableModel));
        setShowHorizontalLines(true);
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    }

    @Override
    public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
        Component c = super.prepareRenderer(renderer, row, column);
        PoloniumMole.Entry entry = tableModel.getEntry(row);

        if (isRowSelected(row)) {
            c.setBackground(getSelectionBackground());
            c.setForeground(getSelectionForeground());
        } else {
            c.setBackground(getBackground());
            c.setForeground(getForeground());
        }

        if (entry != null) {
            if (entry.excluded) {
                if (isRowSelected(row)) {
                    c.setBackground(UIManager.getColor("Actions.Grey"));
                } else {
                    c.setBackground(UIManager.getColor("Actions.GreyInline"));
                }
                c.setForeground(getSelectionForeground());
            } else if (entry.getDecay() > config.deleteDays) {
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
