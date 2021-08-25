package com.tercen;

import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Properties;

import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;

import com.tercen.client.impl.TercenClient;
import com.tercen.model.impl.Project;
import com.tercen.model.impl.Schema;
import com.tercen.service.ServiceError;
import com.treestar.flowjo.core.Sample;
import com.treestar.lib.FJPluginHelper;
import com.treestar.lib.core.ExportFileTypes;
import com.treestar.lib.core.ExternalAlgorithmResults;
import com.treestar.lib.core.PopulationPluginInterface;
import com.treestar.lib.file.FJFileRef;
import com.treestar.lib.gui.FJButton;
import com.treestar.lib.gui.FJList;
import com.treestar.lib.gui.GuiFactory;
import com.treestar.lib.gui.HBox;
import com.treestar.lib.gui.panels.FJLabel;
import com.treestar.lib.gui.swing.FJCheckBox;
import com.treestar.lib.gui.text.FJTextField;
import com.treestar.lib.xml.SElement;

public class ImportPlugin implements PopulationPluginInterface {

	private static final String pluginName = "TercenImportPlugin";
	private static final String version = "1.0";

	// default settings
	private static final String HOSTNAME_URL = "http://10.0.2.2:5402/";
	private static final String TEAM_NAME = "test-team";
	private static final String PROJECT_NAME = "myproject";
	private static final String DOMAIN = "tercen";
	private static final String USERNAME = "test";
	private static final String PASSWORD = "test";

	private static final int fixedToolTipWidth = 300;
	private static final int fixedLabelWidth = 130;
	private static final int fixedFieldWidth = 250;
	private static final int fixedLabelHeigth = 25;
	private static final int fixedFieldHeigth = 25;

	private static final String channelsLabelLine1 = "FCS channels to be used by Tercen. Select multiple items by pressing the Shift";
	private static final String channelsLabelLine2 = "key or toggle items by holding the Ctrl (or Cmd) keys.";

	private static final String SAVE_UPLOAD_FIELDS_TEXT = "Save upload fields";
	private static final String ENABLE_UPLOAD_FIELDS_TEXT = "Enable upload fields";
	private static final String Failed = "Failed";
	private static final String sampleLabel = "Upload Sample FCS file";
	private static final String sampleTooltip = "Should the FCS file be uploaded?";
	private static final boolean fSample = false;
	private static final String csvLabel = "Upload CSV file";
	private static final String csvTooltip = "Should the CSV file be uploaded?";
	private static final boolean fCsv = true;
	private static final String browserLabel = "Open browser window to Tercen";
	private static final String browserTooltip = "Should a browser window be opened to see the uploaded files?";
	private static final boolean fBrowser = true;

	private String hostName = HOSTNAME_URL;
	private String teamName = TEAM_NAME;
	private String projectName = PROJECT_NAME;
	private String domain = DOMAIN;
	private String userName = USERNAME;
	private String passWord = PASSWORD;
	private boolean upload;
	private boolean uploadFCS;
	private boolean uploadCSV;
	private boolean openBrowser;
	private ArrayList<String> channels = new ArrayList<String>();

	@Override
	public SElement getElement() {
		SElement result = new SElement(pluginName);
		result.setBool("upload", upload);
		result.setBool("uploadFCS", uploadFCS);
		result.setBool("uploadCSV", uploadCSV);
		result.setBool("openBrowser", openBrowser);
		result.setString("host", hostName);
		result.setString("team", teamName);
		result.setString("project", projectName);
		result.setString("domain", domain);
		result.setString("user", userName);
		result.setString("pwd", passWord);
		result.setString("channels", String.join(",", channels));
		return result;
	}

	@Override
	public Icon getIcon() {
		return null;
	}

	@Override
	public String getName() {
		return pluginName;
	}

	@Override
	public List<String> getParameters() {
		return new ArrayList<String>();
	}

	@Override
	public String getVersion() {
		return version;
	}

