package com.tercen.flowjo;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import javax.swing.Box;
import javax.swing.DefaultListModel;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextField;

import com.tercen.flowjo.Tercen.ImportPluginStateEnum;
import com.treestar.lib.gui.FJButton;
import com.treestar.lib.gui.FJList;
import com.treestar.lib.gui.FontUtil;
import com.treestar.lib.gui.GuiFactory;
import com.treestar.lib.gui.HBox;
import com.treestar.lib.gui.panels.FJLabel;
import com.treestar.lib.gui.swing.FJCheckBox;
import com.treestar.lib.gui.text.FJTextField;
import com.treestar.lib.xml.SElement;

public class TercenGUI {

	private static final String CHOOSE_DATA = "Choose Data";
	private static final String SELECT_CHANNELS = "Select FCS channels";
	private static final String SELECT_TEXT = "Hold Ctrl or Shift and use your mouse to select multiple.";

	private static final String SAVE_UPLOAD_FIELDS_TEXT = "Save upload fields";
	private static final String ENABLE_UPLOAD_FIELDS_TEXT = "Enable upload fields";

	private static final int fixedToolTipWidth = 300;
	private static final int fixedLabelWidth = 130;
	private static final int fixedFieldWidth = 250;
	private static final int fixedLabelHeigth = 25;
	private static final int fixedFieldHeigth = 25;

	private Tercen plugin;

	public TercenGUI(Tercen importPlugin) {
		this.plugin = importPlugin;
	}

