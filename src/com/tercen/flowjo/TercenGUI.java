package com.tercen.flowjo;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.List;

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

	private static final String channelsLabelLine1 = "FCS channels to be used by Tercen. Select multiple items by pressing the Shift";
	private static final String channelsLabelLine2 = "key or toggle items by holding the Ctrl (or Cmd) keys.";

	private static final String SAVE_UPLOAD_FIELDS_TEXT = "Save upload fields";
	private static final String ENABLE_UPLOAD_FIELDS_TEXT = "Enable upload fields";
	private static final String sampleLabel = "Upload Sample file (s)";
	private static final String sampleTooltip = "Should the Sample file (s) be uploaded?";
	private static final boolean fSample = false;
	private static final String browserLabel = "Open browser window to Tercen";
	private static final String browserTooltip = "Should a browser window be opened to see the uploaded files?";
	private static final boolean fBrowser = false;

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

		// show confirm dialog
		// List<Object> componentList = addHeaderComponents();
		List<Object> componentList = new ArrayList<>();

		FJLabel fjLabel1 = new FJLabel("Upload files to Tercen?");
		Font boldFont = new Font("Verdana", Font.BOLD, 12);
		fjLabel1.setFont(boldFont);
		componentList.add(fjLabel1);

		if (this.plugin.samplePops.size() > 0) {
			FJLabel label = new FJLabel("Populations in Data");
			fjLabel1.setFont(FontUtil.BoldDialog12);
			HBox box = new HBox(new Component[] { label, Box.createHorizontalGlue() });
			componentList.add(box);
			for (String path : this.plugin.samplePops) {
				FJLabel pathLabel = new FJLabel(path);
				pathLabel.setFont(FontUtil.dlog12);
				box = new HBox(new Component[] { Box.createRigidArea(new Dimension(10, 10)), pathLabel,
						Box.createHorizontalGlue() });
				componentList.add(box);
			}
			componentList.add(new JSeparator());
		}

		// upload properties
		FJCheckBox uploadCheckbox = createCheckbox(sampleLabel, sampleTooltip, fSample);
		componentList.add(new HBox(new Component[] { uploadCheckbox }));

		FJCheckBox browserFileCheckbox = createCheckbox(browserLabel, browserTooltip, fBrowser);
		componentList.add(browserFileCheckbox);

		componentList.add(new JSeparator());
		componentList.add(new FJLabel(channelsLabelLine1));
		componentList.add(new FJLabel(channelsLabelLine2));

		FJList paramList = createParameterList(arg1);
		componentList.add(new JScrollPane(paramList));
		componentList.add(new JSeparator());

		// Customize upload settings
		Component[] hostLabelField = createLabelTextFieldCombo("Host", plugin.hostName, plugin.hostName);
		Component[] teamLabelField = createLabelTextFieldCombo("Team", plugin.teamName, plugin.teamName);
		Component[] projectLabelField = createLabelTextFieldCombo("Project", plugin.projectName, plugin.projectName);
		Component[] domainLabelField = createLabelTextFieldCombo("Domain", plugin.domain, plugin.domain);
		Component[] userLabelField = createLabelTextFieldCombo("User", plugin.userName, plugin.userName);
		Component[] passwordLabelField = createLabelTextFieldCombo("Password", plugin.passWord, plugin.passWord);

		componentList.add(new HBox(hostLabelField));
		componentList.add(new HBox(teamLabelField));
		componentList.add(new HBox(projectLabelField));
		componentList.add(new HBox(domainLabelField));
		componentList.add(new HBox(userLabelField));
		componentList.add(new HBox(passwordLabelField));

		FJButton button = new FJButton();
		button.setText(ENABLE_UPLOAD_FIELDS_TEXT);
		button.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				((FJTextField) hostLabelField[1]).setEditable(!((FJTextField) hostLabelField[1]).isEditable());
				((FJTextField) teamLabelField[1]).setEditable(!((FJTextField) teamLabelField[1]).isEditable());
				((FJTextField) projectLabelField[1]).setEditable(!((FJTextField) projectLabelField[1]).isEditable());
				((FJTextField) domainLabelField[1]).setEditable(!((FJTextField) domainLabelField[1]).isEditable());
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

		// init components
		browserFileCheckbox.setEnabled(fSample);
		paramList.setEnabled(fSample);
		button.setEnabled(fSample);
		uploadCheckbox.addItemListener(new ItemListener() {
			public void itemStateChanged(ItemEvent e) {
				paramList.setEnabled(uploadCheckbox.isSelected());
				button.setEnabled(uploadCheckbox.isSelected());
				browserFileCheckbox.setEnabled(uploadCheckbox.isSelected());
				browserFileCheckbox.setSelected(uploadCheckbox.isSelected());
			}
		});

		int option = JOptionPane.showConfirmDialog((Component) null, componentList.toArray(),
				plugin.getName() + " " + plugin.getVersion(), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		if (option == JOptionPane.OK_OPTION) {
			plugin.upload = uploadCheckbox.isSelected();
			plugin.openBrowser = browserFileCheckbox.isSelected();
			plugin.hostName = ((FJTextField) hostLabelField[1]).getText();
			plugin.teamName = ((FJTextField) teamLabelField[1]).getText();
			plugin.projectName = ((FJTextField) projectLabelField[1]).getText();
			plugin.domain = ((FJTextField) domainLabelField[1]).getText();
			plugin.userName = ((FJTextField) userLabelField[1]).getText();
			plugin.passWord = String.valueOf(((JPasswordField) passwordLabelField[1]).getPassword());
			plugin.channels = new ArrayList<String>(paramList.getSelectedValuesList());
			if (plugin.upload) {
				plugin.pluginState = ImportPluginStateEnum.uploading;
			}
			result = true;
		} else {
			plugin.upload = false;
			result = false;
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

	private FJList createParameterList(List<String> parameters) {
		FJList paramList;
		DefaultListModel dlm = new DefaultListModel();
		for (int i = 0; i < parameters.size(); i++) {
			dlm.add(i, parameters.get(i));
		}
		paramList = new FJList(dlm);
		paramList.setSelectionMode(2);

		// TODO: dynamically set selected params?
		int[] indexes = new int[] {};
		paramList.setSelectedIndices(indexes);
		return paramList;
	}

//	private void addHeaderString(List<Component> result, String s) {
//		FJLabel txt = new FJLabel(s);
//		txt.setFont(FontUtil.dlogItal12);
//		result.add(txt);
//	}

//	private List<Object> addHeaderComponents(String headerText) {
//		List<Object> result = new ArrayList<>();
//		FJLabel txt = new FJLabel(headerText);
//		txt.setFont(FontUtil.dlogBold16);
//		result.add(txt);
//		addHeaderString(result, "TODO add explanation about first adding a file and after that uploading the added files");
//		result.add(new JSeparator());
//		return result;
//	}

}
