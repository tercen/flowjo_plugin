package com.tercen.flowjo;

import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.swing.Box;
import javax.swing.DefaultListModel;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.tercen.client.impl.TercenClient;
import com.tercen.flowjo.Tercen.ImportPluginStateEnum;
import com.tercen.model.impl.UserSession;
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

	private static final Logger logger = LogManager.getLogger();
	private static final String CHOOSE_DATA = "Choose Data";
	private static final String SELECT_CHANNELS = "Select FCS channels.";
	private static final String SELECT_TEXT = "Hold Ctrl or Shift and use your mouse to select multiple.";
	private static final String CREATE_USER_TITLE_TEXT = "We're creating your Tercen account.";
	private static final String CREATE_USER_SUBTITLE_TEXT = "Please verify your details and create a password for Tercen.";

	private static final int fixedToolTipWidth = 300;
	private static final int fixedLabelWidth = 130;
	private static final int fixedFieldWidth = 250;
	private static final int fixedLabelHeigth = 25;
	private static final int fixedFieldHeigth = 25;

	private Tercen plugin;
	private List<Object> componentList = new ArrayList<>();

	public TercenGUI(Tercen plugin) {
		this.plugin = plugin;
	}

	public boolean promptForOptions(SElement arg0, List<String> arg1) {
		boolean result = false;

		// show upload dialog
		if (this.plugin.pluginState == ImportPluginStateEnum.collectingSamples
				|| this.plugin.pluginState == ImportPluginStateEnum.uploading
				|| this.plugin.pluginState == ImportPluginStateEnum.uploaded
				|| this.plugin.pluginState == ImportPluginStateEnum.error) {

			if (this.plugin.projectURL != null && !this.plugin.projectURL.equals("")) {
				String[] buttons = { "Return", "Upload Changes", "Import" };
				componentList.clear();
				FJLabel headerLabel = addHeaderString("Welcome Back", FontUtil.dlogBold16);
				FJLabel subHeaderLabel = new FJLabel("What's next for your data? Choose an option.");
				headerLabel.setHorizontalAlignment(SwingConstants.CENTER);
				subHeaderLabel.setHorizontalAlignment(SwingConstants.CENTER);
				componentList.add(headerLabel);
				componentList.add(new FJLabel(""));
				componentList.add(subHeaderLabel);
				componentList
						.add("<html><ul>" + "<li>Return - Open Tercen to resume working on my analysis project.</li>"
								+ "<li>Upload Changes - Create a new project with my recent changes.</li>"
								+ "<li>Import - Load a Tercen export file into this workspace.</li>" + "</ul></html>");
				componentList.add(new FJLabel(""));
				componentList.add(new JSeparator());
				int returnValue = JOptionPane.showOptionDialog(null, componentList.toArray(), getDialogTitle(),
						JOptionPane.DEFAULT_OPTION, -1, null, buttons, buttons[2]);

				if (returnValue == 0) {
					String url = plugin.projectURL;
					Desktop desktop = java.awt.Desktop.getDesktop();
					URI uri;
					try {
						uri = new URI(String.valueOf(url));
						desktop.browse(uri);
					} catch (URISyntaxException e) {
						Utils.showWarningDialog("Error while opening projectURL:" + e.getMessage());
					} catch (IOException e) {
						Utils.showWarningDialog("Error while opening projectURL:" + e.getMessage());
					}
				} else if (returnValue == 1) {
					componentList.clear();
					componentList.add(addHeaderString("Re-Upload Data", FontUtil.dlogBold16));
					componentList.add(new JSeparator());
					result = openUploadDialog(componentList, arg0, arg1);
				} else if (returnValue == 2) {
					JFileChooser fileChooser = new JFileChooser();
					if (Utils.isWindows()) {
						String homeDir = System.getProperty("user.home");
						if (homeDir != null && homeDir != "") {
							File downloadDir = new File(homeDir + "/Downloads/");
							if (downloadDir.exists()) {
								fileChooser.setCurrentDirectory(downloadDir);
							}
						}
					}
					int option = fileChooser.showOpenDialog(null);
					if (option == JFileChooser.APPROVE_OPTION) {
						plugin.importFile = fileChooser.getSelectedFile();
						plugin.pluginState = ImportPluginStateEnum.importing;
						result = true;
					}
				}
			} else {
				componentList.clear();
				componentList.add(addHeaderString("Upload to Tercen", FontUtil.dlogBold16));
				componentList.add(new JSeparator());
				result = openUploadDialog(componentList, arg0, arg1);
			}
		} else {
			FJLabel headerLabel = addHeaderString("<html><center>Instructions</center><html>", FontUtil.dlogBold16);
			headerLabel.setHorizontalAlignment(SwingConstants.CENTER);
			componentList.clear();
			componentList.add(headerLabel);
			componentList.addAll(addHeaderComponents());

			int option = JOptionPane.showConfirmDialog((Component) null, componentList.toArray(), getDialogTitle(),
					JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
			if (option == JOptionPane.OK_OPTION) {
				result = true;
			} else {
				result = false;
			}
		}
		return result;
	}

	private boolean openUploadDialog(List<Object> componentList, SElement arg0, List<String> arg1) {
		boolean result = false;
		FJList samplePopulationsList = null;
		if (this.plugin.samplePops.size() > 0) {
			FJLabel label = new FJLabel(CHOOSE_DATA);
			label.setFont(FontUtil.BoldDialog12);
			HBox box = new HBox(new Component[] { label, Box.createHorizontalGlue() });
			componentList.add(box);

			componentList.add(new FJLabel(""));
			componentList.add(new FJLabel(SELECT_TEXT));

			samplePopulationsList = createParameterList(new ArrayList<String>(this.plugin.samplePops), arg0, false);
			componentList.add(new JScrollPane(samplePopulationsList));
			componentList.add(new JSeparator());
		}

		// channel section
		FJLabel label = new FJLabel(SELECT_CHANNELS);
		label.setFont(FontUtil.BoldDialog12);
		HBox box = new HBox(new Component[] { label, Box.createHorizontalGlue() });
		componentList.add(box);

		componentList.add(new FJLabel(""));
		componentList.add(new FJLabel(SELECT_TEXT));
		componentList.add(new FJLabel(""));

		List<String> compParams = FJPluginHelper.getSample(arg0).getParameters(true).getCompensatedParameterNames();
		DualListBox dualListBox = new DualListBox(arg1, compParams);
		componentList.add(dualListBox);

		int option = JOptionPane.showConfirmDialog((Component) null, componentList.toArray(), getDialogTitle(),
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

		if (option == JOptionPane.OK_OPTION) {
			List<String> fcsChannels = dualListBox.getAllResultItems();
			plugin.channels = new ArrayList<String>(
					fcsChannels.stream().map(s -> Utils.setColumnName(s)).collect(Collectors.toList()));

			// set selected sample files
			if (samplePopulationsList != null) {
				plugin.selectedSamplePops.clear();
				plugin.selectedSamplePops.addAll(samplePopulationsList.getSelectedValuesList());
			}
			plugin.pluginState = ImportPluginStateEnum.uploading;

			result = true;
		} else {
			result = false;
		}
		return result;
	}

	private String getDialogTitle() {
		return "Tercen Plugin V" + plugin.getVersion();
	}

	public Map<String, Object> createUser(TercenClient client, String emailAddress) {
		Map<String, Object> result = new HashMap<String, Object>();
		List<Object> componentList = new ArrayList<>();

		// show create user dialog
		if (emailAddress != null) {
			componentList.add(addHeaderString(CREATE_USER_TITLE_TEXT, FontUtil.dlogBold16));
			FJLabel subTitleLabel = new FJLabel(CREATE_USER_SUBTITLE_TEXT);
			subTitleLabel.setFont(FontUtil.BoldDialog12);
			componentList.add(subTitleLabel);
			componentList.add(new FJLabel("<html><br/></html>"));

			String userName = emailAddress.substring(0, emailAddress.indexOf("@"));
			Component[] userLabelField = createLabelTextFieldCombo("Username", userName, userName, true,
					FontUtil.dlog12);
			Component[] emailLabelField = createLabelTextFieldCombo("Email", emailAddress, emailAddress, true,
					FontUtil.dlog12);
			Component[] passwordLabelField = createLabelTextFieldCombo("Password", "", "", true, FontUtil.dlog12);

			componentList.add(new HBox(userLabelField));
			componentList.add(new HBox(emailLabelField));
			componentList.add(new HBox(passwordLabelField));
			componentList.add(new FJLabel("<html><p/></html>"));

			FJLabel licenseLabel = new FJLabel("Tercen Licence");
			licenseLabel.setFont(FontUtil.BoldDialog12);
			componentList.add(licenseLabel);
			JEditorPane pane = createPaneWithLink(false, false);
			pane.setText(
					"<html><div style='font-size: 12; font-family: Dialog'>By clicking OK you agree to upload under our standard terms and conditions.<br/>"
							+ "Click the links to find out more about Tercen <a href='https://www.tercen.com/terms-of-service'>Terms of Service</a>"
							+ " and <a href='https://www.tercen.com/privacy-policy'>Privacy Policy</a>.<br/>If you have any questions contact "
							+ "<b>support@tercen.com</b> and we will be happy to answer them.</div></html>");
			componentList.add(pane);

			int option = JOptionPane.showConfirmDialog((Component) null, componentList.toArray(), getDialogTitle(),
					JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
			if (option == JOptionPane.OK_OPTION) {
				userName = ((FJTextField) userLabelField[1]).getText();
				emailAddress = ((FJTextField) emailLabelField[1]).getText();
				String passWord = String.valueOf(((JPasswordField) passwordLabelField[1]).getPassword());
				try {
					plugin.session = Utils.createTercenUser(client, userName, emailAddress, passWord);
					result.put("pwd", passWord);
					result.put("token", plugin.session.token.token);
				} catch (ServiceError e) {
					String userMessage = getFailedUserMessage(userName, e);
					JOptionPane optionPane = new JOptionPane(userMessage, JOptionPane.ERROR_MESSAGE);
					JDialog dialog = optionPane.createDialog("Failure");
					dialog.setAlwaysOnTop(true);
					dialog.setVisible(true);
					createUser(client, emailAddress);
				}
			}
		}
		return result;
	}

	private String getFailedUserMessage(String userName, ServiceError e) {
		String result = e.getMessage();
		if (result.contains("user.create.password.required")) {
			result = "Oops, you forgot to make a password. Can you try again?";
		} else if (result.contains("user.create.username.not.valid")) {
			result = String.format(
					"Oops, we can't create '%s'. We can only use letters and numbers for User Names. Can you try again?",
					userName);
		} else if (result.contains("user.create.username.not.available")) {
			result = "Oops, this User Name already exists. Looks like somebody got there before you. Can you try again?";
		}
		return result;
	}

	public String getTercenPassword(TercenClient client, String email) {
		String result = null;
		List<Object> componentList = new ArrayList<>();
		componentList.add(addHeaderString("Tercen Authentication", FontUtil.dlogBold16));
		Component[] emailLabelField = createLabelLabelCombo("Email", email);
		Component[] passwordLabelField = createLabelTextFieldCombo("Password", "", "", true);
		componentList.add(new HBox(emailLabelField));
		componentList.add(new HBox(passwordLabelField));
		String[] buttons = { "OK", "Cancel", "Reset password" };
		int option = JOptionPane.showOptionDialog(null, componentList.toArray(), getDialogTitle(),
				JOptionPane.DEFAULT_OPTION, -1, null, buttons, buttons[0]);
		if (option == JOptionPane.OK_OPTION) {
			result = String.valueOf(((JPasswordField) passwordLabelField[1]).getPassword());
		} else if (option == 2) {
			try {
				logger.info("Requesting new password for:" + email);
				client.userService.sendResetPasswordEmail(email);
				Utils.showInfoDialog("Your password reset email has been sent.");
			} catch (ServiceError e) {
				Utils.showErrorDialog("Error while requesting new password, please try again.");
			}
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

	private Component[] createLabelLabelCombo(String labelText, String labelValueText) {
		FJLabel label = new FJLabel(labelText);
		FJLabel labelValue = new FJLabel(labelValueText);
		GuiFactory.setSizes(label, new Dimension(fixedLabelWidth, fixedLabelHeigth));
		GuiFactory.setSizes(labelValue, new Dimension(fixedFieldWidth, fixedFieldHeigth));
		return new Component[] { label, labelValue };
	}

	private Component[] createLabelTextFieldCombo(String labelText, String fieldValue, String fieldTooltip,
			boolean editable, Font font) {
		Component[] result = createLabelTextFieldCombo(labelText, fieldValue, fieldTooltip, editable);
		result[0].setFont(font);
		return result;
	}

	private FJList createParameterList(List<String> parameters, SElement sElement, boolean filterCompensated) {
		FJList paramList;
		DefaultListModel dlm = new DefaultListModel();

		if (filterCompensated) {
			parameters = FJPluginHelper.getSample(sElement).getParameters(true).getCompensatedParameterNames();
		}
		int[] indexes = IntStream.range(0, parameters.size()).toArray();
		for (int i = 0; i < parameters.size(); i++) {
			dlm.add(i, parameters.get(i));
		}
		paramList = new FJList(dlm);
		paramList.setSelectionMode(2);
		paramList.setSelectedIndices(indexes);
		return paramList;
	}

	private FJLabel addHeaderString(String s, Font font) {
		FJLabel label = new FJLabel(s);
		label.setFont(font);
		return label;
	}

	private List<Object> addHeaderComponents() {
		List<Object> result = new ArrayList<>();
		result.add("<html><ul>" + "<li>Press Ok to attach the Tercen Connector to your selected population.</li>"
				+ "<li>Drag and Drop the Tercen connector to any other populations you wish to upload.</li>"
				+ "<li>Double Click any Tercen Connector line to open the Uploader.</li>" + "</ul></html>");
		JEditorPane pane = createPaneWithLink(false, false);
		pane.setText("<html><center><div style='font-size: 12; font-family: Dialog; margin-bottom: 5px;'>"
				+ "<a href='https://app.intercom.com/a/apps/fvhxgh49/articles/articles/5880883/show'>Learn more</a></div></center></html>");
		result.add(pane);
		result.add(new JSeparator());
		return result;
	}

	private JEditorPane createPaneWithLink(boolean hideParentOnClick, boolean addToken) {
		JEditorPane pane = new JEditorPane();
		pane.setEditorKit(JEditorPane.createEditorKitForContentType("text/html"));
		pane.setEditable(false);
		pane.setBackground(UIManager.getColor("Panel.background"));
		Insets margin = pane.getMargin();
		pane.setMargin(new Insets(margin.top, 0, margin.bottom, margin.right));
		pane.addHyperlinkListener(new HyperlinkListener() {
			@Override
			public void hyperlinkUpdate(HyperlinkEvent hle) {
				if (HyperlinkEvent.EventType.ACTIVATED.equals(hle.getEventType())) {
					Desktop desktop = Desktop.getDesktop();
					try {
						URI uri = hle.getURL().toURI();
						if (addToken) {
							TercenClient client = new TercenClient(plugin.hostName);
							UserSession session = Utils.getAndExtendTercenSession(client, plugin.gui, plugin.passWord);
							if (session != null) {
								client.httpClient.setAuthorization(session.token.token);
								uri = new URI(hle.getURL().toString() + Utils.addToken(client, session.user.id, true));
							}
						}
						desktop.browse(uri);
						if (hideParentOnClick) {
							JOptionPane.getRootFrame().dispose();
						}
					} catch (Exception ex) {
						logger.error(ex.getMessage());
					}
				}
			}
		});
		return pane;
	}
}
