package com.tercen;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Properties;

import javax.swing.Icon;
import javax.swing.JOptionPane;

import com.tercen.client.impl.TercenClient;
import com.tercen.model.impl.Project;
import com.tercen.model.impl.Schema;
import com.tercen.service.ServiceError;
import com.treestar.flowjo.core.Sample;
import com.treestar.flowjo.engine.utility.ParameterOptionHolder;
import com.treestar.lib.FJPluginHelper;
import com.treestar.lib.core.ExportFileTypes;
import com.treestar.lib.core.ExternalAlgorithmResults;
import com.treestar.lib.core.PopulationPluginInterface;
import com.treestar.lib.file.FJFileRef;
import com.treestar.lib.xml.SElement;

public class ImportPlugin extends ParameterOptionHolder implements PopulationPluginInterface {

	private static final String pluginName = "TercenImportPlugin";
	private static final String version = "1.0";

	protected enum ImportPluginStateEnum {
		empty, collectingSamples, uploading, uploaded, error;
	}

	// default settings
	protected static final String HOSTNAME_URL = "http://10.0.2.2:5402/";
	protected static final String TEAM_NAME = "test-team";
	protected static final String PROJECT_NAME = "myproject";
	protected static final String DOMAIN = "tercen";
	protected static final String USERNAME = "test";
	protected static final String PASSWORD = "test";

	private static final String Failed = "Failed";
	protected String hostName = HOSTNAME_URL;
	protected String teamName = TEAM_NAME;
	protected String projectName = PROJECT_NAME;
	protected String domain = DOMAIN;
	protected String userName = USERNAME;
	protected String passWord = PASSWORD;
	protected boolean upload;
	protected boolean openBrowser;
	protected ArrayList<String> channels = new ArrayList<String>();

	// properties to gather multiple samples
	protected ImportPluginStateEnum pluginState = ImportPluginStateEnum.empty;
	protected HashSet<String> samplePops = new HashSet<String>();
	private ImportPluginGUI gui = new ImportPluginGUI(this);

	public ImportPlugin() {
		super(pluginName);
		// read tercen upload properties
		readPropertiesFile();
	}

	@Override
	public String getName() {
		return pluginName;
	}

	@Override
	public String getVersion() {
		return version;
	}

	@Override
	public SElement getElement() {
		SElement result = super.getElement();
		result.setName(pluginName);
		result.setBool("upload", upload);
		result.setBool("openBrowser", openBrowser);
		result.setString("host", hostName);
		result.setString("team", teamName);
		result.setString("project", projectName);
		result.setString("domain", domain);
		result.setString("user", userName);
		result.setString("pwd", passWord);
		result.setString("channels", String.join(",", channels));
		result.setString("pluginState", pluginState.toString());
		if (!this.samplePops.isEmpty()) {
			SElement pops = new SElement("Populations");
			for (String pop : this.samplePops) {
				SElement popElem = new SElement("Population");
				popElem.setString("path", pop);
				pops.addContent(popElem);
			}
			result.addContent(pops);
		}
		return result;
	}

	@Override
	public void setElement(SElement element) {
		upload = element.getBool("upload");
		openBrowser = element.getBool("openBrowser");
		hostName = element.getString("host");
		teamName = element.getString("team");
		projectName = element.getString("project");
		domain = element.getString("domain");
		userName = element.getString("user");
		passWord = element.getString("pwd");
		String channelString = element.getString("channels");
		if (channelString.equals("")) {
			channels = new ArrayList<String>();
		} else {
			channels = new ArrayList<String>(Arrays.asList(channelString.split(",")));
		}
		this.pluginState = ImportPluginStateEnum.valueOf(element.getString("pluginState"));
		SElement pops = element.getChild("Populations");
		if (pops != null) {
			this.samplePops.clear();
			for (SElement popElem : pops.getChildren("Population")) {
				this.samplePops.add(popElem.getString("path"));
			}
		}
	}

	@Override
	public List<String> getParameters() {
		return new ArrayList<String>();
	}

	@Override
	public ExternalAlgorithmResults invokeAlgorithm(SElement fcmlQueryElement, File sampleFile, File outputFolder) {
		ExternalAlgorithmResults result = new ExternalAlgorithmResults();
		String workspaceText = "";

		try {
			if (pluginState == ImportPluginStateEnum.error) {
				result.setErrorMessage("Previous error prevents uploading");
				return result;
			}

			String fileName = sampleFile.getPath();
			if (pluginState == ImportPluginStateEnum.empty) {
				// add sampleFile
				samplePops.add(fileName);
				pluginState = ImportPluginStateEnum.collectingSamples;
			} else if (pluginState == ImportPluginStateEnum.collectingSamples && !upload) {
				// add sampleFile
				samplePops.add(fileName);
			} else if (pluginState == ImportPluginStateEnum.uploading) {
				if (!sampleFile.exists()) {
					JOptionPane.showMessageDialog(null, "Input file does not exist", "ImportPlugin error",
							JOptionPane.ERROR_MESSAGE);
					workspaceText = ImportPlugin.Failed;
				} else {
					Schema uploadResult = null;
					TercenClient client = null;
					Project project = null;

					// create client and get project (will be created if it doesn't exist)
					List<Object> clientProject = Utils.getClientAndProject(hostName, teamName, projectName, domain,
							userName, passWord);
					client = (TercenClient) clientProject.get(0);
					project = (Project) clientProject.get(1);

					// upload csv file
					uploadResult = Utils.uploadCsvFile(client, project, samplePops, channels);

					// Disable for now: upload fcs-zip file
					// uploadZipFile(fcmlQueryElement, client, project);

					// open browser
					if (openBrowser) {
						if (uploadResult != null) {
							String url = Utils.getTercenProjectURL(hostName, teamName, uploadResult);
							Desktop desktop = java.awt.Desktop.getDesktop();
							URI uri = new URI(String.valueOf(url));
							desktop.browse(uri);
							workspaceText = String.format("Sample file (s) has been uploaded to %s.", hostName);
						} else {
							JOptionPane.showMessageDialog(null,
									"No files have been uploaded, browser window will not open.",
									"ImportPlugin warning", JOptionPane.WARNING_MESSAGE);
						}
					}
				}
				pluginState = ImportPluginStateEnum.uploaded;
			}

			switch (pluginState) {
			case empty:
				result.setWorkspaceString("Empty");
				break;
			case collectingSamples:
				result.setWorkspaceString("" + samplePops.size() + " Classes");
				break;
			case uploading:
				result.setWorkspaceString("Uploading");
				break;
			case uploaded:
				result.setWorkspaceString(workspaceText);
				break;
			default:
				break;
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
		return gui.promptForOptions(arg0, arg1);
	}

	@Override
	public ExportFileTypes useExportFileType() {
		return ExportFileTypes.CSV_CHANNEL;
	}

	@Override
	public Icon getIcon() {
		return null;
	}

	private String uploadZipFile(SElement fcmlQueryElement, TercenClient client, Project project)
			throws ServiceError, IOException {
		Sample sample = FJPluginHelper.getSample(fcmlQueryElement);
		FJFileRef fileRef = sample.getFileRef();
		String fcsFileName = fileRef.getLocalFilepath();
		LinkedHashMap map = Utils.uploadZipFile(client, project, fcsFileName);
		return map.toString();
	}
}
