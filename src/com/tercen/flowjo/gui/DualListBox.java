package com.tercen.flowjo.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.AbstractListModel;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListModel;

public class DualListBox extends JPanel {

	private static final Insets CUSTOM_INSETS = new Insets(4, 2, 4, 2);
	private static final String ADD_BUTTON_LABEL = "Add >";
	private static final String ADD_ALL_BUTTON_LABEL = "Add all >>";
	private static final String REMOVE_BUTTON_LABEL = "< Remove";
	private static final String REMOVE_ALL_BUTTON_LABEL = "<< Remove all";
	private static final String DEFAULT_SOURCE_CHOICE_LABEL = "Available Channels";
	private static final String DEFAULT_DEST_CHOICE_LABEL = "Selected Channels";

	private JLabel sourceLabel, destLabel;
	private JList sourceList, destList;
	private SortedListModel sourceListModel, destListModel;

	public DualListBox(List<String> values, List<String> compensatedParams) {
		initScreen();
		List<String> inputValues = Stream.concat(values.stream(), compensatedParams.stream())
				.filter(i -> values.contains(i) && !compensatedParams.contains(i)).collect(Collectors.toList());
		addSourceElements(inputValues.toArray());
		addDestinationElements(compensatedParams.toArray());
	}

	public String getSourceChoicesTitle() {
		return sourceLabel.getText();
	}

	public void setSourceChoicesTitle(String newValue) {
		sourceLabel.setText(newValue);
	}

	public String getDestinationChoicesTitle() {
		return destLabel.getText();
	}

	public void setDestinationChoicesTitle(String newValue) {
		destLabel.setText(newValue);
	}

	public void clearSourceListModel() {
		sourceListModel.clear();
	}

	public void clearDestinationListModel() {
		destListModel.clear();
	}

	public void addSourceElements(ListModel newValue) {
		fillListModel(sourceListModel, newValue);
	}

	public void setSourceElements(ListModel newValue) {
		clearSourceListModel();
		addSourceElements(newValue);
	}

	public void addDestinationElements(ListModel newValue) {
		fillListModel(destListModel, newValue);
	}

	private void fillListModel(SortedListModel model, ListModel newValues) {
		int size = newValues.getSize();
		for (int i = 0; i < size; i++) {
			model.add(newValues.getElementAt(i));
		}
	}

	public void addSourceElements(Object newValue[]) {
		fillListModel(sourceListModel, newValue);
	}

	public void setSourceElements(Object newValue[]) {
		clearSourceListModel();
		addSourceElements(newValue);
	}

	public void addDestinationElements(Object newValue[]) {
		fillListModel(destListModel, newValue);
	}

	private void fillListModel(SortedListModel model, Object newValues[]) {
		model.addAll(newValues);
	}

	public Iterator sourceIterator() {
		return sourceListModel.iterator();
	}

	public Iterator destinationIterator() {
		return destListModel.iterator();
	}

	private List<String> getAllItems(Iterator iterator) {
		List<Object> result = new ArrayList<Object>();
		iterator.forEachRemaining(result::add);
		return result.stream().map(object -> (String) object).collect(Collectors.toList());
	}

	public List<String> getAllResultItems() {
		return getAllItems(this.destinationIterator());
	}

	public List<String> getAllSourceItems() {
		return getAllItems(this.sourceIterator());
	}

	public void setSourceCellRenderer(ListCellRenderer newValue) {
		sourceList.setCellRenderer(newValue);
	}

	public ListCellRenderer getSourceCellRenderer() {
		return sourceList.getCellRenderer();
	}

	public void setDestinationCellRenderer(ListCellRenderer newValue) {
		destList.setCellRenderer(newValue);
	}

	public ListCellRenderer getDestinationCellRenderer() {
		return destList.getCellRenderer();
	}

	public void setVisibleRowCount(int newValue) {
		sourceList.setVisibleRowCount(newValue);
		destList.setVisibleRowCount(newValue);
	}

	public int getVisibleRowCount() {
		return sourceList.getVisibleRowCount();
	}

	public void setSelectionBackground(Color newValue) {
		sourceList.setSelectionBackground(newValue);
		destList.setSelectionBackground(newValue);
	}

	public Color getSelectionBackground() {
		return sourceList.getSelectionBackground();
	}

	public void setSelectionForeground(Color newValue) {
		sourceList.setSelectionForeground(newValue);
		destList.setSelectionForeground(newValue);
	}

	public Color getSelectionForeground() {
		return sourceList.getSelectionForeground();
	}

	private void clearSourceSelected() {
		Object selected[] = sourceList.getSelectedValues();
		for (int i = selected.length - 1; i >= 0; --i) {
			sourceListModel.removeElement(selected[i]);
		}
		sourceList.getSelectionModel().clearSelection();
	}