	@Override
	public ExternalAlgorithmResults invokeAlgorithm(SElement fcmlQueryElement, File sampleFile, File outputFolder) {
		ExternalAlgorithmResults result = new ExternalAlgorithmResults();

		try {
			if (upload) {
				if (!sampleFile.exists()) {
					JOptionPane.showMessageDialog(null, "Input file does not exist", "ImportPlugin error",
							JOptionPane.ERROR_MESSAGE);
					result.setWorkspaceString(ImportPlugin.Failed);
				} else {
					Schema uploadResult = null;
					TercenClient client = null;
					Project project = null;

					// create client and get project (will be created if it doesn't exist)
					if (uploadCSV || uploadFCS) {
						List<Object> clientProject = Utils.getClientAndProject(hostName, teamName, projectName, domain,
								userName, passWord);
						client = (TercenClient) clientProject.get(0);
						project = (Project) clientProject.get(1);
					}

					// upload csv file
					if (uploadCSV) {
						String fileName = sampleFile.getPath();
						uploadResult = Utils.uploadCsvFile(client, project, fileName, channels);
					}

					// upload fcs-zip file
					if (uploadFCS) {
						Sample sample = FJPluginHelper.getSample(fcmlQueryElement);
						FJFileRef fileRef = sample.getFileRef();
						String fileName = fileRef.getLocalFilepath();
						LinkedHashMap map = Utils.uploadZipFile(client, project, fileName);
						result.setWorkspaceString(map.toString());
					}

					// open browser
					if (openBrowser) {
						if (uploadResult != null) {
							String url = Utils.getTercenProjectURL(hostName, teamName, uploadResult);
							Desktop desktop = java.awt.Desktop.getDesktop();
							URI uri = new URI(String.valueOf(url));
							desktop.browse(uri);

						} else {
							JOptionPane.showMessageDialog(null,
									"No files have been uploaded, browser window will not open.",
									"ImportPlugin warning", JOptionPane.WARNING_MESSAGE);
						}
					}
					// set status for user
					if (uploadCSV && uploadFCS) {
						result.setWorkspaceString(
								String.format("FCS and CSV file have been uploaded to %s.", hostName));
					} else if (uploadCSV && !uploadFCS) {
						result.setWorkspaceString(String.format("CSV file has been uploaded to %s.", hostName));
					} else if (!uploadCSV && uploadFCS) {
						result.setWorkspaceString(String.format("FCS file has been uploaded to %s.", hostName));
					} else {
						result.setWorkspaceString(String.format("File have not been uploaded to %s.", hostName));
					}
				}
			} else {
				result.setWorkspaceString(String.format("Files have not been uploaded to %s.", hostName));
			}
		} catch (ServiceError e) {
			e.printStackTrace();
			result.setWorkspaceString(e.toString());
		} catch (IOException e) {
			e.printStackTrace();
			result.setWorkspaceString(e.getMessage());
		} catch (URISyntaxException e) {
			e.printStackTrace();
			result.setWorkspaceString(e.getMessage());
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
		FJTextField field = new FJTextField();
		field.setText(fieldValue);
		field.setEditable(false);
		field.setToolTipText("<html><p width=\"" + fixedToolTipWidth + "\">" + fieldTooltip + "</p></html>");
		GuiFactory.setSizes(field, new Dimension(fixedFieldWidth, fixedFieldHeigth));
		GuiFactory.setSizes(label, new Dimension(fixedLabelWidth, fixedLabelHeigth));
		return new Component[] { label, field };
	}

	private void readPropertiesFile() {
		Properties prop = new Properties();
		File jarfile = new File(ImportPlugin.class.getProtectionDomain().getCodeSource().getLocation().getPath());
		String propertyFilePath = jarfile.getParent() + File.separator + "tercen.properties";
		try {
			prop.load(new BufferedReader(new InputStreamReader(new FileInputStream(propertyFilePath))));
			hostName = prop.getProperty("host");
			teamName = prop.getProperty("team");
			projectName = prop.getProperty("project");
			domain = prop.getProperty("domain");
			userName = prop.getProperty("user");
			passWord = prop.getProperty("password");
		} catch (IOException e) {
			e.printStackTrace();
			// some error reading properties file, use default settings
		}
	}

	@Override
	public boolean promptForOptions(SElement arg0, List<String> arg1) {
		// read tercen upload properties
		readPropertiesFile();

		// show confirm dialog
		List<Object> componentList = new ArrayList<Object>();

		FJLabel fjLabel1 = new FJLabel("Are you sure to upload files to Tercen?");
		Font boldFont = new Font("Verdana", Font.BOLD, 12);
		fjLabel1.setFont(boldFont);
		componentList.add(fjLabel1);

		// checkboxes
		FJCheckBox sampleFileCheckbox = createCheckbox(sampleLabel, sampleTooltip, fSample);
		FJCheckBox csvFileCheckbox = createCheckbox(csvLabel, csvTooltip, fCsv);
		FJCheckBox browserFileCheckbox = createCheckbox(browserLabel, browserTooltip, fBrowser);
		componentList.add(new HBox(new Component[] { sampleFileCheckbox }));
		componentList.add(new HBox(new Component[] { csvFileCheckbox }));
		componentList.add(new HBox(new Component[] { browserFileCheckbox }));

		componentList.add(new JSeparator());
		componentList.add(new FJLabel(channelsLabelLine1));
		componentList.add(new FJLabel(channelsLabelLine2));

		FJList paramList = createParameterList(arg1);
		componentList.add(new JScrollPane(paramList));
		componentList.add(new JSeparator());
		// Add fields to change tercen upload settings

		Component[] hostLabelField = createLabelTextFieldCombo("Host", hostName, hostName);
		Component[] teamLabelField = createLabelTextFieldCombo("Team", teamName, teamName);
		Component[] projectLabelField = createLabelTextFieldCombo("Project", projectName, projectName);
		Component[] domainLabelField = createLabelTextFieldCombo("Domain", domain, domain);
		Component[] userLabelField = createLabelTextFieldCombo("User", userName, userName);
		Component[] passwordLabelField = createLabelTextFieldCombo("Password", passWord, passWord);

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
				((FJTextField) passwordLabelField[1]).setEditable(!((FJTextField) passwordLabelField[1]).isEditable());
				if (button.getText() == ENABLE_UPLOAD_FIELDS_TEXT) {
					button.setText(SAVE_UPLOAD_FIELDS_TEXT);
				} else {
					button.setText(ENABLE_UPLOAD_FIELDS_TEXT);
				}
			}
		});
		componentList.add(button);

		int option = JOptionPane.showConfirmDialog((Component) null, componentList.toArray(),
				getName() + " " + getVersion(), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		if (option == JOptionPane.OK_OPTION) {
			upload = true;
			uploadFCS = sampleFileCheckbox.isSelected();
			uploadCSV = csvFileCheckbox.isSelected();
			openBrowser = browserFileCheckbox.isSelected();
			hostName = ((FJTextField) hostLabelField[1]).getText();
			teamName = ((FJTextField) teamLabelField[1]).getText();
			projectName = ((FJTextField) projectLabelField[1]).getText();
			domain = ((FJTextField) domainLabelField[1]).getText();
			userName = ((FJTextField) userLabelField[1]).getText();
			passWord = ((FJTextField) passwordLabelField[1]).getText();
			channels = new ArrayList<String>(paramList.getSelectedValuesList());
			return true;
		} else {
			upload = false;
			return false;
		}
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

	@Override
	public void setElement(SElement arg0) {
		upload = arg0.getBool("upload");
		uploadFCS = arg0.getBool("uploadFCS");
		uploadCSV = arg0.getBool("uploadCSV");
		openBrowser = arg0.getBool("openBrowser");
		hostName = arg0.getString("host");
		teamName = arg0.getString("team");
		projectName = arg0.getString("project");
		domain = arg0.getString("domain");
		userName = arg0.getString("user");
		passWord = arg0.getString("pwd");
		String channelString = arg0.getString("channels");
		if (channelString.equals("")) {
			channels = new ArrayList<String>();
		} else {
			channels = new ArrayList<String>(Arrays.asList(channelString.split(",")));
		}
	}

	@Override
	public ExportFileTypes useExportFileType() {
		return ExportFileTypes.CSV_CHANNEL;
	}
}
