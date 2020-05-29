package gui;

import controller.*;
import view.TabPaneView;

import javax.swing.*;

public class QueryToolbar extends JToolBar {

    private JButton addButton = new JButton("Add");
    private JButton updateButton = new JButton("Update");
    private JButton deleteButton = new JButton("Delete");
    private JButton filterSortButton = new JButton("Filter and Sort");
    private JButton searchButton = new JButton("Search");
    private JTabbedPane tabPane = null;

    public QueryToolbar() {
        setFloatable(false);

        addButton.addActionListener(new AddButtonController(this));
        updateButton.addActionListener(new UpdateButtonController(this));
        deleteButton.addActionListener(new DeleteButtonController(this));
        filterSortButton.addActionListener(new FilterAndSortController(this));
        searchButton.addActionListener(new SearchController(this));

        add(addButton);
        add(updateButton);
        add(deleteButton);
        add(filterSortButton);
        add(searchButton);

        setVisible(false);
    }

    public JTabbedPane getTabPane() {
        return tabPane;
    }

    public void setTabPane(JTabbedPane tabPane) {
        this.tabPane = tabPane;
    }
}