	private void clearSource(Object items[]) {
		for (int i = items.length - 1; i >= 0; --i) {
			sourceListModel.removeElement(items[i]);
		}
		sourceList.getSelectionModel().clearSelection();
	}

	private void clearDestinationSelected() {
		Object selected[] = destList.getSelectedValues();
		for (int i = selected.length - 1; i >= 0; --i) {
			destListModel.removeElement(selected[i]);
		}
		destList.getSelectionModel().clearSelection();
	}

	private void clearDestination(Object items[]) {
		for (int i = items.length - 1; i >= 0; --i) {
			destListModel.removeElement(items[i]);
		}
		destList.getSelectionModel().clearSelection();
	}

	public JList getDestList() {
		return destList;
	}

	private void initScreen() {
		setBorder(BorderFactory.createEtchedBorder());
		setLayout(new GridBagLayout());
		sourceLabel = new JLabel(DEFAULT_SOURCE_CHOICE_LABEL);
		sourceListModel = new SortedListModel();
		sourceList = new JList(sourceListModel);
		add(sourceLabel, new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.CENTER, GridBagConstraints.NONE,
				CUSTOM_INSETS, 0, 0));
		add(new JScrollPane(sourceList), new GridBagConstraints(0, 1, 1, 5, .5, 1, GridBagConstraints.CENTER,
				GridBagConstraints.BOTH, CUSTOM_INSETS, 0, 0));

		createButton(ADD_BUTTON_LABEL, new AddListener(), 2);
		createButton(REMOVE_BUTTON_LABEL, new RemoveListener(), 3);
		createButton(ADD_ALL_BUTTON_LABEL, new AddAllListener(this), 4);
		createButton(REMOVE_ALL_BUTTON_LABEL, new RemoveAllListener(this), 5);

		destLabel = new JLabel(DEFAULT_DEST_CHOICE_LABEL);
		destListModel = new SortedListModel();
		destList = new JList(destListModel);
		add(destLabel, new GridBagConstraints(2, 0, 1, 1, 0, 0, GridBagConstraints.CENTER, GridBagConstraints.NONE,
				CUSTOM_INSETS, 0, 0));
		add(new JScrollPane(destList), new GridBagConstraints(2, 1, 1, 5, .5, 1.0, GridBagConstraints.CENTER,
				GridBagConstraints.BOTH, CUSTOM_INSETS, 0, 0));
	}

	private void createButton(String text, ActionListener listener, int gridy) {
		JButton btn = new JButton(text);
		btn.setPreferredSize(new Dimension(110, 28));
		add(btn, new GridBagConstraints(1, gridy, 1, 1, 0, .25, GridBagConstraints.CENTER, GridBagConstraints.NONE,
				CUSTOM_INSETS, 0, 0));
		btn.addActionListener(listener);
	}

	private class AddListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			Object selected[] = sourceList.getSelectedValues();
			addDestinationElements(selected);
			clearSourceSelected();
		}
	}

	private class AddAllListener implements ActionListener {
		DualListBox box;

		AddAllListener(DualListBox box) {
			this.box = box;
		}

		public void actionPerformed(ActionEvent e) {
			Object items[] = this.box.getAllSourceItems().toArray();
			addDestinationElements(items);
			clearSource(items);
		}
	}

	private class RemoveListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			Object selected[] = destList.getSelectedValues();
			addSourceElements(selected);
			clearDestinationSelected();
		}
	}

	private class RemoveAllListener implements ActionListener {
		DualListBox box;

		RemoveAllListener(DualListBox box) {
			this.box = box;
		}

		public void actionPerformed(ActionEvent e) {
			Object items[] = this.box.getAllResultItems().toArray();
			addSourceElements(items);
			clearDestination(items);
		}
	}
}

class SortedListModel extends AbstractListModel {

	SortedSet model;

	public SortedListModel() {
		model = new TreeSet();
	}

	public int getSize() {
		return model.size();
	}

	public Object getElementAt(int index) {
		return model.toArray()[index];
	}

	public void add(Object element) {
		if (model.add(element)) {
			fireContentsChanged(this, 0, getSize());
		}
	}

	public void addAll(Object elements[]) {
		Collection c = Arrays.asList(elements);
		model.addAll(c);
		fireContentsChanged(this, 0, getSize());
	}

	public void clear() {
		model.clear();
		fireContentsChanged(this, 0, getSize());
	}

	public boolean contains(Object element) {
		return model.contains(element);
	}

	public Object firstElement() {
		return model.first();
	}

	public Iterator iterator() {
		return model.iterator();
	}

	public Object lastElement() {
		return model.last();
	}

	public boolean removeElement(Object element) {
		boolean removed = model.remove(element);
		if (removed) {
			fireContentsChanged(this, 0, getSize());
		}
		return removed;
	}
}
