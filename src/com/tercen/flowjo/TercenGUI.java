package com.tercen.flowjo;

import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import javax.swing.Box;
import javax.swing.DefaultListModel;
import javax.swing.JEditorPane;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.tercen.client.impl.TercenClient;
import com.tercen.flowjo.Tercen.ImportPluginStateEnum;
import com.tercen.service.ServiceError;
import com.treestar.lib.FJPluginHelper;
import com.treestar.lib.gui.FJList;
import com.treestar.lib.gui.FontUtil;
import com.treestar.lib.gui.GuiFactory;
import com.treestar.lib.gui.HBox;
import com.treestar.lib.gui.panels.FJLabel;
import com.treestar.lib.gui.text.FJTextField;
import com.treestar.lib.xml.SElement;

public class TercenGUI {

	private static final Logger logger = LogManager.getLogger(TercenGUI.class);
	private static final String CHOOSE_DATA = "Choose Data";
	private static final String SELECT_CHANNELS = "Select FCS channels";
	private static final String SELECT_TEXT = "Hold Ctrl or Shift and use your mouse to select multiple.";

	private static final String CREATE_USER_TEXT = "Create Tercen User";

	private static final int fixedToolTipWidth = 300;
	private static final int fixedLabelWidth = 130;
	private static final int fixedFieldWidth = 250;
	private static final int fixedLabelHeigth = 25;
	private static final int fixedFieldHeigth = 25;

	private Tercen plugin;

	public TercenGUI(Tercen plugin) {
		this.plugin = plugin;
	}