	public boolean promptForOptions(SElement arg0, List<String> arg1) {
		boolean result;
		List<Object> componentList = new ArrayList<>();

		// show upload dialog
		if (this.plugin.pluginState == ImportPluginStateEnum.collectingSamples
				|| this.plugin.pluginState == ImportPluginStateEnum.uploaded
				|| this.plugin.pluginState == ImportPluginStateEnum.error) {
			componentList.add(addHeaderString("Upload to Tercen", FontUtil.dlogBold16));

			FJList samplePopulationsList = null;
			if (this.plugin.samplePops.size() > 0) {
				FJLabel label = new FJLabel(CHOOSE_DATA);
				label.setFont(FontUtil.BoldDialog12);
				HBox box = new HBox(new Component[] { label, Box.createHorizontalGlue() });
				componentList.add(box);

				componentList.add(new FJLabel(""));
				componentList.add(new FJLabel(SELECT_TEXT));

				samplePopulationsList = createParameterList(new ArrayList<String>(this.plugin.samplePops), true);
				componentList.add(new JScrollPane(samplePopulationsList));
				componentList.add(new JSeparator());
			}

			// channels
			FJLabel label = new FJLabel(SELECT_CHANNELS);
			label.setFont(FontUtil.BoldDialog12);
			HBox box = new HBox(new Component[] { label, Box.createHorizontalGlue() });
			componentList.add(box);
			componentList.add(new FJLabel(SELECT_TEXT));

			FJList paramList = createParameterList(arg1, false);
			componentList.add(new JScrollPane(paramList));
			componentList.add(new JSeparator());

			// Customize upload settings
			Component[] hostLabelField = createLabelTextFieldCombo("Host", plugin.hostName, plugin.hostName);
			Component[] teamLabelField = createLabelTextFieldCombo("Team", plugin.teamName, plugin.teamName);
			Component[] userLabelField = createLabelTextFieldCombo("User", plugin.userName, plugin.userName);
			Component[] passwordLabelField = createLabelTextFieldCombo("Password", plugin.passWord, plugin.passWord);

			componentList.add(new HBox(hostLabelField));
			componentList.add(new HBox(teamLabelField));
			componentList.add(new HBox(userLabelField));
			componentList.add(new HBox(passwordLabelField));

			FJButton button = new FJButton();
			button.setText(ENABLE_UPLOAD_FIELDS_TEXT);
			button.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					((FJTextField) hostLabelField[1]).setEditable(!((FJTextField) hostLabelField[1]).isEditable());
					((FJTextField) teamLabelField[1]).setEditable(!((FJTextField) teamLabelField[1]).isEditable());
					((FJTextField) userLabelField[1]).setEditable(!((FJTextField) userLabelField[1]).isEditable());
					((JPasswordField) passwordLabelField[1])
							.setEditable(!((JPasswordField) passwordLabelField[1]).isEditable());
					if (button.getText() == ENABLE_UPLOAD_FIELDS_TEXT) {
						button.setText(SAVE_UPLOAD_FIELDS_TEXT);
					} else {
						button.setText(ENABLE_UPLOAD_FIELDS_TEXT);
					}
				}
			});
			componentList.add(button);

			int option = JOptionPane.showConfirmDialog((Component) null, componentList.toArray(),
					plugin.getName() + " " + plugin.getVersion(), JOptionPane.OK_CANCEL_OPTION,
					JOptionPane.PLAIN_MESSAGE);
			if (option == JOptionPane.OK_OPTION) {
				plugin.hostName = ((FJTextField) hostLabelField[1]).getText();
				plugin.teamName = ((FJTextField) teamLabelField[1]).getText();
				plugin.userName = ((FJTextField) userLabelField[1]).getText();
				plugin.passWord = String.valueOf(((JPasswordField) passwordLabelField[1]).getPassword());
				plugin.channels = new ArrayList<String>(paramList.getSelectedValuesList());
				// set selected sample files
				if (samplePopulationsList != null) {
					plugin.selectedSamplePops.addAll(samplePopulationsList.getSelectedValuesList());
				}
				plugin.pluginState = ImportPluginStateEnum.uploading;
				result = true;
			} else {
				result = false;
			}
		} else {

			componentList.add(addHeaderString("Tercen Plugin Instructions", FontUtil.dlogBold16));
			componentList.addAll(addHeaderComponents());

			int option = JOptionPane.showConfirmDialog((Component) null, componentList.toArray(),
					plugin.getName() + " " + plugin.getVersion(), JOptionPane.OK_CANCEL_OPTION,
					JOptionPane.PLAIN_MESSAGE);
			if (option == JOptionPane.OK_OPTION) {
				result = true;
			} else {
				result = false;
			}
		}
		return result;
	}

	private FJCheckBox createCheckbox(String label, String tooltip, boolean selected) {
		FJCheckBox checkbox = new FJCheckBox(label);
		checkbox.setToolTipText("<html><p width=\"" + fixedToolTipWidth + "\">" + tooltip + "</p></html>");
		checkbox.setSelected(selected);
		return checkbox;
	}

	private Component[] createLabelTextFieldCombo(String labelText, String fieldValue, String fieldTooltip) {
		FJLabel label = new FJLabel(labelText);
		JTextField field;
		if (labelText.equals("Password")) {
			field = new JPasswordField();
		} else {
			field = new FJTextField();
		}
		field.setText(fieldValue);
		field.setEditable(false);
		field.setToolTipText("<html><p width=\"" + fixedToolTipWidth + "\">" + fieldTooltip + "</p></html>");
		GuiFactory.setSizes(field, new Dimension(fixedFieldWidth, fixedFieldHeigth));
		GuiFactory.setSizes(label, new Dimension(fixedLabelWidth, fixedLabelHeigth));
		return new Component[] { label, field };
	}

	private FJList createParameterList(List<String> parameters, boolean selectAll) {
		FJList paramList;
		DefaultListModel dlm = new DefaultListModel();
		for (int i = 0; i < parameters.size(); i++) {
			dlm.add(i, parameters.get(i));
		}
		paramList = new FJList(dlm);
		paramList.setSelectionMode(2);
		int[] indexes = selectAll ? IntStream.range(0, parameters.size()).toArray() : new int[] {};
		paramList.setSelectedIndices(indexes);
		return paramList;
	}

	private FJLabel addHeaderString(String s, Font font) {
		FJLabel txt = new FJLabel(s);
		txt.setFont(font);
		return txt;
	}

	private FJLabel addHeaderString(String s) {
		return addHeaderString(s, FontUtil.dlogItal12);
	}

	private List<Object> addHeaderComponents() {
		List<Object> result = new ArrayList<>();
		result.add("");
		result.add("Press Ok to begin.");
		result.add("A Tercen connector will be attached to your data selection.");
		result.add("Drag and Drop the Tercen connector to any other files (or gates) you wish to upload.");
		result.add("Double Click any Tercen connector line to open the Tercen uploader.");
		result.add(new JSeparator());
		return result;
	}

}