	public boolean promptForOptions(SElement arg0, List<String> arg1) {
		boolean result;
		List<Object> componentList = new ArrayList<>();

		// show upload dialog
		if (this.plugin.pluginState == ImportPluginStateEnum.collectingSamples
				|| this.plugin.pluginState == ImportPluginStateEnum.uploaded
				|| this.plugin.pluginState == ImportPluginStateEnum.error) {
			componentList.add(addHeaderString("Upload to Tercen", FontUtil.dlogBold16));

			if (this.plugin.projectURL != null && !this.plugin.projectURL.equals("")) {
				JEditorPane pane = new JEditorPane();
				pane.setEditorKit(JEditorPane.createEditorKitForContentType("text/html"));
				pane.setEditable(false);
				pane.setText(String.format("<html><a href='%s'>Tercen Project</a></html>", this.plugin.projectURL));
				pane.setToolTipText("Go to the Tercen Project");
				pane.setBackground(UIManager.getColor("Panel.background"));
				pane.addHyperlinkListener(new HyperlinkListener() {
					@Override
					public void hyperlinkUpdate(HyperlinkEvent hle) {
						if (HyperlinkEvent.EventType.ACTIVATED.equals(hle.getEventType())) {
							Desktop desktop = Desktop.getDesktop();
							try {
								desktop.browse(hle.getURL().toURI());
							} catch (Exception ex) {
								logger.error(ex.getMessage());
							}
						}
					}
				});
				componentList.add(pane);
				componentList.add(new JSeparator());
			}

			FJList samplePopulationsList = null;
			if (this.plugin.samplePops.size() > 0) {
				FJLabel label = new FJLabel(CHOOSE_DATA);
				label.setFont(FontUtil.BoldDialog12);
				HBox box = new HBox(new Component[] { label, Box.createHorizontalGlue() });
				componentList.add(box);

				componentList.add(new FJLabel(""));
				componentList.add(new FJLabel(SELECT_TEXT));

				samplePopulationsList = createParameterList(new ArrayList<String>(this.plugin.samplePops), arg0, true);
				componentList.add(new JScrollPane(samplePopulationsList));
				componentList.add(new JSeparator());
			}

			// channels
			FJLabel label = new FJLabel(SELECT_CHANNELS);
			label.setFont(FontUtil.BoldDialog12);
			HBox box = new HBox(new Component[] { label, Box.createHorizontalGlue() });
			componentList.add(box);
			componentList.add(new FJLabel(SELECT_TEXT));

			FJList paramList = createParameterList(arg1, arg0, false);
			componentList.add(new JScrollPane(paramList));
			componentList.add(new JSeparator());

			int option = JOptionPane.showConfirmDialog((Component) null, componentList.toArray(),
					plugin.getName() + " " + plugin.getVersion(), JOptionPane.OK_CANCEL_OPTION,
					JOptionPane.PLAIN_MESSAGE);
			if (option == JOptionPane.OK_OPTION) {
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

	public Map<String, Object> createUser(TercenClient client, String emailAddress) {
		Map<String, Object> result = new HashMap<String, Object>();
		List<Object> componentList = new ArrayList<>();

		// show create user dialog
		if (emailAddress != null) {
			componentList.add(addHeaderString(CREATE_USER_TEXT, FontUtil.dlogBold16));

			String userName = emailAddress.substring(0, emailAddress.indexOf("@"));
			Component[] userLabelField = createLabelTextFieldCombo("Username", userName, userName, true);
			Component[] emailLabelField = createLabelTextFieldCombo("Email", emailAddress, emailAddress, true);
			Component[] passwordLabelField = createLabelTextFieldCombo("Password", "", "", true);

			componentList.add(new HBox(userLabelField));
			componentList.add(new HBox(emailLabelField));
			componentList.add(new HBox(passwordLabelField));

			int option = JOptionPane.showConfirmDialog((Component) null, componentList.toArray(),
					plugin.getName() + " " + plugin.getVersion(), JOptionPane.OK_CANCEL_OPTION,
					JOptionPane.PLAIN_MESSAGE);
			if (option == JOptionPane.OK_OPTION) {
				userName = ((FJTextField) userLabelField[1]).getText();
				emailAddress = ((FJTextField) emailLabelField[1]).getText();
				String passWord = String.valueOf(((JPasswordField) passwordLabelField[1]).getPassword());
				try {
					plugin.session = Utils.createTercenUser(client, userName, emailAddress, passWord);
					result.put("pwd", passWord);
					result.put("token", plugin.session.token.token);
				} catch (ServiceError e) {
					// TODO inform user -> retry?
					e.printStackTrace();
				}
			}
		}
		return result;
	}

	public String getTercenPassword(String userNameOrEmail) {
		String result = null;
		List<Object> componentList = new ArrayList<>();

		componentList.add(addHeaderString("Tercen Authentication", FontUtil.dlogBold16));

		Component[] emailLabelField = createLabelTextFieldCombo("Email", userNameOrEmail, userNameOrEmail, false);
		Component[] passwordLabelField = createLabelTextFieldCombo("Password", "", "", true);
		componentList.add(new HBox(emailLabelField));
		componentList.add(new HBox(passwordLabelField));

		int option = JOptionPane.showConfirmDialog((Component) null, componentList.toArray(),
				plugin.getName() + " " + plugin.getVersion(), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		if (option == JOptionPane.OK_OPTION) {
			result = String.valueOf(((JPasswordField) passwordLabelField[1]).getPassword());
		}
		return result;
	}

	private Component[] createLabelTextFieldCombo(String labelText, String fieldValue, String fieldTooltip,
			boolean editable) {
		FJLabel label = new FJLabel(labelText);
		JTextField field;
		if (labelText.equals("Password")) {
			field = new JPasswordField();
		} else {
			field = new FJTextField();
		}
		field.setText(fieldValue);
		field.setEditable(editable);
		field.setToolTipText("<html><p width=\"" + fixedToolTipWidth + "\">" + fieldTooltip + "</p></html>");
		GuiFactory.setSizes(field, new Dimension(fixedFieldWidth, fixedFieldHeigth));
		GuiFactory.setSizes(label, new Dimension(fixedLabelWidth, fixedLabelHeigth));
		return new Component[] { label, field };
	}

	private FJList createParameterList(List<String> parameters, SElement sElement, boolean selectAll) {
		FJList paramList;
		DefaultListModel dlm = new DefaultListModel();
		for (int i = 0; i < parameters.size(); i++) {
			dlm.add(i, parameters.get(i));
		}
		paramList = new FJList(dlm);
		paramList.setSelectionMode(2);

		int[] indexes;
		if (selectAll) {
			indexes = IntStream.range(0, parameters.size()).toArray();
		} else {
			List<String> compParamNames = FJPluginHelper.getSample(sElement).getParameters(true)
					.getCompensatedParameterNames();
			indexes = compParamNames.stream().mapToInt(s -> parameters.indexOf(s)).toArray();
		}
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
